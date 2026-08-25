package com.jarvisquest.app.stt

internal object WhisperNative {
    init { System.loadLibrary("jarvis_whisper") }
    external fun nativeInit(modelPath: String): Long
    external fun nativeTranscribe(handle: Long, pcmFloat: FloatArray, sampleRate: Int): String?
    /** Fast rolling-window transcription used while the user is still speaking. */
    external fun nativeTranscribePartial(handle: Long, pcmFloat: FloatArray, sampleRate: Int): String?
    external fun nativeRelease(handle: Long)
}
