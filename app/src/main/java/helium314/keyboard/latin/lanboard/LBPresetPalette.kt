/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

import android.graphics.Color

/**
 * Single source of truth for LANboard preset colors (§6.2 / §8.1, Q2 palette — locked v2.3 Q6).
 *
 * Consumed by VoicePresetManager (storage defaults + migration), the mic-ring renderer (icon tint
 * + listening spike-tip color), the quick-preset picker, and the Manage Presets UI — they must all
 * read their colors from here so the identity stays consistent.
 *
 * Only the **five named defaults** below are locked by the spec. The broader curated swatch grid
 * shown in the Manage Presets edit screen (8-wide, hue-separated, blocking the reserved status hues
 * + cyan base + purple per §6.2/§13.2) is owned by the preset-management-ui subsystem and is
 * intentionally NOT invented here — it will be authored against that screen's approved design.
 */
object LBPresetPalette {

    // Locked default preset colors (§6.2 / §8.1). Cyan is the signature and the SOLE owner of cyan.
    const val CODING_HEX = "#3b82f6"   // blue
    const val EMAIL_HEX = "#14b8a6"    // teal
    const val PERSONAL_HEX = "#e0569f" // magenta
    const val TERMINAL_HEX = "#8090a8" // muted slate (distinct from the cyan terminal *row*)
    const val GENERAL_HEX = "#00d4ff"  // cyan — the signature baseline, reserved for General

    /** General cyan, the universal fallback when a stored/parsed color is missing or malformed. */
    const val GENERAL_CYAN_HEX = GENERAL_HEX
    val GENERAL_CYAN: Int = Color.parseColor(GENERAL_HEX)

    /** Locked name → default color, used to seed defaults and to migrate color-less stored presets. */
    private val LOCKED_DEFAULTS: Map<String, String> = mapOf(
        "Coding" to CODING_HEX,
        "Email" to EMAIL_HEX,
        "Personal" to PERSONAL_HEX,
        "Terminal" to TERMINAL_HEX,
        "General" to GENERAL_HEX,
    )

    /**
     * Reserved status / signature hues that the curated user palette must never offer (§6.2/§13.2):
     * green-pulse, amber, red are server-state channels; cyan is General-only; purple is banned.
     * Kept here so the palette UI and any validation share one exclusion list.
     */
    val RESERVED_STATUS_HEXES = listOf("#3ddc97", "#f0b429", "#ef4444") // green-pulse, amber, red
    const val RESERVED_CYAN_HEX = GENERAL_HEX // shown but assignable only to General

    /** The locked default color hex for a preset name, or General cyan for any other/new preset. */
    fun defaultHexForName(name: String): String =
        LOCKED_DEFAULTS[name] ?: GENERAL_CYAN_HEX

    /** Parse a stored hex to an ARGB int, falling back to General cyan so a bad value never crashes. */
    fun parseOrDefault(hex: String?): Int =
        try {
            if (hex.isNullOrBlank()) GENERAL_CYAN else Color.parseColor(hex)
        } catch (_: IllegalArgumentException) {
            GENERAL_CYAN
        }
}
