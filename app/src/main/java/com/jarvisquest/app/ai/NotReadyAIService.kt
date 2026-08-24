package com.jarvisquest.app.ai

/**
 * Milestone 1/2 placeholder. This class exists so
 * [com.jarvisquest.app.controller.AssistantController] and the UI have a
 * real [AIService] to hold onto and can display an honest "not ready yet"
 * state — it must never be mistaken for a working model, and it never
 * returns invented text. Local Qwen3 inference (llama.cpp + GGUF) replaces
 * this in Milestone 3.
 */
class NotReadyAIService : AIService {

    override fun isReady(): Boolean = false

    override suspend fun loadModel(): Result<Unit> =
        Result.failure(
            AIServiceError.ModelFileMissing(
                expectedPath = "(not yet defined — Milestone 3 introduces the model file path and ModelManager)"
            )
        )

    override suspend fun generate(prompt: String, onToken: (String) -> Unit): Result<String> =
        Result.failure(AIServiceError.ModelNotLoaded)

    override fun release() = Unit
}
