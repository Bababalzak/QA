package com.jarvisquest.app.audio

import kotlin.math.sqrt

/** Fast energy-based VAD for short conversational commands. */
class EnergyBasedVad(
    private val startFrames: Int = 2,
    private val endFrames: Int = 10,
    private val energyMultiplier: Double = 3.0,
    private val minAbsoluteThreshold: Double = 150.0,
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
            if (isLoud) utteranceFrames.add(frame)
            if (consecutiveAbove >= startFrames) {
                inSpeech = true
                consecutiveAbove = 0
                return VadEvent.SpeechStarted
            }
            return VadEvent.Silence
        }

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

    private fun rms(frame: ShortArray): Double {
        if (frame.isEmpty()) return 0.0
        var sumSquares = 0.0
        for (sample in frame) sumSquares += sample.toDouble() * sample.toDouble()
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
