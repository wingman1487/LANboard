/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R

/**
 * §5.6 quick-preset picker overlay view (v2.5 — authoritative render contract quick-picker-overlay.html).
 *
 * A purely decorative renderer + hit-tester. It is **non-touchable** (clickable/focusable false) and never
 * consumes events — the whole DOWN→MOVE→UP gesture is owned by the mic container's OnTouchListener, which
 * forwards raw coordinates into [hitTest]. The view draws, right-to-left within a single full-strip cyan-glass
 * scrim plate: the inert cyan "you are here" float (the row's right-most head, immediately left of the ring),
 * then the alternative pills fanning left in §8.1 order (nearest-the-ring-first), then the quiet "…/gear"
 * utility tile at the far-left. The plate's left edge is hard-inset past the ▸/toolbar-expand column so the
 * §7.4 amber badge there is never painted over, and its right edge stops short of the live mic ring.
 *
 * Geometry is supplied per-gesture in overlay-local pixels via [render]; nothing is cached at creation.
 * Motion (staggered open / retract / pulse) is layered on in a later step — this view renders the resting
 * frame and exposes the per-pill hit rectangles for the gesture layer.
 */
class QuickPickerView(context: Context) : View(context) {

    init {
        isClickable = false
        isFocusable = false
        // Soft shadows + glows are drawn via Paint.setShadowLayer, which needs a software layer.
        // The picker is only visible during a gesture and is otherwise static, so this is cheap.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // ---- geometry (overlay-local px), set by render() ----
    private var bandTop = 0f
    private var bandBottom = 0f
    private var plateLeft = 0f
    private var plateRight = 0f
    private var st: QuickPickerModel.PickerState? = null
    private var armedIndex = -1

    /** Laid-out element rects in overlay-local px (index-aligned with state.pills); reused for hit-testing. */
    private val pillRects = ArrayList<RectF>()
    private val floatRect = RectF()
    private val utilRect = RectF()
    private var hasLayout = false

    // ---- dp / sp ----
    private val dm = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density
    private fun d(id: Int) = resources.getDimension(id)

    // ---- tokens ----
    private val plateRadius = d(R.dimen.lb_qp_plate_radius)
    private val sidePad = d(R.dimen.lb_qp_plate_side_pad)
    private val pillH = d(R.dimen.lb_qp_pill_height)
    private val pillRadius = d(R.dimen.lb_qp_pill_radius)
    private val pillPad = d(R.dimen.lb_qp_pill_padding)
    private val pillMaxW = d(R.dimen.lb_qp_pill_max_width)
    private val pillGap = d(R.dimen.lb_qp_pill_gap)
    private val floatPad = d(R.dimen.lb_qp_float_padding)
    private val floatRadius = d(R.dimen.lb_qp_float_radius)
    private val dotSize = d(R.dimen.lb_qp_dot_size)
    private val utilMinW = d(R.dimen.lb_qp_util_min_width)
    private val countRadius = d(R.dimen.lb_qp_count_radius)
    private val pillTextSize = d(R.dimen.lb_qp_pill_text)
    private val countTextSize = d(R.dimen.lb_qp_count_text)

    // ---- colors (render :root → existing R.color where they map; literals otherwise) ----
    private val cyan = ContextCompat.getColor(context, R.color.lb_cyan)            // #00d4ff
    private val cyanSoft = ContextCompat.getColor(context, R.color.lb_cyan_soft)   // #4dd9ee
    private val textPrimary = ContextCompat.getColor(context, R.color.lb_text_primary)   // #e6edf3
    private val textMuted = ContextCompat.getColor(context, R.color.lb_text_tertiary)    // #6b7785
    private val floatInk = 0xFF04222B.toInt()

    // ---- paints ----
    private val plateFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val plateStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1f); color = (0x2E shl 24) or (0x00D4FF) // rgba(0,212,255,0.18)
    }
    private val lipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1f); color = (0x14 shl 24) or 0xFFFFFF // ~rgba(255,255,255,0.08)
    }
    private val pillFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1f); color = (0x1A shl 24) or 0xFFFFFF // rgba(255,255,255,0.10)
    }
    private val pillLip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1f); color = (0x2E shl 24) or 0xFFFFFF // rgba(255,255,255,0.18)
    }
    private val armedStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1.5f); color = cyan
    }
    private val dotFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(3f); color = (0x0A shl 24) or 0xFFFFFF // rgba(255,255,255,0.04)
    }
    private val floatFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan }
    private val utilFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val utilStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1f); color = (0x0F shl 24) or 0xFFFFFF // rgba(255,255,255,0.06)
    }
    private val countStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1f); color = (0x59 shl 24) or 0x00D4FF // rgba(0,212,255,0.35)
    }
    private val pillText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPrimary; textSize = pillTextSize; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val floatText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = floatInk; textSize = pillTextSize; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val utilGlyph = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textMuted; textSize = dp(14f); textAlign = Paint.Align.CENTER
    }
    private val countText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cyanSoft; textSize = countTextSize; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    /** Supply state + overlay-local geometry for the current gesture, then lay out and repaint. */
    fun render(
        state: QuickPickerModel.PickerState,
        bandTop: Float,
        bandBottom: Float,
        plateLeft: Float,
        plateRight: Float,
    ) {
        this.st = state
        this.bandTop = bandTop
        this.bandBottom = bandBottom
        this.plateLeft = plateLeft
        this.plateRight = plateRight
        this.armedIndex = -1
        layout()
        invalidate()
    }

    /** Set the armed pill index (or −1). */
    fun setArmed(index: Int) {
        if (index != armedIndex) {
            armedIndex = index
            invalidate()
        }
    }

    private val locBuf = IntArray(2)

    /**
     * Hit-test a forwarded raw window coordinate → the armed pill index, or −1. The hit region for each
     * pill is its width plus half the inter-pill gap on each side (so regions are contiguous — a sliding
     * finger always arms exactly one pill), at the full band height (the 30dp pill is only the visual).
     * The inert float and the util tile are not switch targets, so they never arm.
     */
    fun hitTest(rawX: Float, rawY: Float): Int {
        if (!hasLayout) return -1
        getLocationInWindow(locBuf)
        val lx = rawX - locBuf[0]
        val ly = rawY - locBuf[1]
        if (ly < bandTop || ly > bandBottom) return -1
        val halfGap = pillGap / 2f
        for (i in pillRects.indices) {
            val r = pillRects[i]
            if (lx >= r.left - halfGap && lx <= r.right + halfGap) return i
        }
        return -1
    }

    /** The name of the currently-armed pill, or null if none is armed (a no-change release). */
    fun armedPresetName(): String? = st?.pills?.getOrNull(armedIndex)?.name

    /** Lay out float → pills → util right-to-left, packed against the plate's right inset (flex-end). */
    private fun layout() {
        pillRects.clear()
        val state = st ?: run { hasLayout = false; return }
        val cy = (bandTop + bandBottom) / 2f
        val top = cy - pillH / 2f
        val bottom = cy + pillH / 2f
        val contentRight = plateRight - sidePad
        var x = contentRight

        // inert float (right-most head)
        state.floatHead?.let { head ->
            val w = floatPad * 2 + floatText.measureText(head.name)
            floatRect.set(x - w, top, x, bottom)
            x -= w + pillGap
        }
        // alternative pills, index 0 = nearest the ring (right), fanning left
        for (pill in state.pills) {
            val labelMax = pillMaxW - (pillPad * 2 + dotSize + pillGap)
            val labelW = minOf(pillText.measureText(pill.name), labelMax)
            val w = pillPad * 2 + dotSize + pillGap + labelW
            pillRects.add(RectF(x - w, top, x, bottom))
            x -= w + pillGap
        }
        // utility tile (far-left of the packed group)
        val utilW = utilWidth(state)
        utilRect.set(x - utilW, top, x, bottom)
        hasLayout = true
    }

    private fun utilWidth(state: QuickPickerModel.PickerState): Float {
        val pad = dp(7f)
        return if (state.hiddenCount > 0) {
            val glyphW = utilGlyph.measureText("⋯")
            val countW = dp(10f) + countText.measureText("+${state.hiddenCount}")
            maxOf(utilMinW, pad * 2 + glyphW + dp(5f) + countW)
        } else {
            maxOf(utilMinW, pad * 2 + utilGlyph.measureText("⚙"))
        }
    }

    override fun onDraw(canvas: Canvas) {
        val state = st ?: return
        if (!hasLayout) return

        drawPlate(canvas)
        drawUtil(canvas, state)
        for (i in state.pills.indices) drawPill(canvas, state.pills[i], pillRects[i], i == armedIndex)
        state.floatHead?.let { drawFloat(canvas, it) }
    }

    private fun drawPlate(canvas: Canvas) {
        val r = RectF(plateLeft, bandTop, plateRight, bandBottom)
        plateFill.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            (0xEB shl 24) or 0x10242C, (0xEB shl 24) or 0x0B181D, Shader.TileMode.CLAMP, // 92% alpha cyan-glass
        )
        plateFill.setShadowLayer(dp(24f), 0f, dp(10f), (0x99 shl 24)) // 0 10px 24px rgba(0,0,0,0.6)
        canvas.drawRoundRect(r, plateRadius, plateRadius, plateFill)
        plateFill.clearShadowLayer()
        plateFill.shader = null
        canvas.drawRoundRect(r, plateRadius, plateRadius, plateStroke)
        // inset top lip
        val inset = RectF(r.left + dp(1f), r.top + dp(1f), r.right - dp(1f), r.bottom - dp(1f))
        canvas.drawLine(inset.left + plateRadius, inset.top, inset.right - plateRadius, inset.top, lipPaint)
    }

    private fun drawPill(canvas: Canvas, pill: QuickPickerModel.Pill, rect: RectF, armed: Boolean) {
        val r = if (armed) {
            // armed: 1.03 scale + 1dp lift
            val cx = rect.centerX(); val cy = rect.centerY()
            RectF(
                cx - rect.width() / 2f * 1.03f, cy - rect.height() / 2f * 1.03f - dp(1f),
                cx + rect.width() / 2f * 1.03f, cy + rect.height() / 2f * 1.03f - dp(1f),
            )
        } else rect
        pillFill.shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xFF2B333D.toInt(), 0xFF181E25.toInt(), Shader.TileMode.CLAMP)
        pillFill.setShadowLayer(dp(if (armed) 8f else 6f), 0f, dp(if (armed) 3f else 2f), (0x66 shl 24)) // 0,0,0,0.4
        canvas.drawRoundRect(r, pillRadius, pillRadius, pillFill)
        pillFill.clearShadowLayer(); pillFill.shader = null
        canvas.drawRoundRect(r, pillRadius, pillRadius, if (armed) armedStroke else pillStroke)
        // top lip
        canvas.drawLine(r.left + pillRadius, r.top + dp(1f), r.right - pillRadius, r.top + dp(1f), pillLip)

        // identity dot (leading)
        val dotCx = r.left + pillPad + dotSize / 2f
        val dotCy = r.centerY()
        if (pill.isGeneral) {
            dotFill.setShadowLayer(dp(8f), 0f, 0f, (0x80 shl 24) or 0x00D4FF) // 0 0 8px rgba(0,212,255,.5)
        }
        dotFill.color = pill.dotColor
        canvas.drawCircle(dotCx, dotCy, dotSize / 2f, dotFill)
        dotFill.clearShadowLayer()
        if (!pill.isGeneral) canvas.drawCircle(dotCx, dotCy, dotSize / 2f + dp(1.5f), dotRing) // faint white ring

        // label, ellipsized to the available width
        val labelLeft = r.left + pillPad + dotSize + pillGap
        val labelMaxW = r.right - pillPad - labelLeft
        val label = TextUtils.ellipsize(pill.name, pillText, labelMaxW, TextUtils.TruncateAt.END)
        val baseline = r.centerY() - (pillText.descent() + pillText.ascent()) / 2f
        canvas.drawText(label, 0, label.length, labelLeft, baseline, pillText)
    }

    private fun drawFloat(canvas: Canvas, head: QuickPickerModel.FloatHead) {
        floatFill.setShadowLayer(dp(10f), 0f, 0f, (0x99 shl 24) or 0x00D4FF) // 0 0 10px rgba(0,212,255,0.6) glow
        canvas.drawRoundRect(floatRect, floatRadius, floatRadius, floatFill)
        floatFill.clearShadowLayer()
        val baseline = floatRect.centerY() - (floatText.descent() + floatText.ascent()) / 2f
        canvas.drawText(head.name, floatRect.left + floatPad, baseline, floatText)
    }

    private fun drawUtil(canvas: Canvas, state: QuickPickerModel.PickerState) {
        utilFill.shader = LinearGradient(0f, utilRect.top, 0f, utilRect.bottom, 0xFF222A33.toInt(), 0xFF161C23.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(utilRect, pillRadius, pillRadius, utilFill)
        utilFill.shader = null
        canvas.drawRoundRect(utilRect, pillRadius, pillRadius, utilStroke)
        if (state.hiddenCount > 0) {
            val pad = dp(7f)
            val glyphX = utilRect.left + pad + utilGlyph.measureText("⋯") / 2f
            val baseline = utilRect.centerY() - (utilGlyph.descent() + utilGlyph.ascent()) / 2f
            canvas.drawText("⋯", glyphX, baseline, utilGlyph)
            // "+N" count chip
            val chipText = "+${state.hiddenCount}"
            val chipW = countText.measureText(chipText) + dp(10f)
            val chipR = RectF(utilRect.right - pad - chipW, utilRect.centerY() - dp(8f), utilRect.right - pad, utilRect.centerY() + dp(8f))
            canvas.drawRoundRect(chipR, countRadius, countRadius, countStroke)
            val cb = chipR.centerY() - (countText.descent() + countText.ascent()) / 2f
            canvas.drawText(chipText, chipR.left + dp(5f), cb, countText)
        } else {
            val baseline = utilRect.centerY() - (utilGlyph.descent() + utilGlyph.ascent()) / 2f
            canvas.drawText("⚙", utilRect.centerX(), baseline, utilGlyph)
        }
    }
}
