// JNI bridge between WhisperNative.kt and whisper.cpp's public C API
// (third_party/whisper.cpp/include/whisper.h).
//
// Scope is deliberately narrow: load a GGML model from a local file path,
// run one full transcription pass over a caller-supplied buffer of
// already-VAD-segmented float samples, return the text. No streaming,
// no timestamps, no multi-segment handling beyond concatenation — that's
// enough for AudioService -> EnergyBasedVad -> WhisperSpeechToTextService
// as wired up today, and more can be added later without touching this
// file's JNI surface.
//
// NOTE: this is the least-verified file in the project. It was written
// against the core whisper.h API as of this project's knowledge, not
// compiled here (no NDK in this sandbox) or diffed against the exact
// pinned submodule commit. If the CI build in android-build.yml fails
// here, the error will name the missing/renamed symbol directly —
// fix that one symbol against third_party/whisper.cpp/include/whisper.h
// rather than rewriting this file.

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <thread>
#include <algorithm>

#include "whisper.h"

#define LOG_TAG "JarvisWhisperNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// com.jarvisquest.app.stt.WhisperNative.nativeInit(modelPath: String): Long
// Returns an opaque handle (the whisper_context pointer) or 0 on failure.
JNIEXPORT jlong JNICALL
Java_com_jarvisquest_app_stt_WhisperNative_nativeInit(JNIEnv *env, jobject /* this */, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // Milestone 2 targets correctness on CPU first.

    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("whisper_init_from_file_with_params failed");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

// com.jarvisquest.app.stt.WhisperNative.nativeTranscribe(
//     handle: Long, pcmFloat: FloatArray, sampleRate: Int
// ): String?
// pcmFloat must already be mono, 16 kHz, normalized to [-1.0, 1.0] — see
// WhisperSpeechToTextService.kt for the ShortArray -> FloatArray conversion.
// Returns null on failure (caller maps that to Result.failure, never to
// fabricated text).
JNIEXPORT jstring JNICALL
Java_com_jarvisquest_app_stt_WhisperNative_nativeTranscribe(
        JNIEnv *env, jobject /* this */, jlong handle, jfloatArray pcmFloat, jint sampleRate) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(handle);
    if (ctx == nullptr) {
        LOGE("nativeTranscribe called with null context");
        return nullptr;
    }
    if (sampleRate != WHISPER_SAMPLE_RATE) {
        // whisper.cpp is built around a fixed 16 kHz input rate; our own
        // pipeline already captures at 16 kHz (see AudioService.kt), so
        // this should never fire — treated as a hard error, not silently
        // resampled, so a future pipeline change can't silently degrade.
        LOGE("Unexpected sample rate %d (whisper.cpp expects %d)", sampleRate, WHISPER_SAMPLE_RATE);
        return nullptr;
    }

    jsize numSamples = env->GetArrayLength(pcmFloat);
    jfloat *samples = env->GetFloatArrayElements(pcmFloat, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "auto";       // brief requires Dutch + implicitly English; auto-detect both.
    params.translate = false;       // transcribe, never translate to English.
    params.single_segment = true;   // input is already one VAD-segmented utterance.
    params.no_context = true;       // no cross-utterance state to carry.
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    unsigned int hw = std::thread::hardware_concurrency();
    params.n_threads = std::max(2, std::min(4, static_cast<int>(hw == 0 ? 4 : hw)));

    int rc = whisper_full(ctx, params, samples, numSamples);
    env->ReleaseFloatArrayElements(pcmFloat, samples, JNI_ABORT);

    if (rc != 0) {
        LOGE("whisper_full returned %d", rc);
        return nullptr;
    }

    std::string text;
    const int nSegments = whisper_full_n_segments(ctx);
    for (int i = 0; i < nSegments; i++) {
        text += whisper_full_get_segment_text(ctx, i);
    }
    return env->NewStringUTF(text.c_str());
}

// com.jarvisquest.app.stt.WhisperNative.nativeRelease(handle: Long)
JNIEXPORT void JNICALL
Java_com_jarvisquest_app_stt_WhisperNative_nativeRelease(JNIEnv * /* env */, jobject /* this */, jlong handle) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(handle);
    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}

} // extern "C"
