package com.jarvisquest.app.model

import android.content.Context
import java.io.File

sealed class ModelStatus {
    data class Ready(val path: String, val sizeBytes: Long) : ModelStatus()
    data class Missing(val expectedPath: String) : ModelStatus()
}

class ModelManager(context: Context) {
    private val modelsDir: File = File(context.getExternalFilesDir(null), "models")
    private val whisperModelFile: File = File(modelsDir, WHISPER_MODEL_FILENAME)
    private val qwenModelFile: File = File(modelsDir, QWEN_MODEL_FILENAME)

    private val minPlausibleWhisperBytes = 10_000_000L
    private val minPlausibleQwenBytes = 100_000_000L

    fun checkWhisperModel(): ModelStatus =
        if (whisperModelFile.exists() && whisperModelFile.length() >= minPlausibleWhisperBytes)
            ModelStatus.Ready(whisperModelFile.absolutePath, whisperModelFile.length())
        else ModelStatus.Missing(whisperModelFile.absolutePath)

    fun checkQwenModel(): ModelStatus =
        if (qwenModelFile.exists() && qwenModelFile.length() >= minPlausibleQwenBytes)
            ModelStatus.Ready(qwenModelFile.absolutePath, qwenModelFile.length())
        else ModelStatus.Missing(qwenModelFile.absolutePath)

    companion object {
        // Tiny Whisper is much faster on Quest 3 and is sufficient for the prototype.
        const val WHISPER_MODEL_FILENAME = "ggml-tiny.bin"
        // Qwen3 1.7B Instruct, GGUF Q4_K_M. Keep this outside the APK.
        const val QWEN_MODEL_FILENAME = "Qwen3-1.7B-Q4_K_M.gguf"
    }
}
