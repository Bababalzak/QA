package com.jarvisquest.app.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ModelDownloader(private val context: Context) {
    companion object {
        private const val QWEN_URL = "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true"
        private const val WHISPER_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin?download=true"
        private const val MIN_QWEN_BYTES = 1_000_000_000L
        private const val MIN_WHISPER_BYTES = 20_000_000L
    }

    private suspend fun download(url: String, target: File, minimumBytes: Long): Result<String> = withContext(Dispatchers.IO) {
        if (target.exists() && target.length() >= minimumBytes) return@withContext Result.success(target.absolutePath)
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, "${target.name}.part")
        runCatching {
            if (part.exists()) part.delete()
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 120_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            try {
                connection.connect()
                if (connection.responseCode !in 200..299) error("Download HTTP ${connection.responseCode}")
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (part.length() < minimumBytes) error("Downloaded file is incomplete (${part.length()} bytes)")
            if (target.exists()) target.delete()

            // File.renameTo() can fail on Android storage even when the download succeeded.
            // Use the NIO move API and verify the final file before reporting success.
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            check(target.exists() && target.length() >= minimumBytes) {
                "Could not finalize download"
            }
            target.absolutePath
        }.onFailure {
            part.delete()
        }
    }

    suspend fun ensureQwenModel(): Result<String> = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
        download(QWEN_URL, File(dir, ModelManager.QWEN_MODEL_FILENAME), MIN_QWEN_BYTES)
    }

    suspend fun ensureWhisperModel(): Result<String> = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
        download(WHISPER_URL, File(dir, ModelManager.WHISPER_MODEL_FILENAME), MIN_WHISPER_BYTES)
    }

    suspend fun ensureAllModels(): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val whisper = ensureWhisperModel().getOrElse { return@withContext Result.failure(it) }
        val qwen = ensureQwenModel().getOrElse { return@withContext Result.failure(it) }
        Result.success(Pair(whisper, qwen))
    }
}
