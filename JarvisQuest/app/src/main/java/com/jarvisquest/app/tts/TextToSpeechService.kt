package com.jarvisquest.app.tts

sealed class TtsError : Exception() {
    object EngineUnavailable : TtsError() {
        override val message = "No text-to-speech engine is available on this device."
        private fun readResolve(): Any = EngineUnavailable
    }
    data class SynthesisFailed(override val message: String) : TtsError()
}

/**
 * Local text-to-speech abstraction. The Milestone 1 implementation
 * ([com.jarvisquest.app.tts.AndroidTextToSpeechService]) wraps the
 * platform [android.speech.tts.TextToSpeech] engine — unlike STT, this API
 * shape (text in, spoken audio out, no pre-owned mic session) has no
 * architectural conflict with this app's pipeline, so it's a genuine,
 * working implementation, not a stub. Its main open question is whether
 * Quest 3 ships ANY TTS engine at all, since it has no Google Mobile
 * Services — [isAvailable] reports the real, measured answer rather than
 * assuming either way (see README "Known limitations").
 */
interface TextToSpeechService {
    /** True once an engine has initialized successfully. */
    fun isAvailable(): Boolean

    /** Speaks [text] and suspends until playback finishes (or fails). */
    suspend fun speak(text: String): Result<Unit>

    /** Stops any speech currently in progress. */
    fun stop()

    /** Releases the underlying engine. Call from onDestroy. */
    fun release()
}
