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
import com.jarvisquest.app.ui.AssistantScreen
import com.jarvisquest.app.ui.AssistantViewModel
import com.jarvisquest.app.ui.theme.JarvisQuestTheme

/** Quest voice entry point: microphone -> local VAD -> local Whisper. */
class MainActivity : ComponentActivity() {
    private val viewModel: AssistantViewModel by viewModels()

    private val requestMicPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onMicPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alreadyGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        viewModel.onMicPermissionResult(alreadyGranted)
        if (!alreadyGranted) requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            JarvisQuestTheme {
                val uiState by viewModel.uiState.collectAsState()
                AssistantScreen(uiState = uiState, onMicClick = { viewModel.toggleListening() })
            }
        }
    }
}
