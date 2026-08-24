package com.jarvisquest.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class AndroidTextToSpeechService(context: Context) : TextToSpeechService {

    private var engine: TextToSpeech? = null
    private var ready = false

    init {
        // TextToSpeech's constructor initializes asynchronously; `ready`
        // flips to true (or stays false) in the OnInitListener callback.
        // Applying to `context.applicationContext` avoids leaking an
        // Activity if this service outlives it.
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                // Prefer Dutch (matches the STT language requirement in
                // the project brief) but fall back to the device default,
                // then to US English, rather than failing outright if a
                // Dutch voice isn't installed.
                val dutch = engine?.setLanguage(Locale.forLanguageTag("nl-NL"))
                val dutchOk = dutch == TextToSpeech.LANG_AVAILABLE ||
                    dutch == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    dutch == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                if (!dutchOk) {
                    engine?.setLanguage(Locale.US)
                }
            }
        }
    }

    override fun isAvailable(): Boolean = ready

    override suspend fun speak(text: String): Result<Unit> {
        val tts = engine
        if (!ready || tts == null) {
            return Result.failure(TtsError.EngineUnavailable)
        }
        if (text.isBlank()) return Result.success(Unit)

        val utteranceId = UUID.randomUUID().toString()
        return suspendCancellableCoroutine { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceIdArg: String?) = Unit
                override fun onDone(utteranceIdArg: String?) {
                    if (utteranceIdArg == utteranceId && cont.isActive) {
                        cont.resume(Result.success(Unit))
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceIdArg: String?) {
                    if (utteranceIdArg == utteranceId && cont.isActive) {
                        cont.resume(Result.failure(TtsError.SynthesisFailed("TextToSpeech reported onError for utterance $utteranceIdArg")))
                    }
                }

                override fun onError(utteranceIdArg: String?, errorCode: Int) {
                    if (utteranceIdArg == utteranceId && cont.isActive) {
                        cont.resume(Result.failure(TtsError.SynthesisFailed("TextToSpeech error code $errorCode")))
                    }
                }
            })

            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resume(Result.failure(TtsError.SynthesisFailed("TextToSpeech.speak() returned $result")))
            }

            cont.invokeOnCancellation { tts.stop() }
        }
    }

    override fun stop() {
        engine?.stop()
    }

    override fun release() {
        engine?.shutdown()
        engine = null
        ready = false
    }
}
