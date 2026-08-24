package com.jarvisquest.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvisquest.app.controller.AssistantState
import com.jarvisquest.app.controller.AssistantUiState
import com.jarvisquest.app.ui.theme.JarvisAccent
import com.jarvisquest.app.ui.theme.JarvisAccentDim
import com.jarvisquest.app.ui.theme.JarvisBackground
import com.jarvisquest.app.ui.theme.JarvisError
import com.jarvisquest.app.ui.theme.JarvisTextSecondary

private fun colorFor(state: AssistantState): Color = when (state) {
    AssistantState.IDLE -> JarvisAccentDim
    AssistantState.LISTENING -> JarvisAccent
    AssistantState.THINKING -> Color(0xFFFFC168)
    AssistantState.SPEAKING -> Color(0xFF6CFFB0)
    AssistantState.ERROR -> JarvisError
}

private fun labelFor(state: AssistantState): String = when (state) {
    AssistantState.IDLE -> "IDLE"
    AssistantState.LISTENING -> "LISTENING"
    AssistantState.THINKING -> "THINKING"
    AssistantState.SPEAKING -> "SPEAKING"
    AssistantState.ERROR -> "ERROR"
}

@Composable
fun AssistantScreen(
    uiState: AssistantUiState,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: the big status orb + mic control — this is what you glance
        // at from across the room, so it gets roughly half the panel.
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusOrb(state = uiState.state)
            Spacer(Modifier.height(32.dp))
            Text(
                text = labelFor(uiState.state),
                style = MaterialTheme.typography.headlineLarge,
                color = colorFor(uiState.state)
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onMicClick,
                enabled = uiState.micPermissionGranted,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisAccentDim),
                modifier = Modifier.height(72.dp)
            ) {
                Text(
                    text = if (uiState.state == AssistantState.IDLE) "TAP TO LISTEN" else "STOP",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            if (!uiState.micPermissionGranted) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Microphone permission required",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisError
                )
            }
        }

        // Right: transcript / response / latency — the development &
        // debugging panel called for while the pipeline is being built out.
        Column(
            modifier = Modifier.weight(1.2f),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            uiState.modelWarning?.let {
                TranscriptCard(title = "Model", body = it, isError = true)
            }
            TranscriptCard(title = "You said", body = uiState.recognizedSpeech.ifBlank { "—" })
            TranscriptCard(
                title = "Jarvis",
                body = uiState.assistantResponse.ifBlank { "—" },
                highlight = true
            )
            uiState.errorMessage?.let {
                TranscriptCard(title = "Error", body = it, isError = true)
            }
            if (uiState.latencyReport.isNotBlank()) {
                TranscriptCard(title = "Latency", body = uiState.latencyReport, monospace = true)
            }
        }
    }
}

@Composable
private fun StatusOrb(state: AssistantState) {
    val transition = rememberInfiniteTransition(label = "orb-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-pulse-value"
    )
    val color = colorFor(state)

    Canvas(modifier = Modifier.size(220.dp)) {
        val radius = size.minDimension / 2f * pulse
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.35f), Color.Transparent),
                center = center,
                radius = radius * 1.6f
            ),
            radius = radius * 1.6f,
            center = center
        )
        drawCircle(color = color.copy(alpha = 0.15f), radius = radius, center = center)
        drawCircle(
            color = color,
            radius = radius * 0.55f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
        )
        drawCircle(color = color, radius = radius * 0.22f, center = center)
    }
}

@Composable
private fun TranscriptCard(
    title: String,
    body: String,
    highlight: Boolean = false,
    isError: Boolean = false,
    monospace: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0F14), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                isError -> JarvisError
                highlight -> JarvisAccent
                else -> JarvisTextSecondary
            }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = if (monospace) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = if (isError) JarvisError else Color(0xFFEAF6F8)
        )
    }
}
