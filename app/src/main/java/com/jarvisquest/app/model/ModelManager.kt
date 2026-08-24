package com.jarvisquest.app.model

import android.content.Context
import java.io.File

sealed class ModelStatus {
    data class Ready(val path: String, val sizeBytes: Long) : ModelStatus()
    data class Missing(val expectedPath: String) : ModelStatus()
}

/**
 * Milestone 2 scope: detection only, no downloading. The project brief for
 * this milestone asks for detect-and-explain ("Whisper model ontbreekt" +
 * where to put it), not a download pipeline — adding one would mean adding
 * INTERNET permission and a progress UI that weren't asked for here, so
 * that's deliberately left for a later pass rather than implemented
 * speculatively.
 *
 * Models go in the app's external-files directory
 * (`/Android/data/com.jarvisquest.app/files/models/` on the device), which
 * needs no storage permission on modern Android and is reachable with
 * `adb push` or any on-device file manager — the same way the APK itself
 * gets sideloaded.
 */
class ModelManager(context: Context) {

    private val modelsDir: File = File(context.getExternalFilesDir(null), "models")
    private val whisperModelFile: File = File(modelsDir, WHISPER_MODEL_FILENAME)

    /** Sanity floor so a truncated/partial copy isn't mistaken for a ready model. */
    private val minPlausibleWhisperBytes = 20_000_000L // ggml-base.bin is ~142 MB; even ggml-tiny is ~75 MB.

    fun checkWhisperModel(): ModelStatus {
        return if (whisperModelFile.exists() && whisperModelFile.length() >= minPlausibleWhisperBytes) {
            ModelStatus.Ready(whisperModelFile.absolutePath, whisperModelFile.length())
        } else {
            ModelStatus.Missing(whisperModelFile.absolutePath)
        }
    }

    companion object {
        // ggml-base multilingual: ~142 MB, CPU-friendly, meaningfully better
        // non-English accuracy than ggml-tiny. See README "Model selected"
        // for the reasoning and the ggml-small upgrade path.
        const val WHISPER_MODEL_FILENAME = "ggml-base.bin"
    }
}
