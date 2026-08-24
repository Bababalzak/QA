package com.jarvisquest.app.stt

/**
 * Used when [WhisperSpeechToTextService] exists and is wired up but
 * [com.jarvisquest.app.model.ModelManager] found no model file on this
 * device. Deliberately a different class (and message) from
 * [NotYetImplementedSpeechToTextService] — that one means "this code
 * doesn't exist yet"; this one means "the code exists, the file is just
 * missing on this particular device," which is a different, actionable
 * problem the person can fix without waiting on a future milestone.
 *
 * Message is in Dutch per the project brief's exact wording
 * ("Whisper model ontbreekt"), since Dutch is this project's primary
 * language.
 */
class ModelMissingSpeechToTextService(private val expectedPath: String) : SpeechToTextService {

    override fun isAvailable(): Boolean = false

    override suspend fun transcribe(pcm16Mono: ShortArray, sampleRateHz: Int): Result<String> =
        Result.failure(
            SttError.RecognitionFailed(
                "Whisper model ontbreekt. Plaats ggml-base.bin op: $expectedPath"
            )
        )

    override fun release() = Unit
}
