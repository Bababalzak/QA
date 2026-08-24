package com.jarvisquest.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvisquest.app.ai.NotReadyAIService
import com.jarvisquest.app.audio.AudioService
import com.jarvisquest.app.audio.EnergyBasedVad
import com.jarvisquest.app.controller.AssistantController
import com.jarvisquest.app.controller.AssistantUiState
import com.jarvisquest.app.model.ModelManager
import com.jarvisquest.app.model.ModelStatus
import com.jarvisquest.app.router.CommandRouter
import com.jarvisquest.app.stt.ModelMissingSpeechToTextService
import com.jarvisquest.app.stt.SpeechToTextService
import com.jarvisquest.app.stt.WhisperSpeechToTextService
import com.jarvisquest.app.tts.AndroidTextToSpeechService
import kotlinx.coroutines.flow.StateFlow

/**
 * Composition root. Milestone 2 change: STT now resolves to a real
 * [WhisperSpeechToTextService] when [ModelManager] finds a model file on
 * device, or an honest [ModelMissingSpeechToTextService] when it doesn't —
 * either way [AssistantController] and the UI are untouched, exactly the
 * point of keeping STT behind an interface from Milestone 1.
 *
 * [NotReadyAIService] is unchanged on purpose: Qwen/llama.cpp are next
 * milestone's work, not this one's.
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val modelManager = ModelManager(application)

    private val stt: SpeechToTextService = when (val status = modelManager.checkWhisperModel()) {
        is ModelStatus.Ready -> WhisperSpeechToTextService(status.path)
        is ModelStatus.Missing -> ModelMissingSpeechToTextService(status.expectedPath)
    }

    private val controller = AssistantController(
        audioService = AudioService(application),
        vad = EnergyBasedVad(),
        stt = stt,
        router = CommandRouter(),
        aiService = NotReadyAIService(),
        tts = AndroidTextToSpeechService(application),
        scope = viewModelScope
    )

    val uiState: StateFlow<AssistantUiState> = controller.uiState

    init {
        val status = modelManager.checkWhisperModel()
        if (status is ModelStatus.Missing) {
            controller.setModelWarning(
                "Whisper model ontbreekt. Plaats ${ModelManager.WHISPER_MODEL_FILENAME} op:\n${status.expectedPath}"
            )
        }
    }

    fun onMicPermissionResult(granted: Boolean) = controller.onMicPermissionResult(granted)

    fun toggleListening() {
        if (controller.isListening()) controller.stopListening() else controller.startListening()
    }

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }
}
