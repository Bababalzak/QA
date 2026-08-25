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

/** Local speech-to-text abstraction. */
interface SpeechToTextService {
    fun isAvailable(): Boolean
    suspend fun transcribe(pcm16Mono: ShortArray, sampleRateHz: Int): Result<String>

    /** Optional low-latency partial transcription. Implementations may override this. */
    suspend fun transcribePartial(pcm16Mono: ShortArray, sampleRateHz: Int): Result<String> =
        transcribe(pcm16Mono, sampleRateHz)

    fun release()
}
