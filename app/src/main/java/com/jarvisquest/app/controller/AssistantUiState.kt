package com.jarvisquest.app.controller

enum class AssistantState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

data class AssistantUiState(
    val state: AssistantState = AssistantState.IDLE,
    val recognizedSpeech: String = "",
    val assistantResponse: String = "",
    val latencyReport: String = "",
    val errorMessage: String? = null,
    val micPermissionGranted: Boolean = false,
    /** Non-null when a required model file is missing — shown as a persistent banner, not just after a failed attempt. */
    val modelWarning: String? = null
)
