/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

import android.content.Context
import android.graphics.Color
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Locks the v2.3 per-preset color data model (§6.2/§8.1, Q2): persistence round-trip, name-based
 * back-compat migration for color-less stored presets, malformed-hex fallback, and the locked
 * default palette. Pure data layer — no UI, no device.
 */
@RunWith(RobolectricTestRunner::class)
class VoicePresetColorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun prefs() = context.getSharedPreferences("lanboard_presets", Context.MODE_PRIVATE)

    @Test fun `fresh install seeds the five locked default colors`() {
        val byName = VoicePresetManager(context).getPresets().associateBy { it.name }
        assertEquals(LBPresetPalette.CODING_HEX, byName["Coding"]?.colorHex)
        assertEquals(LBPresetPalette.EMAIL_HEX, byName["Email"]?.colorHex)
        assertEquals(LBPresetPalette.PERSONAL_HEX, byName["Personal"]?.colorHex)
        assertEquals(LBPresetPalette.TERMINAL_HEX, byName["Terminal"]?.colorHex)
        assertEquals(LBPresetPalette.GENERAL_HEX, byName["General"]?.colorHex)
    }

    @Test fun `colorHex survives a save reload round-trip`() {
        val mgr = VoicePresetManager(context)
        mgr.savePresets(listOf(VoicePresetManager.Preset("Work", "prompt", colorHex = "#abcdef")))
        assertEquals("#abcdef", VoicePresetManager(context).getPresets().single().colorHex)
    }

    @Test fun `color-less stored presets migrate to the locked default for their name`() {
        // simulate a pre-color stored payload (no "colorHex" key)
        prefs().edit {
            putString(
                "presets",
                """[{"name":"Coding","promptText":"p","isDefault":true},
                    {"name":"MyCustom","promptText":"p","isDefault":false}]""".trimIndent()
            )
        }
        val byName = VoicePresetManager(context).getPresets().associateBy { it.name }
        // known locked name -> its locked hex; unknown name -> General cyan; nothing throws
        assertEquals(LBPresetPalette.CODING_HEX, byName["Coding"]?.colorHex)
        assertEquals(LBPresetPalette.GENERAL_CYAN_HEX, byName["MyCustom"]?.colorHex)
    }

    @Test fun `a malformed stored hex never crashes and falls back to General cyan`() {
        val p = VoicePresetManager.Preset("Bad", "p", colorHex = "not-a-color")
        assertEquals(Color.parseColor(LBPresetPalette.GENERAL_HEX), p.colorInt())
    }

    @Test fun `addPreset without a color defaults to General cyan`() {
        val mgr = VoicePresetManager(context)
        mgr.addPreset(VoicePresetManager.Preset("Notes", "p"))
        val added = VoicePresetManager(context).getPresets().single { it.name == "Notes" }
        assertEquals(LBPresetPalette.GENERAL_CYAN_HEX, added.colorHex)
    }

    @Test fun `palette helpers resolve locked names and reject the reserved hues`() {
        assertEquals(LBPresetPalette.EMAIL_HEX, LBPresetPalette.defaultHexForName("Email"))
        assertEquals(LBPresetPalette.GENERAL_CYAN_HEX, LBPresetPalette.defaultHexForName("Whatever"))
        // status hues stay reserved (never offered as user swatches)
        assertTrue(LBPresetPalette.RESERVED_STATUS_HEXES.containsAll(listOf("#3ddc97", "#f0b429", "#ef4444")))
    }
}
