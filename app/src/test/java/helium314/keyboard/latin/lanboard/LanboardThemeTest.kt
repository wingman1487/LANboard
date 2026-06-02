/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.DefaultColors
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.content.Context

/**
 * Locks the LANboard Dark theme contract (§6.1/§6.7, Q6):
 * it is the registered install default, on the Rounded style, and is the only colorset that
 * turns on the glass-key renderer.
 */
@RunWith(RobolectricTestRunner::class)
class LanboardThemeTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>().prefs()

    @Test fun `LANboard Dark is the install default colorset, day and night`() {
        assertEquals(KeyboardTheme.THEME_LANBOARD_DARK, Defaults.PREF_THEME_COLORS)
        assertEquals(KeyboardTheme.THEME_LANBOARD_DARK, Defaults.PREF_THEME_COLORS_NIGHT)
    }

    @Test fun `LANboard Dark ships on the Rounded style so glass has rounded corners`() {
        assertEquals(KeyboardTheme.STYLE_ROUNDED, Defaults.PREF_THEME_STYLE)
    }

    @Test fun `LANboard Dark is offered in the colorset picker in both day and night`() {
        assertTrue(KeyboardTheme.getAvailableDefaultColors(prefs, false).contains(KeyboardTheme.THEME_LANBOARD_DARK))
        assertTrue(KeyboardTheme.getAvailableDefaultColors(prefs, true).contains(KeyboardTheme.THEME_LANBOARD_DARK))
    }

    @Test fun `glassKeys is set only when explicitly requested`() {
        // a stock-style colorset (any other theme) must never trigger the glass renderer
        val plain = DefaultColors(
            KeyboardTheme.STYLE_ROUNDED, false,
            0xff00d4ff.toInt(), 0xff0a0d10.toInt(), 0xff232b34.toInt(), 0xff143039.toInt(),
            0xff232b34.toInt(), 0xffe6edf3.toInt(), 0xff9ba8b4.toInt(),
        )
        assertFalse(plain.glassKeys)

        val glass = DefaultColors(
            KeyboardTheme.STYLE_ROUNDED, false,
            0xff00d4ff.toInt(), 0xff0a0d10.toInt(), 0xff232b34.toInt(), 0xff143039.toInt(),
            0xff232b34.toInt(), 0xffe6edf3.toInt(), 0xff9ba8b4.toInt(),
            glassKeys = true,
        )
        assertTrue(glass.glassKeys)
    }
}
