package com.jarvisquest.app.ai

internal object LlamaNative {
    init { System.loadLibrary("jarvis_llama") }

    external fun nativeInit(modelPath: String): Long
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String?
    external fun nativeGenerateStreaming(handle: Long, prompt: String, maxTokens: Int, callback: TokenCallback): String?
    external fun nativeRelease(handle: Long)

    fun interface TokenCallback {
        fun onToken(text: String)
    }
}
