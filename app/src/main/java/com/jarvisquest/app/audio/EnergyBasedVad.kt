package com.jarvisquest.app.audio

import kotlin.math.sqrt

/**
 * Milestone-1/2 "temporary" VAD per the project spec: this is a real,
 * working short-term-energy detector, not a stub. It measures RMS energy
 * per 20 ms frame, tracks an adaptive noise floor during silence, and
 * requires a few consecutive frames above/below threshold before flipping
 * state so brief clicks/pops don't trigger false starts or false stops.
 *
 * It is intentionally simple so it is easy to reason about and to replace:
 * a neural VAD (e.g. Silero VAD via onnxruntime-android, ~2 MB) can
 * implement the same [VoiceActivityDetector] interface later without any
 * change to [com.jarvisquest.app.controller.AssistantController].
 */
class EnergyBasedVad(
    /** Consecutive voiced frames required before declaring SpeechStarted (debounce). */
    private val startFrames: Int = 3,      // 3 * 20 ms = 60 ms
    /** Consecutive silent frames required before declaring SpeechEnded (hangover). */
    private val endFrames: Int = 25,       // 25 * 20 ms = 500 ms
    /** How much louder than the noise floor a frame must be to count as speech. */
    private val energyMultiplier: Double = 3.0,
    /** Absolute floor so a dead-silent room doesn't self-trigger on rounding noise. */
    private val minAbsoluteThreshold: Double = 150.0,
    /** How quickly the noise floor adapts to the ambient room during silence. */
    private val noiseFloorAlpha: Double = 0.05
) : VoiceActivityDetector {

    private var noiseFloor = minAbsoluteThreshold
    private var consecutiveAbove = 0
    private var consecutiveBelow = 0
    private var inSpeech = false
    private val utteranceFrames = mutableListOf<ShortArray>()

    override fun reset() {
        noiseFloor = minAbsoluteThreshold
        consecutiveAbove = 0
        consecutiveBelow = 0
        inSpeech = false
        utteranceFrames.clear()
    }

    override fun process(frame: ShortArray): VadEvent {
        val energy = rms(frame)
        val threshold = (noiseFloor * energyMultiplier).coerceAtLeast(minAbsoluteThreshold)
        val isLoud = energy > threshold

        if (!inSpeech) {
            // Only adapt the noise floor while we believe this is silence,
            // so speech itself never drags the threshold upward.
            noiseFloor = (1 - noiseFloorAlpha) * noiseFloor + noiseFloorAlpha * energy
        }

        if (isLoud) {
            consecutiveAbove++
            consecutiveBelow = 0
        } else {
            consecutiveBelow++
            consecutiveAbove = 0
        }

        if (!inSpeech) {
            if (isLoud) utteranceFrames.add(frame) // keep pre-roll while debouncing
            if (consecutiveAbove >= startFrames) {
                inSpeech = true
                consecutiveAbove = 0
                return VadEvent.SpeechStarted
            }
            return VadEvent.Silence
        } else {
            utteranceFrames.add(frame)
            if (consecutiveBelow >= endFrames) {
                val combined = concat(utteranceFrames)
                utteranceFrames.clear()
                inSpeech = false
                consecutiveBelow = 0
                return VadEvent.SpeechEnded(combined)
            }
            return VadEvent.SpeechContinuing
        }
    }

    private fun rms(frame: ShortArray): Double {
        if (frame.isEmpty()) return 0.0
        var sumSquares = 0.0
        for (sample in frame) sumSquares += (sample.toDouble() * sample.toDouble())
        return sqrt(sumSquares / frame.size)
    }

    private fun concat(frames: List<ShortArray>): ShortArray {
        val total = frames.sumOf { it.size }
        val out = ShortArray(total)
        var offset = 0
        for (f in frames) {
            System.arraycopy(f, 0, out, offset, f.size)
            offset += f.size
        }
        return out
    }
}
