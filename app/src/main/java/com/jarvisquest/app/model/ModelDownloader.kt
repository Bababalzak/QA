package com.jarvisquest.app.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader(private val context: Context) {
    companion object {
        private const val QWEN_URL = "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true"
        private const val MIN_QWEN_BYTES = 1_000_000_000L
    }

    suspend fun ensureQwenModel(): Result<String> = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
        val target = File(dir, ModelManager.QWEN_MODEL_FILENAME)
        if (target.exists() && target.length() >= MIN_QWEN_BYTES) return@withContext Result.success(target.absolutePath)

        val part = File(dir, "${target.name}.part")
        runCatching {
            val connection = (URL(QWEN_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 120_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            try {
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    error("Model download HTTP ${connection.responseCode}")
                }
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (part.length() < MIN_QWEN_BYTES) error("Downloaded Qwen model is incomplete")
            if (target.exists()) target.delete()
            check(part.renameTo(target)) { "Could not finalize Qwen model" }
            target.absolutePath
        }.onFailure { part.delete() }
    }
}
