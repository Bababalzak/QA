package com.jarvisquest.app.controller

import android.util.Log
import com.jarvisquest.app.ai.AIService
import com.jarvisquest.app.audio.AUDIO_SAMPLE_RATE_HZ
import com.jarvisquest.app.audio.AudioService
import com.jarvisquest.app.audio.AudioServiceError
import com.jarvisquest.app.audio.VadEvent
import com.jarvisquest.app.audio.VoiceActivityDetector
import com.jarvisquest.app.diagnostics.LatencyTracker
import com.jarvisquest.app.router.CommandRouter
import com.jarvisquest.app.router.JarvisAction
import com.jarvisquest.app.router.RouteResult
import com.jarvisquest.app.stt.SpeechToTextService
import com.jarvisquest.app.tts.TextToSpeechService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val TAG = "JarvisAssistant"

class AssistantController(
    private val audioService: AudioService,
    private val vad: VoiceActivityDetector,
    private val stt: SpeechToTextService,
    private val router: CommandRouter,
    private val aiService: AIService,
    private val tts: TextToSpeechService,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState

    private var captureJob: Job? = null

    // Created the instant VAD reports SpeechStarted so the latency report
    // covers real speech-start -> speech-end -> STT -> response timing
    // (Phase 8: "microphone start, speech start, speech end, Whisper
    // start, Whisper end, total STT latency"), not just the STT portion.
    private var utteranceLatency: LatencyTracker? = null

    fun onMicPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(micPermissionGranted = granted)
    }

    /** Set once at startup from [com.jarvisquest.app.model.ModelManager]'s check — shown as a persistent banner. */
    fun setModelWarning(message: String?) {
        _uiState.value = _uiState.value.copy(modelWarning = message)
    }

    fun isListening(): Boolean = captureJob?.isActive == true

    fun startListening() {
        if (isListening()) return
        if (!audioService.hasRecordAudioPermission()) {
            _uiState.value = _uiState.value.copy(
                state = AssistantState.ERROR,
                errorMessage = "Microphone permission not granted."
            )
            return
        }

        vad.reset()
        _uiState.value = _uiState.value.copy(
            state = AssistantState.LISTENING,
            errorMessage = null
        )

        captureJob = audioService.captureFrames()
            .onEach { frame -> handleFrame(frame) }
            .catch { error -> handleCaptureError(error) }
            .launchIn(scope)
    }

    fun stopListening() {
        captureJob?.cancel()
        captureJob = null
        tts.stop()
        _uiState.value = _uiState.value.copy(state = AssistantState.IDLE)
    }

    private fun handleCaptureError(error: Throwable) {
        val message = when (error) {
            is AudioServiceError.PermissionDenied -> "Microphone permission was denied."
            is AudioServiceError.DeviceUnavailable -> "No usable microphone was found on this device."
            is AudioServiceError.InitializationFailed -> "Microphone failed to start: ${error.message}"
            else -> "Unexpected audio error: ${error.message}"
        }
        Log.e(TAG, "Audio capture error", error)
        _uiState.value = _uiState.value.copy(state = AssistantState.ERROR, errorMessage = message)
        captureJob = null
    }

    private fun handleFrame(frame: ShortArray) {
        when (val event = vad.process(frame)) {
            is VadEvent.Silence -> Unit
            is VadEvent.SpeechStarted -> {
                utteranceLatency = LatencyTracker().also { it.mark("speech_start") }
                _uiState.value = _uiState.value.copy(state = AssistantState.LISTENING)
            }
            is VadEvent.SpeechContinuing -> Unit
            is VadEvent.SpeechEnded -> {
                // Falls back to a fresh tracker if SpeechStarted was somehow
                // missed (e.g. VAD implementation swapped later) rather than
                // crashing on a null — the report is just less complete.
                val latency = (utteranceLatency ?: LatencyTracker()).also { it.mark("speech_end") }
                utteranceLatency = null
                val utterance = event.utterance
                scope.launch { processUtterance(utterance, latency) }
            }
        }
    }

    private suspend fun processUtterance(utterance: ShortArray, latency: LatencyTracker) {
        latency.mark("stt_start")
        _uiState.value = _uiState.value.copy(state = AssistantState.THINKING)

        val transcriptResult = stt.transcribe(utterance, AUDIO_SAMPLE_RATE_HZ)
        latency.mark("stt_end")

        transcriptResult.onFailure { error ->
            Log.w(TAG, "STT unavailable: ${error.message}")
            _uiState.value = _uiState.value.copy(
                state = AssistantState.LISTENING,
                recognizedSpeech = "",
                assistantResponse = error.message ?: "Speech-to-text failed.",
                latencyReport = buildLatencyReport(latency)
            )
            return
        }

        val transcript = transcriptResult.getOrThrow()
        _uiState.value = _uiState.value.copy(recognizedSpeech = transcript)

        latency.mark("router_start")
        val route = router.route(transcript)
        latency.mark("router_end")

        when (route) {
            is RouteResult.DirectAction -> {
                applyDirectAction(route.action)
                if (route.spokenAck.isNotBlank()) speak(route.spokenAck, latency)
                _uiState.value = _uiState.value.copy(
                    state = AssistantState.LISTENING,
                    assistantResponse = route.spokenAck,
                    latencyReport = buildLatencyReport(latency)
                )
            }
            is RouteResult.NeedsAI -> {
                latency.mark("llm_start")
                val aiResult = aiService.generate(route.prompt)
                latency.mark("llm_end")

                aiResult.fold(
                    onSuccess = { reply ->
                        _uiState.value = _uiState.value.copy(assistantResponse = reply)
                        speak(reply, latency)
                    },
                    onFailure = { error ->
                        val message = error.message ?: "The local model isn't ready yet."
                        _uiState.value = _uiState.value.copy(assistantResponse = message)
                    }
                )
                _uiState.value = _uiState.value.copy(
                    state = AssistantState.LISTENING,
                    latencyReport = buildLatencyReport(latency)
                )
            }
        }
    }

    private suspend fun speak(text: String, latency: LatencyTracker) {
        _uiState.value = _uiState.value.copy(state = AssistantState.SPEAKING)
        latency.mark("tts_start")
        tts.speak(text)
        latency.mark("tts_end")
    }

    private fun applyDirectAction(action: JarvisAction) {
        when (action) {
            JarvisAction.STOP_SPEAKING -> tts.stop()
            JarvisAction.CLEAR_CONVERSATION -> {
                _uiState.value = _uiState.value.copy(recognizedSpeech = "", assistantResponse = "")
            }
        }
    }

    private fun buildLatencyReport(latency: LatencyTracker): String {
        val stages = buildList {
            if (latency.durationMs("speech_start", "speech_end") != null) {
                add("Speech" to ("speech_start" to "speech_end"))
            }
            add("STT" to ("stt_start" to "stt_end"))
            add("Router" to ("router_start" to "router_end"))
            if (latency.durationMs("llm_start", "llm_end") != null) {
                add("LLM" to ("llm_start" to "llm_end"))
            }
            if (latency.durationMs("tts_start", "tts_end") != null) {
                add("TTS" to ("tts_start" to "tts_end"))
            }
        }
        return latency.summary(stages)
    }

    fun release() {
        stopListening()
        stt.release()
        aiService.release()
        tts.release()
    }
}
