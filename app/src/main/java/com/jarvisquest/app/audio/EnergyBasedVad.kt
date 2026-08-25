package com.jarvisquest.app.audio

import kotlin.math.sqrt

/** Low-latency VAD tuned for Quest microphone noise. */
class EnergyBasedVad(
    private val startFrames: Int = 2,       // 40 ms speech start
    private val endFrames: Int = 5,         // 100 ms silence ends speech
    private val energyMultiplier: Double = 2.0,
    private val minAbsoluteThreshold: Double = 300.0,
    private val noiseFloorAlpha: Double = 0.05,
    private val maxUtteranceFrames: Int = 400 // 8 seconds hard safety cap
) : VoiceActivityDetector {

    private var noiseFloor = minAbsoluteThreshold
    private var consecutiveAbove = 0
    private var consecutiveBelow = 0
    private var inSpeech = false
    private var speechFrames = 0
    private val utteranceFrames = mutableListOf<ShortArray>()

    override fun reset() {
        noiseFloor = minAbsoluteThreshold
        consecutiveAbove = 0
        consecutiveBelow = 0
        inSpeech = false
        speechFrames = 0
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
                speechFrames = utteranceFrames.size
                consecutiveAbove = 0
                return VadEvent.SpeechStarted
            }
            return VadEvent.Silence
        }

        utteranceFrames.add(frame)
        speechFrames++

        // Never wait indefinitely if the Quest microphone reports background noise.
        if (speechFrames >= maxUtteranceFrames) {
            return finishUtterance()
        }

        if (consecutiveBelow >= endFrames) {
            return finishUtterance()
        }
        return VadEvent.SpeechContinuing
    }

    private fun finishUtterance(): VadEvent {
        val combined = concat(utteranceFrames)
        utteranceFrames.clear()
        inSpeech = false
        speechFrames = 0
        consecutiveBelow = 0
        consecutiveAbove = 0
        return if (combined.isEmpty()) VadEvent.Silence else VadEvent.SpeechEnded(combined)
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
