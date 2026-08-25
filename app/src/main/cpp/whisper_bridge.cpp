// JNI bridge between WhisperNative.kt and whisper.cpp's public C API
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

JNIEXPORT jlong JNICALL
Java_com_jarvisquest_app_stt_WhisperNative_nativeInit(JNIEnv *env, jobject, jstring modelPath) {
    if (modelPath == nullptr) {
        LOGE("nativeInit: modelPath is null");
        return 0;
    }

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) {
        LOGE("nativeInit: GetStringUTFChars failed");
        return 0;
    }

    LOGI("nativeInit: loading model: %s", path);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;

    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("nativeInit: whisper model initialization failed");
        return 0;
    }

    LOGI("nativeInit: whisper model initialized");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_jarvisquest_app_stt_WhisperNative_nativeTranscribe(
        JNIEnv *env, jobject, jlong handle, jfloatArray pcmFloat, jint sampleRate) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(handle);
    if (ctx == nullptr || pcmFloat == nullptr) {
        LOGE("nativeTranscribe: null context or PCM buffer");
        return nullptr;
    }
    if (sampleRate != WHISPER_SAMPLE_RATE) {
        LOGE("Unexpected sample rate %d (expected %d)", sampleRate, WHISPER_SAMPLE_RATE);
        return nullptr;
    }

    jsize numSamples = env->GetArrayLength(pcmFloat);
    if (numSamples <= 0) {
        LOGE("nativeTranscribe: empty PCM buffer");
        return nullptr;
    }

    jfloat *samples = env->GetFloatArrayElements(pcmFloat, nullptr);
    if (samples == nullptr) {
        LOGE("nativeTranscribe: GetFloatArrayElements failed");
        return nullptr;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "auto";
    params.translate = false;
    params.single_segment = true;
    params.no_context = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    unsigned int hw = std::thread::hardware_concurrency();
    params.n_threads = std::max(1, std::min(4, static_cast<int>(hw == 0 ? 2 : hw)));

    LOGI("nativeTranscribe: running whisper_full on %d samples", numSamples);
    int rc = whisper_full(ctx, params, samples, numSamples);
    env->ReleaseFloatArrayElements(pcmFloat, samples, JNI_ABORT);

    if (rc != 0) {
        LOGE("whisper_full returned %d", rc);
        return nullptr;
    }

    std::string text;
    const int nSegments = whisper_full_n_segments(ctx);
    for (int i = 0; i < nSegments; ++i) {
        text += whisper_full_get_segment_text(ctx, i);
    }
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_jarvisquest_app_stt_WhisperNative_nativeRelease(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(handle);
    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}

}
