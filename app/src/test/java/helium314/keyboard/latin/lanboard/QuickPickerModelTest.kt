/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Locks the §5.6 quick-picker pure data layer (v2.5 — render contract quick-picker-overlay.html):
 * persisted-order nearest-ring-first fill, active exclusion, per-posture cap + overflow math, the
 * single-/zero-alternative degeneracy, identity-dot color resolution, and the truncation boundary.
 *
 * Membership is driven purely by the persisted §8.1 order, so the rosters here are seeded in a KNOWN
 * order to lock the render's frame-2 (cap 3) and frame-3 (cap 4) splits — not the default install order.
 * Pure data layer — no UI, no device.
 */
@RunWith(RobolectricTestRunner::class)
class QuickPickerModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Seed a known global preset order + active preset, then return a model over it. */
    private fun model(order: List<Pair<String, String>>, active: String): QuickPickerModel {
        val mgr = VoicePresetManager(context)
        mgr.savePresets(order.map { (name, hex) -> VoicePresetManager.Preset(name, "p", colorHex = hex) })
        mgr.setActivePreset(active)
        return QuickPickerModel(mgr)
    }

    // The render's frame-2/3 roster: a REORDERED set (Meeting transcripts before Terminal/General).
    private val frame23Roster = listOf(
        "Coding" to LBPresetPalette.CODING_HEX,
        "Email" to LBPresetPalette.EMAIL_HEX,
        "Personal" to LBPresetPalette.PERSONAL_HEX,
        "Meeting transcripts" to "#6366f1",
        "Terminal" to LBPresetPalette.TERMINAL_HEX,
        "General" to LBPresetPalette.GENERAL_HEX,
    )

    @Test fun `portrait cap 3 reproduces frame 2 visible split and overflow count`() {
        val s = model(frame23Roster, active = "Coding").build(QuickPickerModel.PORTRAIT_CAP)
        assertEquals(listOf("Email", "Personal", "Meeting transcripts"), s.pills.map { it.name })
        assertEquals(2, s.hiddenCount) // Terminal + General hidden → "… +2"
        assertEquals("Coding", s.floatHead?.name)
        assertFalse(s.isDegenerateNoOp)
    }

    @Test fun `wide cap 4 reproduces frame 3 visible split and overflow count`() {
        val s = model(frame23Roster, active = "Coding").build(QuickPickerModel.WIDE_CAP)
        assertEquals(listOf("Email", "Personal", "Meeting transcripts", "Terminal"), s.pills.map { it.name })
        assertEquals(1, s.hiddenCount) // only General hidden → "… +1"
    }

    @Test fun `the active preset is floated and excluded from the pill row`() {
        val s = model(frame23Roster, active = "Personal").build(QuickPickerModel.WIDE_CAP)
        assertEquals("Personal", s.floatHead?.name)
        assertFalse(s.pills.any { it.name == "Personal" })
    }

    @Test fun `nothing hidden yields a gear (hiddenCount 0) - frame 1`() {
        val roster = listOf(
            "Coding" to LBPresetPalette.CODING_HEX,
            "Email" to LBPresetPalette.EMAIL_HEX,
            "Personal" to LBPresetPalette.PERSONAL_HEX,
            "General" to LBPresetPalette.GENERAL_HEX,
        )
        val s = model(roster, active = "Coding").build(QuickPickerModel.PORTRAIT_CAP)
        assertEquals(listOf("Email", "Personal", "General"), s.pills.map { it.name })
        assertEquals(0, s.hiddenCount)
    }

    @Test fun `two presets degenerate to a one-pill row, not a no-op - frame 2b`() {
        val roster = listOf("Coding" to LBPresetPalette.CODING_HEX, "General" to LBPresetPalette.GENERAL_HEX)
        val s = model(roster, active = "Coding").build(QuickPickerModel.PORTRAIT_CAP)
        assertEquals(listOf("General"), s.pills.map { it.name })
        assertEquals(0, s.hiddenCount)
        assertFalse(s.isDegenerateNoOp)
    }

    @Test fun `one preset total is a no-op - the picker never opens`() {
        val roster = listOf("General" to LBPresetPalette.GENERAL_HEX)
        val s = model(roster, active = "General").build(QuickPickerModel.PORTRAIT_CAP)
        assertTrue(s.isDegenerateNoOp)
        assertTrue(s.pills.isEmpty())
    }

    @Test fun `pill dots resolve to the per-preset identity color and flag General`() {
        val s = model(frame23Roster, active = "Coding").build(QuickPickerModel.WIDE_CAP)
        val byName = s.pills.associateBy { it.name }
        assertEquals(Color.parseColor(LBPresetPalette.EMAIL_HEX), byName["Email"]?.dotColor)
        assertEquals(Color.parseColor(LBPresetPalette.TERMINAL_HEX), byName["Terminal"]?.dotColor)
        assertFalse(byName["Email"]!!.isGeneral)
        // make General visible to assert its flag
        val withGeneral = model(
            listOf("Coding" to LBPresetPalette.CODING_HEX, "General" to LBPresetPalette.GENERAL_HEX),
            active = "Coding",
        ).build(QuickPickerModel.PORTRAIT_CAP)
        assertTrue(withGeneral.pills.single { it.name == "General" }.isGeneral)
    }

    @Test fun `a malformed stored hex falls back to General cyan on the dot`() {
        val roster = listOf("Coding" to LBPresetPalette.CODING_HEX, "Bad" to "not-a-color")
        val s = model(roster, active = "Coding").build(QuickPickerModel.PORTRAIT_CAP)
        assertEquals(LBPresetPalette.GENERAL_CYAN, s.pills.single { it.name == "Bad" }.dotColor)
    }

    @Test fun `visibleCapFor maps posture to the render caps`() {
        val m = model(frame23Roster, active = "Coding")
        assertEquals(QuickPickerModel.PORTRAIT_CAP, m.visibleCapFor(wide = false))
        assertEquals(QuickPickerModel.WIDE_CAP, m.visibleCapFor(wide = true))
        assertEquals(3, QuickPickerModel.PORTRAIT_CAP)
        assertEquals(4, QuickPickerModel.WIDE_CAP)
    }

    @Test fun `label truncation flips exactly at the max-width boundary`() {
        val m = model(frame23Roster, active = "Coding")
        assertTrue(m.labelNeedsTruncation(measuredWidthPx = 119f, maxWidthPx = 118f))
        assertFalse(m.labelNeedsTruncation(measuredWidthPx = 118f, maxWidthPx = 118f))
        assertFalse(m.labelNeedsTruncation(measuredWidthPx = 117f, maxWidthPx = 118f))
    }
}
