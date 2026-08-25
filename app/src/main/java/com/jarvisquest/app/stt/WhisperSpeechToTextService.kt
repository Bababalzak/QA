package com.jarvisquest.app.stt

import android.util.Log
import com.jarvisquest.app.audio.AUDIO_SAMPLE_RATE_HZ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local whisper.cpp speech-to-text implementation. */
class WhisperSpeechToTextService(private val modelPath: String) : SpeechToTextService {

    companion object {
        private const val TAG = "JarvisWhisper"
        // Prevent accidentally feeding an unbounded VAD utterance into native Whisper.
        private const val MAX_UTTERANCE_SAMPLES = AUDIO_SAMPLE_RATE_HZ * 30
    }

    private val nativeLock = Any()
    private var handle: Long = 0

    override fun isAvailable(): Boolean = synchronized(nativeLock) {
        if (handle != 0L) return@synchronized true
        handle = try {
            WhisperNative.nativeInit(modelPath)
        } catch (t: Throwable) {
            Log.e(TAG, "Whisper nativeInit failed", t)
            0L
        }
        handle != 0L
    }

    override suspend fun transcribe(pcm16Mono: ShortArray, sampleRateHz: Int): Result<String> =
        withContext(Dispatchers.Default) {
            if (sampleRateHz != AUDIO_SAMPLE_RATE_HZ) {
                return@withContext Result.failure(
                    SttError.RecognitionFailed("Expected $AUDIO_SAMPLE_RATE_HZ Hz, got $sampleRateHz Hz")
                )
            }
            if (pcm16Mono.isEmpty()) {
                return@withContext Result.failure(SttError.NoSpeechDetected)
            }
            if (pcm16Mono.size > MAX_UTTERANCE_SAMPLES) {
                Log.w(TAG, "Rejecting oversized utterance: ${pcm16Mono.size} samples")
                return@withContext Result.failure(
                    SttError.RecognitionFailed("Speech segment was too long; please try again.")
                )
            }

            synchronized(nativeLock) {
                if (handle == 0L) {
                    handle = try {
                        WhisperNative.nativeInit(modelPath)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Whisper nativeInit failed", t)
                        0L
                    }
                }
                if (handle == 0L) {
                    return@synchronized Result.failure(SttError.EngineUnavailable)
                }

                val floatPcm = FloatArray(pcm16Mono.size) { i -> pcm16Mono[i] / 32768.0f }
                val text = try {
                    WhisperNative.nativeTranscribe(handle, floatPcm, sampleRateHz)
                } catch (t: Throwable) {
                    Log.e(TAG, "Whisper nativeTranscribe failed", t)
                    null
                }

                when {
                    text == null -> Result.failure(
                        SttError.RecognitionFailed("Whisper failed — please try again.")
                    )
                    text.isBlank() -> Result.failure(SttError.NoSpeechDetected)
                    else -> Result.success(text.trim())
                }
            }
        }

    override fun release() = synchronized(nativeLock) {
        if (handle != 0L) {
            try {
                WhisperNative.nativeRelease(handle)
            } catch (t: Throwable) {
                Log.e(TAG, "Whisper nativeRelease failed", t)
            }
            handle = 0L
        }
    }
}
