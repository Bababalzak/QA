package com.jarvisquest.app.stt

/**
 * Milestone 1 deliberately ships WITHOUT a working speech-to-text engine.
 * This is not an oversight — two real constraints ruled out a quick stub
 * that "sort of" works, and faking a transcript was explicitly out of
 * scope for this project:
 *
 * 1. Android's built-in [android.speech.SpeechRecognizer] does not accept
 *    a pre-captured PCM buffer — it owns the microphone itself for the
 *    duration of a `startListening()` session. That is architecturally
 *    incompatible with this app's AudioService -> VAD -> STT pipeline,
 *    where the VAD (not the recognizer) decides when an utterance starts
 *    and ends. Wiring it in anyway would mean silently bypassing the VAD,
 *    which is exactly the kind of "architecture that needs to be
 *    rewritten later" the project brief says to avoid.
 *
 * 2. Meta Quest ships without Google Mobile Services, so even where the
 *    platform API shape fits, `SpeechRecognizer.isRecognitionAvailable()`
 *    is expected to return false on-device — there is no confirmed local
 *    recognition service to bind to.
 *
 * The real Milestone 2 implementation is expected to be a whisper.cpp JNI
 * binding (an official Android example exists upstream, see
 * `examples/whisper.android` in ggerganov/whisper.cpp) using a small
 * multilingual model such as ggml-base or ggml-small for Dutch + English
 * support, fed directly with the PCM16 buffer this interface already
 * expects. That implementation needs the NDK + CMake native build step,
 * which could not be exercised in the sandbox this project was scaffolded
 * in (no Android NDK, no network to fetch model weights) — see README.md.
 */
class NotYetImplementedSpeechToTextService : SpeechToTextService {

    override fun isAvailable(): Boolean = false

    override suspend fun transcribe(pcm16Mono: ShortArray, sampleRateHz: Int): Result<String> =
        Result.failure(
            SttError.RecognitionFailed(
                "Local speech-to-text isn't wired up yet (Milestone 2 — whisper.cpp). " +
                    "Captured ${pcm16Mono.size} samples at $sampleRateHz Hz; nothing was transcribed."
            )
        )

    override fun release() {
        // Nothing to release — no engine handle exists yet.
    }
}
