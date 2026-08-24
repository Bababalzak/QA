package com.jarvisquest.app.stt

/**
 * Raw JNI surface over whisper.cpp. Nothing outside the `stt` package
 * should call this directly — [WhisperSpeechToTextService] is the
 * [SpeechToTextService] implementation that wraps it and is what
 * [com.jarvisquest.app.controller.AssistantController] actually depends on.
 */
internal object WhisperNative {
    init {
        System.loadLibrary("jarvis_whisper")
    }

    /** Returns an opaque native handle, or 0 if the model failed to load. */
    external fun nativeInit(modelPath: String): Long

    /** [pcmFloat] must be mono, 16 kHz, normalized to [-1.0, 1.0]. Returns null on failure. */
    external fun nativeTranscribe(handle: Long, pcmFloat: FloatArray, sampleRate: Int): String?

    external fun nativeRelease(handle: Long)
}
