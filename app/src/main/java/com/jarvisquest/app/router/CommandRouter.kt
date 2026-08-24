package com.jarvisquest.app.router

/** Where a recognized transcript should go next. */
sealed class RouteResult {
    /** Handled immediately — no AI round-trip. */
    data class DirectAction(val action: JarvisAction, val spokenAck: String) : RouteResult()

    /** Not a recognized command; pass the raw transcript to [com.jarvisquest.app.ai.AIService]. */
    data class NeedsAI(val prompt: String) : RouteResult()
}

/** The minimal action set for Milestone 1, per the brief's "keep actions minimal" guidance. */
enum class JarvisAction {
    STOP_SPEAKING,
    CLEAR_CONVERSATION
}

/**
 * Deliberately tiny for Milestone 1 — just enough to prove the
 * STT -> Router -> (DirectAction | AIService) split works, per the brief:
 * "create the router architecture but keep the number of actual actions
 * minimal." Real Quest actions (open app, adjust volume, ...) arrive in a
 * later milestone alongside tool calling.
 */
class CommandRouter {

    private val stopPhrases = setOf("stop", "stop talking", "be quiet", "cancel", "houd op", "stil")
    private val clearPhrases = setOf("clear", "reset", "start over", "forget that", "wis alles")

    fun route(transcript: String): RouteResult {
        val normalized = transcript.trim().lowercase()

        if (normalized.isEmpty()) {
            return RouteResult.NeedsAI(transcript)
        }
        if (normalized in stopPhrases) {
            return RouteResult.DirectAction(JarvisAction.STOP_SPEAKING, spokenAck = "")
        }
        if (normalized in clearPhrases) {
            return RouteResult.DirectAction(JarvisAction.CLEAR_CONVERSATION, spokenAck = "Cleared.")
        }
        return RouteResult.NeedsAI(transcript)
    }
}
