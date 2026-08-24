package com.jarvisquest.app.diagnostics

/**
 * Records wall-clock timings for one turn of the voice pipeline
 * (VAD -> STT -> Router -> AI -> TTS), so the numbers shown in the UI and
 * printed to Logcat are always measured, never invented.
 *
 * Usage:
 *   val t = LatencyTracker()
 *   t.mark("vad_start"); ...; t.mark("vad_end")
 *   t.mark("stt_start"); ...; t.mark("stt_end")
 *   ...
 *   val report = t.summary()
 *
 * This is intentionally simple (a list of named timestamps) rather than a
 * fixed set of stages, so new stages (router, first-token, tts-startup,
 * ...) can be added in later milestones without changing this class.
 */
class LatencyTracker {

    private data class Mark(val label: String, val atNanos: Long)

    private val marks = mutableListOf<Mark>()
    private val startNanos = System.nanoTime()

    /** Records "now" under [label]. Call at the start and end of each stage. */
    fun mark(label: String) {
        marks.add(Mark(label, System.nanoTime()))
    }

    /** Milliseconds between two marks, or null if either mark is missing. */
    fun durationMs(fromLabel: String, toLabel: String): Long? {
        val from = marks.firstOrNull { it.label == fromLabel } ?: return null
        val to = marks.lastOrNull { it.label == toLabel } ?: return null
        return (to.atNanos - from.atNanos) / 1_000_000
    }

    /** Milliseconds from pipeline start to a given mark. */
    fun sinceStartMs(label: String): Long? {
        val to = marks.firstOrNull { it.label == label } ?: return null
        return (to.atNanos - startNanos) / 1_000_000
    }

    /** Total elapsed time since this tracker was created. */
    fun totalMs(): Long = (System.nanoTime() - startNanos) / 1_000_000

    /**
     * Produces a human-readable breakdown, e.g.:
     *   VAD: 118 ms
     *   STT: 642 ms
     *   Router: 3 ms
     *   Total: 763 ms
     */
    fun summary(stages: List<Pair<String, Pair<String, String>>>): String {
        val lines = stages.mapNotNull { (name, range) ->
            val (from, to) = range
            durationMs(from, to)?.let { "$name: $it ms" }
        }
        return (lines + "Total: ${totalMs()} ms").joinToString("\n")
    }
}
