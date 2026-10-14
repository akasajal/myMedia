package com.ishaan.myMedia.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ishaan.myMedia.data.model.AccentColor

private val LocalAccent = staticCompositionLocalOf { Color(AccentColor.PURPLE.argb) }

// Shared accents used by the album cards, viewer, editor, and player.
val PrimaryAccent: Color
    @Composable get() = LocalAccent.current
val SoftPurple: Color
    @Composable get() = lerp(LocalAccent.current, Color.White, 0.55f)

val AccentForeground = Color(0xFF1C1B1F)

private fun readableAccent(accent: Color, background: Color): Color {
    val backgroundLuminance = background.luminance()
    for (step in 0..100) {
        val candidate = lerp(accent, Color.Black, step / 100f)
        val ratio = (backgroundLuminance + 0.05f) / (candidate.luminance() + 0.05f)
        if (ratio >= 4.5f) return candidate
    }
    return Color.Black
}

private fun galleryColors(darkTheme: Boolean, accent: Color): ColorScheme {
    val primaryAccent = lerp(accent, Color.White, 0.35f)
    val secondaryAccent = lerp(accent, Color.White, 0.55f)
    val pale = lerp(accent, Color.White, 0.80f)
    val deep = lerp(accent, Color.Black, 0.65f)
    val lightAccent = readableAccent(accent, Color(0xFFFAF8FB))
    return if (darkTheme) darkColorScheme(
        primary = primaryAccent,
        onPrimary = deep,
        primaryContainer = deep,
        onPrimaryContainer = pale,
        secondary = secondaryAccent,
        onSecondary = deep,
        secondaryContainer = lerp(accent, Color.Black, 0.75f),
        onSecondaryContainer = pale,
        tertiary = accent,
        onTertiary = Color.Black,
        tertiaryContainer = deep,
        onTertiaryContainer = pale,
        background = Color(0xFF141318),
        onBackground = Color(0xFFE8E5EC),
        surface = Color(0xFF141318),
        onSurface = Color(0xFFE8E5EC),
        surfaceVariant = Color(0xFF25232B),
        onSurfaceVariant = Color(0xFFC8C3CE),
        outline = Color(0xFF938D9B),
        outlineVariant = Color(0xFF45414B),
    ) else lightColorScheme(
        primary = lightAccent,
        onPrimary = Color.White,
        primaryContainer = pale,
        onPrimaryContainer = deep,
        secondary = lightAccent,
        onSecondary = Color.White,
        secondaryContainer = lerp(accent, Color.White, 0.90f),
        onSecondaryContainer = deep,
        tertiary = lightAccent,
        onTertiary = Color.White,
        tertiaryContainer = pale,
        onTertiaryContainer = deep,
        background = Color(0xFFFAF8FB),
        onBackground = Color(0xFF333038),
        surface = Color(0xFFFAF8FB),
        onSurface = Color(0xFF333038),
        surfaceVariant = Color(0xFFF0EDF3),
        onSurfaceVariant = Color(0xFF706B76),
        outline = Color(0xFF8A8490),
        outlineVariant = Color(0xFFDED9E2),
    )
}

@Composable
fun GalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.PURPLE,
    content: @Composable () -> Unit
) {
    val accent = Color(accentColor.argb)
    val colorScheme = galleryColors(darkTheme, accent)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
