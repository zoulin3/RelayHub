package dev.relayhub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.relayhub.data.ColorSource
import dev.relayhub.data.SchemeStyle
import dev.relayhub.data.ThemeMode
import dev.relayhub.data.ThemePrefs

@Composable
fun RelayHubTheme(
    prefs: ThemePrefs,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (prefs.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val context = LocalContext.current
    val scheme: ColorScheme = remember(dark, prefs.colorSource, prefs.accent, prefs.style) {
        if (prefs.colorSource == ColorSource.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            val tones = buildTones(
                seed = Color(prefs.accent.seed.toInt()),
                saturationScale = prefs.style.saturationScale,
                hueShift = prefs.style.hueShift,
                dark = dark,
                neutralSurface = prefs.style == SchemeStyle.Neutral ||
                    prefs.style == SchemeStyle.Monochrome,
            )
            if (dark) tones.toDarkScheme(prefs.pureBlack) else tones.toLightScheme()
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = RelayTypography,
        shapes = RelayShapes,
        content = content,
    )
}

private val SchemeStyle.saturationScale: Float
    get() = when (this) {
        SchemeStyle.TonalSpot -> 1.0f
        SchemeStyle.Vibrant -> 1.45f
        SchemeStyle.Expressive -> 1.35f
        SchemeStyle.Neutral -> 0.35f
        SchemeStyle.Monochrome -> 0.02f
        SchemeStyle.Fidelity -> 1.15f
    }

private val SchemeStyle.hueShift: Float
    get() = when (this) {
        SchemeStyle.Expressive -> 18f
        else -> 0f
    }

private fun Tones.toLightScheme() = lightColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    secondary = secondary, onSecondary = onSecondary,
    secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
    background = background, onBackground = onBackground,
    surface = surface, onSurface = onSurface,
    surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    outline = outline, outlineVariant = outlineVariant,
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

private fun Tones.toDarkScheme(pureBlack: Boolean) = darkColorScheme(
    primary = primary, onPrimary = onPrimary,
    primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    secondary = secondary, onSecondary = onSecondary,
    secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
    background = if (pureBlack) Color.Black else background,
    onBackground = onBackground,
    surface = if (pureBlack) Color.Black else surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = if (pureBlack) Color.Black else surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    outline = outline, outlineVariant = outlineVariant,
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
)
