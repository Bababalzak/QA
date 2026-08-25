package com.jarvisquest.app.ai

internal object LlamaNative {
    init { System.loadLibrary("jarvis_llama") }

    external fun nativeInit(modelPath: String): Long
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String?
    external fun nativeRelease(handle: Long)
}
