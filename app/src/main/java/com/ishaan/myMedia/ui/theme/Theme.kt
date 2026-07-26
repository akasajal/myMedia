package com.ishaan.myMedia.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Palette ──────────────────────────────────────────────────────────────────
val LavenderMist   = Color(0xFFF3E8FF)
val SoftPurple     = Color(0xFFE4BCFF)
val MediumViolet   = Color(0xFFD49FFF)
val PrimaryAccent  = Color(0xFFC47FFF)
val DeepGrape      = Color(0xFF9B59D4)
val RichPlum       = Color(0xFF6B3FA0)
val NearBlack      = Color(0xFF1C1B1F)
val SurfaceLight   = Color(0xFFFAF7FF)
val SurfaceDark    = Color(0xFF13111A)
val CardDark       = Color(0xFF1E1B26)
val SubtleGray     = Color(0xFF79747E)
val ErrorRed       = Color(0xFFBA1A1A)

private val LightColorScheme = lightColorScheme(
    primary           = RichPlum,
    onPrimary         = Color.White,
    primaryContainer  = SoftPurple,
    onPrimaryContainer= RichPlum,
    secondary         = DeepGrape,
    onSecondary       = Color.White,
    secondaryContainer= LavenderMist,
    onSecondaryContainer = RichPlum,
    tertiary          = MediumViolet,
    background        = SurfaceLight,
    onBackground      = NearBlack,
    surface           = SurfaceLight,
    onSurface         = NearBlack,
    surfaceVariant    = LavenderMist,
    onSurfaceVariant  = SubtleGray,
    error             = ErrorRed,
    outline           = SoftPurple,
)

private val DarkColorScheme = darkColorScheme(
    primary           = MediumViolet,
    onPrimary         = Color(0xFF3B006B),
    primaryContainer  = RichPlum,
    onPrimaryContainer= LavenderMist,
    secondary         = SoftPurple,
    onSecondary       = Color(0xFF3B006B),
    secondaryContainer= Color(0xFF4A2A6B),
    onSecondaryContainer = LavenderMist,
    tertiary          = PrimaryAccent,
    background        = SurfaceDark,
    onBackground      = LavenderMist,
    surface           = SurfaceDark,
    onSurface         = LavenderMist,
    surfaceVariant    = CardDark,
    onSurfaceVariant  = SoftPurple,
    error             = Color(0xFFFFB4AB),
    outline           = Color(0xFF4A2A6B),
)

@Composable
fun GalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
