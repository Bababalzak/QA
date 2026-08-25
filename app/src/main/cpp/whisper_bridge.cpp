// JNI bridge between WhisperNative.kt and whisper.cpp
#include <jni.h>
#include <android/log.h>
#include <string>
#include <thread>
#include <algorithm>
#include "whisper.h"

#define LOG_TAG "JarvisWhisperNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr int MAX_UTTERANCE_SAMPLES = WHISPER_SAMPLE_RATE * 30;

static int thread_count() {
    const unsigned hw = std::thread::hardware_concurrency();
    return std::max(1, std::min(8, static_cast<int>(hw == 0 ? 4 : hw)));
}

static jstring run_whisper(JNIEnv *env, whisper_context *ctx, jfloat *samples, int n, bool partial) {
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "nl";
    params.translate = false;
    params.single_segment = partial;
    params.no_context = true;
    params.no_timestamps = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_blank = true;
    params.temperature = 0.0f;
    params.temperature_inc = 0.0f;
    params.n_threads = thread_count();

    // whisper.cpp's streaming example uses a partial encoder context for faster
    // rolling-window inference. This is only used for partial updates.
    if (partial) {
        params.audio_ctx = 768;
        params.max_tokens = 32;
    }

    const int rc = whisper_full(ctx, params, samples, n);
    if (rc != 0) {
        LOGE("whisper_full returned %d", rc);
        return nullptr;
    }

    std::string text;
    const int nSegments = whisper_full_n_segments(ctx);
    for (int i = 0; i < nSegments; ++i) {
        const char *segment = whisper_full_get_segment_text(ctx, i);
        if (segment != nullptr) text += segment;
    }

    while (!text.empty() && (text.back() == ' ' || text.back() == '\n' || text.back() == '\r' || text.back() == '\t')) text.pop_back();
    size_t first = 0;
    while (first < text.size() && (text[first] == ' ' || text[first] == '\n' || text[first] == '\r' || text[first] == '\t')) ++first;
    if (first > 0) text.erase(0, first);
    return env->NewStringUTF(text.c_str());
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_jarvisquest_app_stt_WhisperNative_nativeInit(JNIEnv *env, jobject, jstring modelPath) {
    if (modelPath == nullptr) return 0;
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) return 0;
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) {
        LOGE("Whisper model initialization failed");
        return 0;
    }
    LOGI("Whisper model initialized; threads=%d", thread_count());
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_jarvisquest_app_stt_WhisperNative_nativeTranscribe(JNIEnv *env, jobject, jlong handle, jfloatArray pcmFloat, jint sampleRate) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (!ctx || !pcmFloat || sampleRate != WHISPER_SAMPLE_RATE) return nullptr;
    const jsize n = env->GetArrayLength(pcmFloat);
    if (n <= 0 || n > MAX_UTTERANCE_SAMPLES) return nullptr;
    jfloat *samples = env->GetFloatArrayElements(pcmFloat, nullptr);
    if (!samples) return nullptr;
    jstring result = run_whisper(env, ctx, samples, n, false);
    env->ReleaseFloatArrayElements(pcmFloat, samples, JNI_ABORT);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_jarvisquest_app_stt_WhisperNative_nativeTranscribePartial(JNIEnv *env, jobject, jlong handle, jfloatArray pcmFloat, jint sampleRate) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (!ctx || !pcmFloat || sampleRate != WHISPER_SAMPLE_RATE) return nullptr;
    const jsize n = env->GetArrayLength(pcmFloat);
    if (n <= 0 || n > WHISPER_SAMPLE_RATE * 4) return nullptr;
    jfloat *samples = env->GetFloatArrayElements(pcmFloat, nullptr);
    if (!samples) return nullptr;
    jstring result = run_whisper(env, ctx, samples, n, true);
    env->ReleaseFloatArrayElements(pcmFloat, samples, JNI_ABORT);
    return result;
}

JNIEXPORT void JNICALL
Java_com_jarvisquest_app_stt_WhisperNative_nativeRelease(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx) whisper_free(ctx);
}

}
