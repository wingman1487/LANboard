/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import helium314.keyboard.latin.suggestions.SuggestionStripView

/**
 * Hosts and drives the §5.6 quick-picker overlay (v2.5). Attaches a [QuickPickerView] as a z-stacked
 * child of the strip-row overlay FrameLayout (R.id.lb_strip_overlay_host), so the picker draws in the
 * strip row's own coordinate space, on top of [SuggestionStripView] (hence the §7.4 amber badge is
 * protected by the plate's left-inset, not z-order).
 *
 * The view is non-touchable; the whole gesture is owned by the mic container's OnTouchListener in
 * [LANboardBridge], which calls [open]/[onHover]/[resolveOnUp]/[abort]. Geometry (plate left past the ▸
 * column, right short of the live mic ring, band top/bottom) is computed per-gesture from on-screen rects
 * — never cached — so it tracks posture and the §7.4 banner-aware band.
 */
class QuickPickerController(
    private val overlayHost: FrameLayout,
    private val stripView: SuggestionStripView,
    private val micContainer: View,
    presetManager: VoicePresetManager,
    private val onResolve: (String) -> Unit,
) {
    private val model = QuickPickerModel(presetManager)
    private val picker = QuickPickerView(overlayHost.context).also {
        overlayHost.addView(
            it,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        it.visibility = View.GONE
    }
    private val dm = overlayHost.resources.displayMetrics
    private fun dp(v: Float) = v * dm.density

    private var isOpen = false

    /**
     * Open the picker for the current posture if there are ≥2 presets. Returns false (a no-op) at ≤1
     * preset (§5.6 L188/L214) or if geometry is not yet valid. Computes plate bounds from the live ▸-key
     * right edge and mic-ring left edge so the badge stays clear and the plate stops short of the ring.
     */
    fun open(): Boolean {
        if (overlayHost.width == 0 || overlayHost.height == 0) return false
        val wide = overlayHost.resources.configuration.screenWidthDp >= WIDE_THRESHOLD_DP
        val state = model.build(wide)
        if (state.isDegenerateNoOp) return false

        val loc = IntArray(2)
        overlayHost.getLocationInWindow(loc); val hostLeft = loc[0]
        micContainer.getLocationInWindow(loc); val ringLeftLocal = (loc[0] - hostLeft).toFloat()
        val toolbarRightLocal = (stripView.toolbarExpandKeyRightInWindow() - hostLeft).toFloat()

        val plateLeft = maxOf(dp(PLATE_INSET_LEFT_DP), toolbarRightLocal)
        val plateRight = ringLeftLocal - dp(PLATE_RING_GAP_DP)
        val bandTop = dp(PLATE_INSET_TOP_DP)
        val bandBottom = overlayHost.height - dp(PLATE_INSET_BOTTOM_DP)
        if (plateRight <= plateLeft + dp(MIN_PLATE_WIDTH_DP)) return false // no room → don't open a sliver

        picker.render(state, bandTop, bandBottom, plateLeft, plateRight)
        picker.visibility = View.VISIBLE
        picker.alpha = 0f
        picker.animate().alpha(1f).setDuration(OPEN_FADE_MS).start()
        isOpen = true
        return true
    }

    /** Forward the moving finger (raw window coords) so the view arms the pill under it. */
    fun onHover(rawX: Float, rawY: Float) {
        if (!isOpen) return
        picker.setArmed(picker.hitTest(rawX, rawY))
    }

    /**
     * Finger lifted: resolve to the armed pill (if any), retract, and return the chosen preset name — or
     * null for a no-change release (empty space, the float, the ring, or an immediate release).
     */
    fun resolveOnUp(): String? {
        val name = picker.armedPresetName()
        close()
        return name
    }

    /** Abort with no commit (posture change / cancel). */
    fun abort() = close()

    /** Retract the picker. */
    fun close() {
        if (!isOpen && picker.visibility != View.VISIBLE) return
        isOpen = false
        picker.animate().cancel()
        picker.alpha = 1f
        picker.visibility = View.GONE
    }

    /** Session ended (input view finished) — tear down any open picker. */
    fun clearSession() = close()

    companion object {
        /** Wide-strip threshold (unfolded inner display / landscape) — refined in Step 7. */
        private const val WIDE_THRESHOLD_DP = 520
        private const val PLATE_INSET_LEFT_DP = 6f
        private const val PLATE_INSET_TOP_DP = 5f
        private const val PLATE_INSET_BOTTOM_DP = 5f
        /** Gap between the plate's right edge and the live mic-ring container's left edge. */
        private const val PLATE_RING_GAP_DP = 6f
        private const val MIN_PLATE_WIDTH_DP = 60f
        private const val OPEN_FADE_MS = 140L
    }
}
