package com.jarvisquest.app.controller

import android.util.Log
import com.jarvisquest.app.audio.AUDIO_SAMPLE_RATE_HZ
import com.jarvisquest.app.audio.AudioService
import com.jarvisquest.app.audio.AudioServiceError
import com.jarvisquest.app.audio.VadEvent
import com.jarvisquest.app.audio.VoiceActivityDetector
import com.jarvisquest.app.ai.AIService
import com.jarvisquest.app.diagnostics.LatencyTracker
import com.jarvisquest.app.router.CommandRouter
import com.jarvisquest.app.router.JarvisAction
import com.jarvisquest.app.router.RouteResult
import com.jarvisquest.app.stt.SpeechToTextService
import com.jarvisquest.app.tts.TextToSpeechService
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val TAG = "JarvisAssistant"

/** Reliable Quest voice pipeline: microphone -> VAD -> one Whisper decode -> router -> AI/TTS. */
class AssistantController(
    private val audioService: AudioService,
    private val vad: VoiceActivityDetector,
    initialStt: SpeechToTextService,
    private val router: CommandRouter,
    private val aiService: AIService,
    private val tts: TextToSpeechService,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState
    private var stt: SpeechToTextService = initialStt
    private var captureJob: Job? = null
    private var utteranceJob: Job? = null
    private var utteranceLatency: LatencyTracker? = null

    fun setSpeechToTextService(service: SpeechToTextService) {
        if (captureJob?.isActive == true) stopListening()
        stt.release(); stt = service
        _uiState.value = _uiState.value.copy(modelWarning = null)
        scope.launch { if (!service.isAvailable()) Log.w(TAG, "Whisper warm-up failed") }
    }

    fun onMicPermissionResult(granted: Boolean) { _uiState.value = _uiState.value.copy(micPermissionGranted = granted) }
    fun setModelWarning(message: String?) { _uiState.value = _uiState.value.copy(modelWarning = message) }
    fun isListening(): Boolean = captureJob?.isActive == true

    fun beginExternalSpeech() {
        tts.stop(); _uiState.value = _uiState.value.copy(state = AssistantState.LISTENING, errorMessage = null, assistantResponse = "")
    }
    fun showExternalPartial(text: String) {
        if (text.isNotBlank()) _uiState.value = _uiState.value.copy(state = AssistantState.LISTENING, recognizedSpeech = text)
    }
    fun processExternalTranscript(transcript: String) {
        if (transcript.isBlank()) return
        val latency = LatencyTracker().also { it.mark("speech_start"); it.mark("speech_end"); it.mark("stt_start"); it.mark("stt_end") }
        _uiState.value = _uiState.value.copy(state = AssistantState.THINKING, recognizedSpeech = transcript, assistantResponse = "", errorMessage = null)
        scope.launch { routeAndRespond(transcript, latency) }
    }
    fun externalSpeechError(message: String) { _uiState.value = _uiState.value.copy(state = AssistantState.LISTENING, errorMessage = message) }

    fun startListening() {
        if (isListening()) return
        if (!audioService.hasRecordAudioPermission()) {
            _uiState.value = _uiState.value.copy(state = AssistantState.ERROR, errorMessage = "Microphone permission not granted.")
            return
        }
        utteranceJob?.cancel()
        vad.reset()
        _uiState.value = _uiState.value.copy(state = AssistantState.LISTENING, errorMessage = null, assistantResponse = "")
        captureJob = audioService.captureFrames().onEach { handleFrame(it) }.catch { handleCaptureError(it) }.launchIn(scope)
    }

    fun stopListening() {
        captureJob?.cancel(); captureJob = null
        utteranceJob?.cancel(); utteranceJob = null
        tts.stop(); _uiState.value = _uiState.value.copy(state = AssistantState.IDLE)
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
                _uiState.value = _uiState.value.copy(state = AssistantState.LISTENING, errorMessage = null)
            }
            is VadEvent.SpeechContinuing -> Unit
            is VadEvent.SpeechEnded -> {
                val latency = (utteranceLatency ?: LatencyTracker()).also { it.mark("speech_end") }
                utteranceLatency = null
                // One and only one Whisper inference per utterance. Rolling partial
                // inference was removed because it could monopolize the native
                // Whisper context and make the final transcript arrive very late.
                utteranceJob?.cancel()
                utteranceJob = scope.launch { processUtterance(event.utterance, latency) }
            }
        }
    }

    private suspend fun processUtterance(utterance: ShortArray, latency: LatencyTracker) {
        latency.mark("stt_start")
        _uiState.value = _uiState.value.copy(state = AssistantState.THINKING)
        val result = stt.transcribe(utterance, AUDIO_SAMPLE_RATE_HZ)
        latency.mark("stt_end")
        result.fold(
            onSuccess = { transcript ->
                if (transcript.isBlank()) {
                    _uiState.value = _uiState.value.copy(state = AssistantState.LISTENING, errorMessage = "No speech detected.", latencyReport = buildLatencyReport(latency))
                } else {
                    // Publish You said immediately after the single STT pass, then route.
                    _uiState.value = _uiState.value.copy(recognizedSpeech = transcript, assistantResponse = "", state = AssistantState.THINKING)
                    routeAndRespond(transcript, latency)
                }
            },
            onFailure = { error -> _uiState.value = _uiState.value.copy(state = AssistantState.LISTENING, errorMessage = error.message ?: "Speech-to-text failed.", latencyReport = buildLatencyReport(latency)) }
        )
    }

    private suspend fun routeAndRespond(transcript: String, latency: LatencyTracker) {
        _uiState.value = _uiState.value.copy(recognizedSpeech = transcript)
        latency.mark("router_start")
        val route = router.route(transcript)
        latency.mark("router_end")
        when (route) {
            is RouteResult.DirectAction -> {
                applyDirectAction(route.action)
                if (route.spokenAck.isNotBlank()) speak(route.spokenAck, latency)
                _uiState.value = _uiState.value.copy(state = AssistantState.LISTENING, assistantResponse = route.spokenAck, latencyReport = buildLatencyReport(latency))
            }
            is RouteResult.NeedsAI -> {
                latency.mark("llm_start")
                val streamedText = StringBuilder()
                val result = aiService.generate(route.prompt) { token ->
                    streamedText.append(token)
                    _uiState.value = _uiState.value.copy(state = AssistantState.THINKING, assistantResponse = streamedText.toString())
                }
                latency.mark("llm_end")
                result.fold(
                    onSuccess = { reply -> _uiState.value = _uiState.value.copy(assistantResponse = reply); speak(reply, latency) },
                    onFailure = { error -> _uiState.value = _uiState.value.copy(assistantResponse = error.message ?: "The local model isn't ready yet.") }
                )
                _uiState.value = _uiState.value.copy(state = AssistantState.LISTENING, latencyReport = buildLatencyReport(latency))
            }
        }
    }

    private suspend fun speak(text: String, latency: LatencyTracker) {
        _uiState.value = _uiState.value.copy(state = AssistantState.SPEAKING)
        latency.mark("tts_start"); tts.speak(text); latency.mark("tts_end")
    }
    private fun applyDirectAction(action: JarvisAction) {
        when (action) {
            JarvisAction.STOP_SPEAKING -> tts.stop()
            JarvisAction.CLEAR_CONVERSATION -> _uiState.value = _uiState.value.copy(recognizedSpeech = "", assistantResponse = "")
        }
    }
    private fun buildLatencyReport(latency: LatencyTracker): String = latency.summary(buildList {
        if (latency.durationMs("speech_start", "speech_end") != null) add("Speech" to ("speech_start" to "speech_end"))
        add("STT" to ("stt_start" to "stt_end")); add("Router" to ("router_start" to "router_end"))
        if (latency.durationMs("llm_start", "llm_end") != null) add("LLM" to ("llm_start" to "llm_end"))
        if (latency.durationMs("tts_start", "tts_end") != null) add("TTS" to ("tts_start" to "tts_end"))
    })
    fun release() { stopListening(); stt.release(); aiService.release(); tts.release() }
}
