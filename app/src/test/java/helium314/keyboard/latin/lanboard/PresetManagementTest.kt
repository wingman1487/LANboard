/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Locks the v2.4 §9.3 Manage Presets data layer against the approved Q2-manage-presets-screen.html:
 * the curated swatch grid excludes every reserved hue, the seeded presets carry the render's
 * subtitles, reorder persists the single global order, and General is permanent. Pure data — the
 * three-screen Compose rendering itself is device visual-QA.
 */
@RunWith(RobolectricTestRunner::class)
class PresetManagementTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun mgr() = VoicePresetManager(context)

    // ── Curated palette contract (§6.2 / §13.2) ──

    @Test fun `curated grid is the 12 authored swatches and excludes every reserved hue`() {
        val grid = LBPresetPalette.CURATED_SWATCHES
        assertEquals(12, grid.size)
        LBPresetPalette.RESERVED_HEXES.forEach { reserved ->
            assertFalse("reserved $reserved leaked into the grid", grid.any { it.equals(reserved, ignoreCase = true) })
        }
    }

    @Test fun `locked-default markers are all present in the curated grid`() {
        assertTrue(LBPresetPalette.CURATED_SWATCHES.containsAll(LBPresetPalette.LOCKED_DEFAULT_HEXES.toList()))
    }

    @Test fun `the new-preset default is a curated, non-reserved swatch`() {
        assertTrue(LBPresetPalette.CURATED_SWATCHES.contains(LBPresetPalette.NEW_PRESET_HEX))
        assertFalse(LBPresetPalette.RESERVED_HEXES.contains(LBPresetPalette.NEW_PRESET_HEX))
    }

    @Test fun `isGeneral is case-insensitive and only matches General`() {
        assertTrue(LBPresetPalette.isGeneral("General"))
        assertTrue(LBPresetPalette.isGeneral("general"))
        assertFalse(LBPresetPalette.isGeneral("Generalist"))
    }

    // ── Seeded subtitles (render list rows) ──

    @Test fun `fresh install seeds the render subtitles`() {
        val byName = mgr().getPresets().associateBy { it.name }
        assertEquals("Termux · code editors", byName["Coding"]?.description)
        assertEquals("Professional correspondence", byName["Email"]?.description)
        assertEquals("Messages · social", byName["Personal"]?.description)
        assertEquals("Shell · git · docker", byName["Terminal"]?.description)
        assertEquals("Default fallback · the signature", byName["General"]?.description)
    }

    @Test fun `description survives a save reload round-trip`() {
        mgr().savePresets(listOf(VoicePresetManager.Preset("Work", "p", colorHex = "#3b82f6", description = "desk")))
        assertEquals("desk", mgr().getPresets().single().description)
    }

    @Test fun `General sorts last on a fresh install`() {
        assertEquals("General", mgr().getPresets().last().name)
    }

    // ── Reorder (single global order — §9.3) ──

    @Test fun `reorderPresets moves an item and persists the new order`() {
        val before = mgr().getPresets().map { it.name }
        // move the first preset (Coding) down one slot
        mgr().reorderPresets(0, 1)
        val after = mgr().getPresets().map { it.name }
        assertEquals(before[1], after[0])
        assertEquals(before[0], after[1])
        // and it survives a fresh manager (persisted, not just in-memory)
        assertEquals(after, mgr().getPresets().map { it.name })
    }

    @Test fun `reorderPresets ignores out-of-range and no-op moves`() {
        val before = mgr().getPresets().map { it.name }
        mgr().reorderPresets(0, 0)
        mgr().reorderPresets(-1, 2)
        mgr().reorderPresets(0, 99)
        assertEquals(before, mgr().getPresets().map { it.name })
    }

    // ── General permanence (§6.2 / §8.1) ──

    @Test fun `deletePreset refuses to remove General`() {
        val m = mgr()
        m.deletePreset("General")
        assertTrue(m.getPresets().any { it.name == "General" })
    }

    @Test fun `deleting a non-General preset removes it and cascades active to General`() {
        val m = mgr()
        m.setActivePreset("Coding")
        m.deletePreset("Coding")
        assertNull(m.getPresets().find { it.name == "Coding" })
        assertEquals("General", m.getActivePreset()?.name)
    }
}
