package me.ethanhoward.countdown.domain

import me.ethanhoward.countdown.data.TextColorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {
    @Test fun luminanceExtremes() {
        assertEquals(0f, ColorMath.luminance(0xFF000000.toInt()), 1e-4f)
        assertEquals(1f, ColorMath.luminance(0xFFFFFFFF.toInt()), 1e-4f)
    }

    @Test fun autoTextColorPicksReadableColor() {
        assertTrue(ColorMath.useDarkText(TextColorMode.AUTO, ColorMath.luminance(0xFFFAFAFA.toInt())))
        assertTrue(ColorMath.useDarkText(TextColorMode.AUTO, ColorMath.luminance(0xFFF9A825.toInt())))
        assertFalse(ColorMath.useDarkText(TextColorMode.AUTO, ColorMath.luminance(0xFF3F51B5.toInt())))
        assertFalse(ColorMath.useDarkText(TextColorMode.AUTO, ColorMath.luminance(0xFF212121.toInt())))
    }

    @Test fun explicitModesOverrideLuminance() {
        assertFalse(ColorMath.useDarkText(TextColorMode.LIGHT, 1f))
        assertTrue(ColorMath.useDarkText(TextColorMode.DARK, 0f))
    }

    @Test fun hexRoundTrip() {
        assertEquals(0xFF3F51B5.toInt(), ColorMath.parseHex("#3f51b5"))
        assertEquals(0xFF3F51B5.toInt(), ColorMath.parseHex("3F51B5"))
        assertEquals("3F51B5", ColorMath.toHex(0xFF3F51B5.toInt()))
        assertNull(ColorMath.parseHex("12345"))
        assertNull(ColorMath.parseHex("GGGGGG"))
    }
}
