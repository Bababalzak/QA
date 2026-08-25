package com.jarvisquest.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvisquest.app.ai.QwenAIService
import com.jarvisquest.app.audio.AudioService
import com.jarvisquest.app.audio.EnergyBasedVad
import com.jarvisquest.app.controller.AssistantController
import com.jarvisquest.app.controller.AssistantUiState
import com.jarvisquest.app.model.ModelDownloader
import com.jarvisquest.app.model.ModelManager
import com.jarvisquest.app.model.ModelStatus
import com.jarvisquest.app.stt.ModelMissingSpeechToTextService
import com.jarvisquest.app.stt.SpeechToTextService
import com.jarvisquest.app.stt.WhisperSpeechToTextService
import com.jarvisquest.app.router.CommandRouter
import com.jarvisquest.app.tts.AndroidTextToSpeechService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = ModelManager(application)
    private val modelDownloader = ModelDownloader(application)

    private val initialStt: SpeechToTextService = when (val status = modelManager.checkWhisperModel()) {
        is ModelStatus.Ready -> WhisperSpeechToTextService(status.path)
        is ModelStatus.Missing -> ModelMissingSpeechToTextService(status.expectedPath)
    }

    private var qwenModelPath = (modelManager.checkQwenModel() as? ModelStatus.Ready)?.path
    private val aiService = QwenAIService(
        qwenModelPath ?: (modelManager.checkQwenModel() as ModelStatus.Missing).expectedPath
    )

    private val controller = AssistantController(
        audioService = AudioService(application),
        vad = EnergyBasedVad(),
        initialStt = initialStt,
        router = CommandRouter(),
        aiService = aiService,
        tts = AndroidTextToSpeechService(application),
        scope = viewModelScope
    )

    val uiState: StateFlow<AssistantUiState> = controller.uiState

    init {
        viewModelScope.launch {
            controller.setModelWarning("Modellen controleren...")
            val allModels = modelDownloader.ensureAllModels()
            allModels.fold(
                onSuccess = { paths ->
                    controller.setSpeechToTextService(WhisperSpeechToTextService(paths.first))
                    if (qwenModelPath == null) qwenModelPath = paths.second
                    aiService.loadModel().onFailure { error ->
                        controller.setModelWarning("AI-model laden mislukt: ${error.message}")
                    }
                    if (qwenModelPath != null && controller.uiState.value.modelWarning == "Modellen controleren...") {
                        controller.setModelWarning(null)
                    }
                },
                onFailure = { error ->
                    controller.setModelWarning("Model download mislukt: ${error.message}")
                }
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
