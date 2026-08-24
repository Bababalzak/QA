package com.jarvisquest.app.ai

sealed class AIServiceError : Exception() {
    object ModelNotLoaded : AIServiceError() {
        override val message = "No local model is loaded."
        private fun readResolve(): Any = ModelNotLoaded
    }
    data class ModelFileMissing(val expectedPath: String) : AIServiceError() {
        override val message = "Model file not found at $expectedPath."
    }
    data class LoadFailed(override val message: String) : AIServiceError()
    data class InferenceFailed(override val message: String) : AIServiceError()
}

/**
 * Abstraction over local LLM inference. Nothing outside the `ai` package
 * should import a specific runtime (llama.cpp, ExecuTorch, MLC, ...) —
 * everything talks to this interface, per the project brief's explicit
 * "AIService" requirement. The Milestone 1 implementation
 * ([NotReadyAIService]) never fabricates a response; the Milestone 3
 * implementation is expected to wrap llama.cpp's Android JNI bindings
 * (an official example ships in `examples/llama.android` upstream) loading
 * a quantized Qwen3-1.7B GGUF file (~1.1–1.3 GB at Q4_K_M) from local
 * storage — too large to bundle in the APK, hence [ModelManager] in a
 * later milestone downloading/verifying it separately (see project brief
 * Section 13 and README "Known limitations").
 */
interface AIService {
    /** True once a model is loaded and ready to answer without a reload. */
    fun isReady(): Boolean

    /**
     * Loads the model once. Safe to call multiple times — implementations
     * must no-op if already loaded, since the brief explicitly forbids
     * reloading per-request.
     */
    suspend fun loadModel(): Result<Unit>

    /**
     * Generates a reply to [prompt]. [onToken] is invoked as tokens stream
     * in (empty/no-op for implementations that don't support streaming
     * yet), so the UI and TTS can start reacting before generation
     * finishes, per the low-latency requirement.
     */
    suspend fun generate(prompt: String, onToken: (String) -> Unit = {}): Result<String>

    /** Releases model resources (native memory, context, ...). */
    fun release()
}
