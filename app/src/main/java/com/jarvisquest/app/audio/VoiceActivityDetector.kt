package com.jarvisquest.app.audio

/** Per-frame verdict from a [VoiceActivityDetector]. */
sealed class VadEvent {
    /** Still silence; nothing to report. */
    object Silence : VadEvent()

    /** This frame is the first frame of a new speech segment. */
    object SpeechStarted : VadEvent()

    /** Speech is ongoing (fired for every voiced frame after SpeechStarted). */
    object SpeechContinuing : VadEvent()

    /**
     * Speech just ended. [utterance] contains every frame captured since
     * SpeechStarted (including the trailing silence hangover), concatenated
     * and ready to hand to [com.jarvisquest.app.stt.SpeechToTextService].
     */
    data class SpeechEnded(val utterance: ShortArray) : VadEvent()
}

/**
 * Abstraction over "is the current frame speech". Kept deliberately narrow
 * (one method) so a smarter implementation — e.g. a Silero VAD ONNX model —
 * can replace [EnergyBasedVad] later without any change to
 * [com.jarvisquest.app.controller.AssistantController], which only ever
 * talks to this interface.
 */
interface VoiceActivityDetector {
    /** Feed one 20 ms PCM16 frame (see [AUDIO_FRAME_SAMPLES]); get back what changed. */
    fun process(frame: ShortArray): VadEvent

    /** Clears any internal state (noise floor, in-progress utterance, ...). */
    fun reset()
}
