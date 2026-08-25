#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include "llama.h"

#define TAG "JarvisLlamaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaHandle { llama_model *model = nullptr; llama_context *ctx = nullptr; const llama_vocab *vocab = nullptr; std::mutex mutex; };

static std::string jstr(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *p = env->GetStringUTFChars(s, nullptr); if (!p) return {};
    std::string out(p); env->ReleaseStringUTFChars(s, p); return out;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_jarvisquest_app_ai_LlamaNative_nativeInit(JNIEnv *env, jobject, jstring modelPath) {
    const std::string path = jstr(env, modelPath); if (path.empty()) return 0;
    llama_backend_init();
    llama_model_params mp = llama_model_default_params(); mp.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(path.c_str(), mp);
    if (!model) { LOGE("Failed to load model: %s", path.c_str()); llama_backend_free(); return 0; }
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 1024; cp.n_batch = 256;
    const unsigned hw = std::thread::hardware_concurrency();
    cp.n_threads = std::max(1, std::min(8, (int)(hw == 0 ? 4 : hw))); cp.n_threads_batch = cp.n_threads;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) { llama_model_free(model); llama_backend_free(); return 0; }
    auto *h = new LlamaHandle{model, ctx, llama_model_get_vocab(model)};
    LOGI("Qwen model loaded; threads=%d", cp.n_threads); return reinterpret_cast<jlong>(h);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jarvisquest_app_ai_LlamaNative_nativeGenerate(JNIEnv *env, jobject, jlong handle, jstring prompt, jint maxTokens) {
    auto *h = reinterpret_cast<LlamaHandle *>(handle); if (!h) return nullptr;
    std::lock_guard<std::mutex> guard(h->mutex);
    if (!h->ctx || !h->vocab) return nullptr;
    const std::string text = jstr(env, prompt); if (text.empty()) return env->NewStringUTF("");
    std::vector<llama_token> tokens(text.size() + 256);
    const int n = llama_tokenize(h->vocab, text.c_str(), (int)text.size(), tokens.data(), (int)tokens.size(), true, true);
    if (n < 0) return nullptr; tokens.resize(n);
    llama_memory_clear(llama_get_memory(h->ctx), true);
    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    if (llama_decode(h->ctx, batch) != 0) return nullptr;
    const int limit = std::max(1, std::min((int)maxTokens, 64));
    std::string result; result.reserve(limit * 4);
    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sp); llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    for (int i = 0; i < limit; ++i) {
        llama_token tok = llama_sampler_sample(sampler, h->ctx, -1); if (llama_vocab_is_eog(h->vocab, tok)) break;
        char buf[256]; const int len = llama_token_to_piece(h->vocab, tok, buf, sizeof(buf), 0, true); if (len > 0) result.append(buf, len);
        batch = llama_batch_get_one(&tok, 1); if (llama_decode(h->ctx, batch) != 0) break;
    }
    llama_sampler_free(sampler); return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvisquest_app_ai_LlamaNative_nativeRelease(JNIEnv *, jobject, jlong handle) {
    auto *h = reinterpret_cast<LlamaHandle *>(handle); if (!h) return;
    {
        std::lock_guard<std::mutex> guard(h->mutex);
        if (h->ctx) llama_free(h->ctx); if (h->model) llama_model_free(h->model);
        h->ctx = nullptr; h->model = nullptr; h->vocab = nullptr;
    }
    delete h; llama_backend_free();
}
