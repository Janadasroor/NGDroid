package com.jnd.ngdroid.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Derives the full Material3 supporting palette from the user's accent so no
 * fixed default (e.g. framework purple) ever leaks into ripples, selection,
 * switches or chips. Pure — JVM-testable.
 */

fun Color.desaturated(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    val gray = 0.299f * red + 0.587f * green + 0.114f * blue
    return copy(
        red = (red + (gray - red) * a).coerceIn(0f, 1f),
        green = (green + (gray - green) * a).coerceIn(0f, 1f),
        blue = (blue + (gray - blue) * a).coerceIn(0f, 1f)
    )
}

/** RGB -> HSV hue in degrees [0, 360). */
fun Color.hueDeg(): Float {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    if (d == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return if (h < 0) h + 360f else h
}

fun hsvToColor(hDeg: Float, s: Float, v: Float, alpha: Float = 1f): Color {
    val h = ((hDeg % 360f) + 360f) % 360f / 60f
    val c = v * s
    val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
    val (r1, g1, b1) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return Color(r1 + m, g1 + m, b1 + m, alpha)
}

/** Same color with saturation/value scaled — hue preserved. */
fun Color.withSv(satScale: Float = 1f, valScale: Float = 1f): Color {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    if (d == 0f) return copy(alpha = alpha) // gray: hue undefined
    val s = if (max == 0f) 0f else d / max
    val h = hueDeg()
    return hsvToColor(h, (s * satScale).coerceIn(0f, 1f), (max * valScale).coerceIn(0f, 1f), alpha)
}

/** Secondary = muted sibling of the accent (same hue family). */
fun secondaryFor(accent: Color): Color = accent.withSv(satScale = 0.55f)

/** Tertiary = accent shifted +40° on the wheel, slightly softened. */
fun tertiaryFor(accent: Color): Color =
    hsvToColor(accent.hueDeg() + 40f, 0.75f, 0.85f, accent.alpha)
