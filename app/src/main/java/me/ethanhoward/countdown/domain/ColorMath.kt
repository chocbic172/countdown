package me.ethanhoward.countdown.domain

import kotlin.math.pow
import me.ethanhoward.countdown.data.TextColorMode

object ColorMath {
    /** WCAG relative luminance (0..1) of an ARGB color; alpha is ignored. */
    fun luminance(argb: Int): Float {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return (0.2126 * r + 0.7152 * g + 0.0722 * b).toFloat()
    }

    /**
     * Whether text over a background of [backgroundLuminance] should be dark. The threshold is the
     * luminance at which black and white text have equal contrast (~0.179).
     */
    fun useDarkText(mode: TextColorMode, backgroundLuminance: Float): Boolean = when (mode) {
        TextColorMode.LIGHT -> false
        TextColorMode.DARK -> true
        TextColorMode.AUTO -> backgroundLuminance > 0.179f
    }

    fun toHex(argb: Int): String = "%06X".format(argb and 0xFFFFFF)

    /** Parses "RRGGBB" or "#RRGGBB" to an opaque ARGB int, or null if invalid. */
    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6) return null
        return hex.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
    }
}
