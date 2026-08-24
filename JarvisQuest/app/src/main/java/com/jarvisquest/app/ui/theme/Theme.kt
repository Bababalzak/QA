package com.jarvisquest.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val JarvisBackground = Color(0xFF05070A)
val JarvisSurface = Color(0xFF0B0F14)
val JarvisAccent = Color(0xFF33E1FF)
val JarvisAccentDim = Color(0xFF12414D)
val JarvisTextPrimary = Color(0xFFEAF6F8)
val JarvisTextSecondary = Color(0xFF7FA6AD)
val JarvisError = Color(0xFFFF5A5A)

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisAccent,
    onPrimary = JarvisBackground,
    secondary = JarvisAccentDim,
    background = JarvisBackground,
    surface = JarvisSurface,
    onBackground = JarvisTextPrimary,
    onSurface = JarvisTextPrimary,
    error = JarvisError
)

// Large, high-contrast type sizes on purpose — this is read from arm's
// length while wearing a headset, not held six inches from the face.
private val JarvisTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
)

@Composable
fun JarvisQuestTheme(content: @Composable () -> Unit) {
    // Always dark, regardless of system setting — a bright theme has no
    // place on a VR panel meant to be read in a dim room.
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = JarvisTypography,
        content = content
    )
}
