package com.jarvisquest.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.jarvisquest.app.stt.AndroidSpeechRecognizer
import com.jarvisquest.app.ui.AssistantScreen
import com.jarvisquest.app.ui.AssistantViewModel
import com.jarvisquest.app.ui.theme.JarvisQuestTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AssistantViewModel by viewModels()
    private lateinit var speechRecognizer: AndroidSpeechRecognizer
    private var usingSystemSpeech = false

    private val requestMicPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onMicPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alreadyGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        viewModel.onMicPermissionResult(alreadyGranted)
        if (!alreadyGranted) requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)

        speechRecognizer = AndroidSpeechRecognizer(
            context = this,
            onStart = { usingSystemSpeech = true; viewModel.beginExternalSpeech() },
            onPartial = viewModel::showExternalPartial,
            onFinal = viewModel::processExternalTranscript,
            onError = { message ->
                if (usingSystemSpeech) viewModel.externalSpeechError(message)
                usingSystemSpeech = false
            }
        )

        setContent {
            JarvisQuestTheme {
                val uiState by viewModel.uiState.collectAsState()
                AssistantScreen(uiState = uiState, onMicClick = { toggleVoice() })
            }
        }
    }

    private fun toggleVoice() {
        if (!viewModel.uiState.value.micPermissionGranted) return
        if (usingSystemSpeech) {
            speechRecognizer.stop()
            usingSystemSpeech = false
            return
        }
        if (speechRecognizer.isAvailable) {
            usingSystemSpeech = true
            speechRecognizer.start()
        } else {
            viewModel.toggleListening()
        }
    }

    override fun onDestroy() {
        speechRecognizer.release()
        super.onDestroy()
    }
}
