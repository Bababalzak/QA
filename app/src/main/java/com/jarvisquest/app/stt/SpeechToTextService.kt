package com.jarvisquest.app.stt

/** Why a transcription attempt failed — lets the UI show a useful message instead of crashing. */
sealed class SttError : Exception() {
    object EngineUnavailable : SttError() {
        override val message = "No speech recognition engine is available on this device."
        private fun readResolve(): Any = EngineUnavailable
    }
    data class RecognitionFailed(override val message: String) : SttError()
    object NoSpeechDetected : SttError()
}

/**
 * Local speech-to-text abstraction. [com.jarvisquest.app.controller.AssistantController]
 * only depends on this interface, never on a concrete engine, so the
 * implementation can move from the Android system recognizer (Milestone 1,
 * used mainly for development on a phone that has Google's on-device
 * speech services) to an embedded whisper.cpp model (Milestone 2, the
 * implementation that is actually expected to work on Quest 3, which ships
 * without Google Mobile Services) without touching any caller.
 */
interface SpeechToTextService {
    /** True if this engine can plausibly transcribe right now (installed, licensed, language available). */
    fun isAvailable(): Boolean

    /**
     * Transcribes [pcm16Mono] (raw PCM16 mono samples at [sampleRateHz]).
     * Returns the recognized text, or a [SttError] — never fabricated text.
     */
    suspend fun transcribe(pcm16Mono: ShortArray, sampleRateHz: Int): Result<String>

    /** Releases any engine resources (recognizer instances, native handles, ...). */
    fun release()
}
