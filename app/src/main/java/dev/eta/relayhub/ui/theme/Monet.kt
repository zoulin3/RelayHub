package dev.eta.relayhub.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * 轻量色调工具：把种子色近似展开成一套 Material 3 色板。
 * 真正的 Monet 动态取色由系统在 Android 12+ 提供，这里只做自定义种子色的兜底实现。
 */
internal data class Hsl(val h: Float, val s: Float, val l: Float)

internal fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val l = (maxC + minC) / 2f
    val s = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when {
        delta == 0f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return Hsl((h + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

internal fun hsl(h: Float, s: Float, l: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val lig = l.coerceIn(0f, 1f)
    val c = (1f - kotlin.math.abs(2f * lig - 1f)) * sat
    val x = c * (1f - kotlin.math.abs(((hue / 60f) % 2f) - 1f))
    val m = lig - c / 2f
    val (r1, g1, b1) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

/** 一套近似 M3 色调 */
internal data class Tones(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outline: Color,
    val outlineVariant: Color,
)

/**
 * 由种子色生成色板。styleFactor 控制饱和度强度，hueShift 控制第三色与强调色的偏转。
 */
internal fun buildTones(
    seed: Color,
    saturationScale: Float,
    hueShift: Float,
    dark: Boolean,
    neutralSurface: Boolean = false,
): Tones {
    val base = seed.toHsl()
    val s = (base.s * saturationScale).coerceIn(0f, 1f)
    val h = base.h

    fun c(hueShiftLocal: Float = 0f, satScale: Float = 1f, light: Float): Color =
        hsl(h + hueShift + hueShiftLocal, (s * satScale).coerceIn(0f, 1f), light)

    val surfaceHue = if (neutralSurface) h else h
    fun surf(satScale: Float, light: Float): Color = hsl(surfaceHue, (s * satScale * 0.55f), light)

    return if (!dark) {
        Tones(
            primary = c(0f, 1f, 0.40f),
            onPrimary = hsl(h, 0.25f, 0.99f),
            primaryContainer = c(0f, 0.55f, 0.90f),
            onPrimaryContainer = c(0f, 0.75f, 0.20f),
            secondary = c(0f, 0.42f, 0.45f),
            onSecondary = hsl(h, 0.20f, 0.99f),
            secondaryContainer = c(0f, 0.30f, 0.91f),
            onSecondaryContainer = c(0f, 0.45f, 0.24f),
            tertiary = c(60f, 0.55f, 0.42f),
            onTertiary = hsl(h + 60f, 0.20f, 0.99f),
            tertiaryContainer = c(60f, 0.38f, 0.90f),
            onTertiaryContainer = c(60f, 0.55f, 0.21f),
            background = surf(0.10f, 0.985f),
            onBackground = surf(0.45f, 0.12f),
            surface = surf(0.10f, 0.985f),
            onSurface = surf(0.45f, 0.12f),
            surfaceVariant = surf(0.30f, 0.92f),
            onSurfaceVariant = surf(0.35f, 0.34f),
            surfaceContainerLowest = hsl(h, 0f, 1.0f),
            surfaceContainerLow = surf(0.14f, 0.965f),
            surfaceContainer = surf(0.18f, 0.945f),
            surfaceContainerHigh = surf(0.24f, 0.915f),
            surfaceContainerHighest = surf(0.30f, 0.885f),
            outline = surf(0.22f, 0.52f),
            outlineVariant = surf(0.28f, 0.80f),
        )
    } else {
        Tones(
            primary = c(0f, 0.72f, 0.80f),
            onPrimary = c(0f, 0.85f, 0.18f),
            primaryContainer = c(0f, 0.75f, 0.30f),
            onPrimaryContainer = c(0f, 0.60f, 0.92f),
            secondary = c(0f, 0.38f, 0.80f),
            onSecondary = c(0f, 0.50f, 0.18f),
            secondaryContainer = c(0f, 0.30f, 0.28f),
            onSecondaryContainer = c(0f, 0.32f, 0.88f),
            tertiary = c(60f, 0.45f, 0.80f),
            onTertiary = c(60f, 0.55f, 0.17f),
            tertiaryContainer = c(60f, 0.42f, 0.29f),
            onTertiaryContainer = c(60f, 0.40f, 0.90f),
            background = surf(0.12f, 0.09f),
            onBackground = surf(0.30f, 0.91f),
            surface = surf(0.12f, 0.09f),
            onSurface = surf(0.30f, 0.91f),
            surfaceVariant = surf(0.28f, 0.28f),
            onSurfaceVariant = surf(0.24f, 0.79f),
            surfaceContainerLowest = surf(0.20f, 0.05f),
            surfaceContainerLow = surf(0.18f, 0.11f),
            surfaceContainer = surf(0.20f, 0.14f),
            surfaceContainerHigh = surf(0.24f, 0.18f),
            surfaceContainerHighest = surf(0.26f, 0.22f),
            outline = surf(0.20f, 0.58f),
            outlineVariant = surf(0.24f, 0.32f),
        )
    }
}
