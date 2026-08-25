package com.jarvisquest.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Local Qwen3 GGUF inference through llama.cpp JNI. */
class QwenAIService(private val modelPath: String) : AIService {
    @Volatile private var handle: Long = 0L

    override fun isReady(): Boolean = handle != 0L

    override suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.Default) {
        if (isReady()) return@withContext Result.success(Unit)
        val file = File(modelPath)
        if (!file.exists() || file.length() < 100_000_000L) {
            return@withContext Result.failure(AIServiceError.ModelFileMissing(modelPath))
        }
        runCatching {
            val h = LlamaNative.nativeInit(modelPath)
            check(h != 0L) { "llama.cpp could not load the Qwen GGUF model" }
            handle = h
        }.fold({ Result.success(Unit) }, { Result.failure(AIServiceError.LoadFailed(it.message ?: "Model load failed")) })
    }

    override suspend fun generate(prompt: String, onToken: (String) -> Unit): Result<String> = withContext(Dispatchers.Default) {
        if (!isReady()) {
            val loaded = loadModel()
            if (loaded.isFailure) return@withContext Result.failure(loaded.exceptionOrNull()!!)
        }
        val currentHandle = handle
        if (currentHandle == 0L) return@withContext Result.failure(AIServiceError.InferenceFailed("AI model is not ready"))

        runCatching {
            val formatted = "<|im_start|>system\nYou are Quest Assistant, a helpful AI assistant running locally on a Meta Quest 3. Answer naturally and directly. Do not explain your reasoning.\n<|im_end|>\n<|im_start|>user\n/no_think\n$prompt\n<|im_end|>\n<|im_start|>assistant\n"
            val streamed = LlamaNative.nativeGenerateStreaming(
                currentHandle,
                formatted,
                0,
                LlamaNative.TokenCallback { piece -> onToken(piece) }
            ) ?: error("llama.cpp returned no response")
            val cleaned = streamed.trim()
            if (cleaned.isEmpty()) error("Qwen returned an empty response")
            cleaned
        }.fold({ Result.success(it) }, { Result.failure(AIServiceError.InferenceFailed(it.message ?: "Inference failed")) })
    }

    override fun release() {
        val currentHandle = handle
        if (currentHandle != 0L) {
            handle = 0L
            LlamaNative.nativeRelease(currentHandle)
        }
    }
}
