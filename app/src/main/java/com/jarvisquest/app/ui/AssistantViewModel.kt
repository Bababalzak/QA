package com.jarvisquest.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvisquest.app.ai.NotReadyAIService
import com.jarvisquest.app.audio.AudioService
import com.jarvisquest.app.audio.EnergyBasedVad
import com.jarvisquest.app.controller.AssistantController
import com.jarvisquest.app.controller.AssistantUiState
import com.jarvisquest.app.router.CommandRouter
import com.jarvisquest.app.stt.NotYetImplementedSpeechToTextService
import com.jarvisquest.app.tts.AndroidTextToSpeechService
import kotlinx.coroutines.flow.StateFlow

/**
 * Composition root for Milestone 1: this is the one place that decides
 * which concrete implementation backs each interface. Swapping
 * [NotYetImplementedSpeechToTextService] for a whisper.cpp-backed one in
 * Milestone 2, or [NotReadyAIService] for a llama.cpp-backed one in
 * Milestone 3, means editing only this file — [AssistantController] and
 * the UI never change.
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = AssistantController(
        audioService = AudioService(application),
        vad = EnergyBasedVad(),
        stt = NotYetImplementedSpeechToTextService(),
        router = CommandRouter(),
        aiService = NotReadyAIService(),
        tts = AndroidTextToSpeechService(application),
        scope = viewModelScope
    )

    val uiState: StateFlow<AssistantUiState> = controller.uiState

    fun onMicPermissionResult(granted: Boolean) = controller.onMicPermissionResult(granted)

    fun toggleListening() {
        if (controller.isListening()) controller.stopListening() else controller.startListening()
    }

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }
}
