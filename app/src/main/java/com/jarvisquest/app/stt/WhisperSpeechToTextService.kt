package com.jarvisquest.app.stt

import android.util.Log
import com.jarvisquest.app.audio.AUDIO_SAMPLE_RATE_HZ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local whisper.cpp speech-to-text with a short rolling-window partial path. */
class WhisperSpeechToTextService(private val modelPath: String) : SpeechToTextService {
    companion object {
        private const val TAG = "JarvisWhisper"
        private const val MAX_UTTERANCE_SAMPLES = AUDIO_SAMPLE_RATE_HZ * 30
        private const val PARTIAL_WINDOW_SAMPLES = AUDIO_SAMPLE_RATE_HZ * 4
    }

    private val nativeLock = Any()
    private var handle: Long = 0

    override fun isAvailable(): Boolean = synchronized(nativeLock) { ensureHandleLocked() }

    private fun ensureHandleLocked(): Boolean {
        if (handle != 0L) return true
        handle = try { WhisperNative.nativeInit(modelPath) } catch (t: Throwable) {
            Log.e(TAG, "Whisper nativeInit failed", t); 0L
        }
        return handle != 0L
    }

    override suspend fun transcribe(pcm16Mono: ShortArray, sampleRateHz: Int): Result<String> =
        runTranscription(pcm16Mono, sampleRateHz, partial = false)

    override suspend fun transcribePartial(pcm16Mono: ShortArray, sampleRateHz: Int): Result<String> =
        runTranscription(pcm16Mono, sampleRateHz, partial = true)

    private suspend fun runTranscription(pcm16Mono: ShortArray, sampleRateHz: Int, partial: Boolean): Result<String> =
        withContext(Dispatchers.Default) {
            if (sampleRateHz != AUDIO_SAMPLE_RATE_HZ) return@withContext Result.failure(
                SttError.RecognitionFailed("Expected $AUDIO_SAMPLE_RATE_HZ Hz, got $sampleRateHz Hz")
            )
            if (pcm16Mono.isEmpty()) return@withContext Result.failure(SttError.NoSpeechDetected)
            if (!partial && pcm16Mono.size > MAX_UTTERANCE_SAMPLES) return@withContext Result.failure(
                SttError.RecognitionFailed("Speech segment was too long; please try again.")
            )

            val samples = if (partial && pcm16Mono.size > PARTIAL_WINDOW_SAMPLES) {
                pcm16Mono.copyOfRange(pcm16Mono.size - PARTIAL_WINDOW_SAMPLES, pcm16Mono.size)
            } else pcm16Mono

            synchronized(nativeLock) {
                if (!ensureHandleLocked()) return@synchronized Result.failure(SttError.EngineUnavailable)
                val floatPcm = FloatArray(samples.size) { i -> samples[i] / 32768.0f }
                val text = try {
                    if (partial) WhisperNative.nativeTranscribePartial(handle, floatPcm, sampleRateHz)
                    else WhisperNative.nativeTranscribe(handle, floatPcm, sampleRateHz)
                } catch (t: Throwable) {
                    Log.e(TAG, "Whisper transcription failed", t); null
                }
                when {
                    text == null -> Result.failure(SttError.RecognitionFailed("Whisper failed — please try again."))
                    text.isBlank() -> Result.failure(SttError.NoSpeechDetected)
                    else -> Result.success(text.trim())
                }
            }
        }

    override fun release() = synchronized(nativeLock) {
        if (handle != 0L) {
            try { WhisperNative.nativeRelease(handle) } catch (t: Throwable) { Log.e(TAG, "Whisper release failed", t) }
            handle = 0L
        }
    }
}
