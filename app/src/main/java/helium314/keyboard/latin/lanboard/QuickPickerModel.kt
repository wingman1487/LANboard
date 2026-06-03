/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

/**
 * Pure data layer for the §5.6 quick-preset picker (v2.5 — render contract quick-picker-overlay.html).
 *
 * Builds the picker's content — the inert active-preset float head and the ordered alternative pills —
 * straight from [VoicePresetManager]'s persisted global order (§8.1). That single order IS the fill
 * priority: index 0 is the alternative nearest the ring (it sits immediately left of the float), filling
 * leftward until the per-posture visible cap; everything past the cap collapses into the "…/+N" overflow
 * tile. Membership is never special-cased — the picker shows the user's persisted order verbatim, so the
 * render's specific roster simply reflects a reordered set, not any hard-coded selection.
 *
 * Deliberately Android-free (no View/Canvas/Paint) so ordering, active-exclusion, cap/overflow and
 * degeneracy are unit-testable; all rendering, label width-fitting and hit-testing live in
 * QuickPickerView and are validated by device render-match.
 */
class QuickPickerModel(private val presetManager: VoicePresetManager) {

    /**
     * The active preset, lifted out of the ring as the inert cyan "you are here" float (§5.6 L191).
     * The float fill is ALWAYS General cyan regardless of the active preset's identity — identity rides
     * the mic-icon tint (§6.2) — so only the name is carried here.
     */
    data class FloatHead(val name: String)

    /**
     * One switchable alternative pill: its name plus its §6.2 identity-dot color. [isGeneral] drives the
     * General-only cyan-glow dot exception (every other dot is a solid hex disc with a faint white ring).
     */
    data class Pill(val name: String, val dotColor: Int, val isGeneral: Boolean)

    data class PickerState(
        val floatHead: FloatHead?,
        val pills: List<Pill>,
        /** Alternatives beyond the visible cap: 0 → plain gear tile, >0 → "…" + "+N" chip (§5.6 L194). */
        val hiddenCount: Int,
        /** ≤ 1 preset total → the idle long-press is a no-op and the picker never opens (§5.6 L188/L214). */
        val isDegenerateNoOp: Boolean,
    )

    /**
     * Visible-alternative ceiling per posture, matching the render frames: portrait fits 3 (frames 1–2),
     * a wide strip — unfolded, split, or folded-landscape — fits the spec's ~4 hard cap (frames 3–4).
     * A true ceiling: extra presets always overflow into "+N", they never grow the row past this.
     */
    fun visibleCapFor(wide: Boolean): Int = if (wide) WIDE_CAP else PORTRAIT_CAP

    /** Build the picker state for a posture (portrait vs wide). */
    fun build(wide: Boolean): PickerState = build(visibleCapFor(wide))

    /** Build with an explicit visible-alternative cap. This is the pure unit-test seam. */
    fun build(visibleCap: Int): PickerState {
        val presets = presetManager.getPresets()                   // persisted §8.1 order; index 0 = nearest ring
        val active = presetManager.getActivePreset()
        val alternatives = presets.filter { it.name != active?.name }   // active excluded, order preserved
        val visible = alternatives.take(visibleCap.coerceAtLeast(0))
        val hiddenCount = (alternatives.size - visible.size).coerceAtLeast(0)
        return PickerState(
            floatHead = active?.let { FloatHead(it.name) },
            pills = visible.map { Pill(it.name, it.colorInt(), it.isGeneral()) },
            hiddenCount = hiddenCount,
            isDegenerateNoOp = presets.size <= 1,
        )
    }

    /**
     * Tail-ellipsis decision for a pill label, given a measured text width and the max-width cap
     * (118dp in the render). Kept pure — the actual glyph measurement is the View's Paint job — so the
     * boundary is unit-testable.
     */
    fun labelNeedsTruncation(measuredWidthPx: Float, maxWidthPx: Float): Boolean =
        measuredWidthPx > maxWidthPx

    companion object {
        /** Portrait visible-alternative cap (render frames 1–2). */
        const val PORTRAIT_CAP = 3
        /** Wide-strip visible-alternative cap — the spec's "~4" hard ceiling (render frames 3–4). */
        const val WIDE_CAP = 4
    }
}
