package com.jarvisquest.app.stt

import com.jarvisquest.app.audio.AUDIO_SAMPLE_RATE_HZ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Milestone 2 implementation: local speech-to-text via an embedded
 * whisper.cpp model, fed directly with the PCM16 buffer
 * [com.jarvisquest.app.audio.EnergyBasedVad] hands to
 * [com.jarvisquest.app.controller.AssistantController] — this is exactly
 * the pipeline shape [NotYetImplementedSpeechToTextService]'s doc comment
 * said Android's built-in SpeechRecognizer couldn't fit.
 *
 * [modelPath] must point at an existing GGML whisper model file (e.g.
 * `ggml-base.bin`); [com.jarvisquest.app.model.ModelManager] is
 * responsible for having downloaded and verified it before this class is
 * ever constructed — see [com.jarvisquest.app.ui.AssistantViewModel].
 */
class WhisperSpeechToTextService(private val modelPath: String) : SpeechToTextService {

    private var handle: Long = 0

    override fun isAvailable(): Boolean {
        if (handle != 0L) return true
        handle = WhisperNative.nativeInit(modelPath)
        return handle != 0L
    }

    override suspend fun transcribe(pcm16Mono: ShortArray, sampleRateHz: Int): Result<String> =
        withContext(Dispatchers.Default) {
            if (!isAvailable()) {
                return@withContext Result.failure(SttError.EngineUnavailable)
            }
            if (sampleRateHz != AUDIO_SAMPLE_RATE_HZ) {
                return@withContext Result.failure(
                    SttError.RecognitionFailed("Expected $AUDIO_SAMPLE_RATE_HZ Hz, got $sampleRateHz Hz")
                )
            }

            val floatPcm = FloatArray(pcm16Mono.size) { i -> pcm16Mono[i] / 32768.0f }
            val text = WhisperNative.nativeTranscribe(handle, floatPcm, sampleRateHz)

            if (text == null) {
                Result.failure(SttError.RecognitionFailed("whisper_full failed — see Logcat tag JarvisWhisperNative"))
            } else if (text.isBlank()) {
                Result.failure(SttError.NoSpeechDetected)
            } else {
                Result.success(text.trim())
            }
        }

    override fun release() {
        if (handle != 0L) {
            WhisperNative.nativeRelease(handle)
            handle = 0
        }
    }
}
