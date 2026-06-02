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
 * Locks the render-side contract of the per-preset mic ring (§6.2): [MicRingView.setActivePreset]
 * records the spike-tip color and whether the active preset is General (which selects the v1
 * single-gradient branch vs. the per-spike radial branch in drawListening). Drawing itself is
 * device visual-QA; this verifies the state the renderer branches on.
 */
@RunWith(RobolectricTestRunner::class)
class MicRingPresetColorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun view() = MicRingView(context)

    @Test fun `defaults to General cyan, General-preset branch`() {
        val v = view()
        assertEquals(LBPresetPalette.GENERAL_CYAN, v.presetColor)
        assertTrue(v.isGeneralPreset)
    }

    @Test fun `setActivePreset stores a non-General preset color and clears the General flag`() {
        val v = view()
        val coding = Color.parseColor(LBPresetPalette.CODING_HEX)
        v.setActivePreset(coding, isGeneral = false)
        assertEquals(coding, v.presetColor)
        assertFalse(v.isGeneralPreset)
    }

    @Test fun `setActivePreset keeps the General flag for the General preset`() {
        val v = view()
        v.setActivePreset(Color.parseColor(LBPresetPalette.CODING_HEX), isGeneral = false)
        v.setActivePreset(LBPresetPalette.GENERAL_CYAN, isGeneral = true)
        assertEquals(LBPresetPalette.GENERAL_CYAN, v.presetColor)
        assertTrue(v.isGeneralPreset)
    }
}
