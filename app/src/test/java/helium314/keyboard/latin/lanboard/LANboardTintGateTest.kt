/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the §6.2 v2.5 mic-tint crossfade gate ([LANboardBridge.shouldAnimateTint]): the crossfade is
 * one-shot on a REAL preset change only — cold-start opens (seeded == active) and same-preset re-applies
 * stay instant (no flash, no flicker), honoring §13.2's no-ambient-animation bar. Pure logic — no device.
 */
class LANboardTintGateTest {

    private val coding = 0xFF3B82F6.toInt()
    private val personal = 0xFFE0569F.toInt()

    @Test fun `same color never animates even when asked (cold-start no-flash, same-preset no-flicker)`() {
        assertFalse(LANboardBridge.shouldAnimateTint(animate = true, from = coding, to = coding))
    }

    @Test fun `a real color change animates when asked (manual switch or differing auto-apply)`() {
        assertTrue(LANboardBridge.shouldAnimateTint(animate = true, from = coding, to = personal))
    }

    @Test fun `the instant path never animates regardless of color`() {
        assertFalse(LANboardBridge.shouldAnimateTint(animate = false, from = coding, to = personal))
    }
}
