package com.jarvisquest.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvisquest.app.ai.QwenAIService
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
import kotlinx.coroutines.launch

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = ModelManager(application)

    private val stt: SpeechToTextService = when (val status = modelManager.checkWhisperModel()) {
        is ModelStatus.Ready -> WhisperSpeechToTextService(status.path)
        is ModelStatus.Missing -> ModelMissingSpeechToTextService(status.expectedPath)
    }

    private val qwenStatus = modelManager.checkQwenModel()
    private val aiService = QwenAIService(
        (qwenStatus as? ModelStatus.Ready)?.path
            ?: (qwenStatus as ModelStatus.Missing).expectedPath
    )

    private val controller = AssistantController(
        audioService = AudioService(application),
        vad = EnergyBasedVad(),
        stt = stt,
        router = CommandRouter(),
        aiService = aiService,
        tts = AndroidTextToSpeechService(application),
        scope = viewModelScope
    )

    val uiState: StateFlow<AssistantUiState> = controller.uiState

    init {
        val warnings = buildList {
            val whisper = modelManager.checkWhisperModel()
            if (whisper is ModelStatus.Missing) add("Whisper model ontbreekt. Plaats ${ModelManager.WHISPER_MODEL_FILENAME} in:\n${whisper.expectedPath}")
            val qwen = modelManager.checkQwenModel()
            if (qwen is ModelStatus.Missing) add("Qwen model ontbreekt. Plaats ${ModelManager.QWEN_MODEL_FILENAME} in:\n${qwen.expectedPath}")
        }
        if (warnings.isNotEmpty()) controller.setModelWarning(warnings.joinToString("\n\n"))

        if (qwenStatus is ModelStatus.Ready) {
            viewModelScope.launch { aiService.loadModel() }
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
