/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode

/**
 * LANboard faux-glass key renderer (§6.7).
 *
 * Faithfully reproduces the approved authoritative visual reference `Q6-glass-keyboard.html`.
 * Each key is a static raised pane of glass — a convex vertical face gradient, a domed specular
 * cap across the top, a bright inner top edge, a dark inner bottom shade + thickness edge (the
 * side-wall), and a soft outer lift shadow. The depth comes from layering all of these; the grid
 * is entirely static and repaints only on press. The one animated element is a one-shot diagonal
 * press glint on key-down (~[GLINT_MS] ms), then gone — no ambient/looping sheen (§13.2).
 *
 * Color logic (§6.7): neutral glass = character keys (letters, '.', space); cyan glass =
 * command/modifier keys (shift, backspace, ?123, enter); the globe is a neutral glass face whose
 * icon the engine tints muted. The spacebar uses a flattened wide-key sheen (a convex cap stretched
 * across a wide key bulges into a tube look).
 *
 * Runs only when [helium314.keyboard.latin.common.Colors.glassKeys] is true (the authored LANboard
 * Dark colorset); every other theme renders through the engine's flat drawables. One instance per
 * [KeyboardView], so the glint timestamps and the Paint/Shader cache are scoped to that view.
 *
 * Values mirror the CSS in Q6-glass-keyboard.html (authored on a 46px-tall key), noted inline.
 */
class GlassKeyRenderer(private val density: Float) {

    private enum class Face { NEUTRAL, CYAN }

    private fun dp(v: Float) = v * density

    // fixed geometry (dp); cap height / inner-shade depth scale with key height (CSS uses %)
    // keep the inset minimal — the engine already spaces keys; extra inset shrinks the glass pane
    // and reads as too much gap (big-thumb feedback). Just enough to keep panes from touching.
    private val inset = dp(0.5f)
    private val radius = dp(9f)            // .gk border-radius: 9px
    private val topEdge = dp(1.5f)         // inner top highlight (border-top + inset 0 1px 0)
    private val bottomRim = dp(2f)         // lit bottom edge so the key doesn't melt into the bg
    private val borderWidth = dp(1f)       // .gk border 1px (cyan on command keys — what makes glass "peel")
    private val liftRadius = dp(8f)        // stronger drop shadow so keys lift off the bg (not "sitting")
    private val liftDy = dp(4f)
    private val pressShift = dp(2f)        // .pressed transform: translateY(2px)

    private val glintStart = HashMap<Key, Long>()

    private val cache = object : LinkedHashMap<Long, Glass>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Glass>?) = size > 96
    }

    /** all cached, coordinate-bound paint layers for one (size, face); reused across like keys */
    private class Glass(
        val face: Paint, val cap: Paint, val capPath: Path,
        val bottomShade: Paint, val bottomRect: RectF, val rect: RectF,
    )

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val borderRect = RectF()
    private val glintPath = Path()

    fun drawKeyBackground(canvas: Canvas, view: KeyboardView, key: Key, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val face = faceOf(key)
        val pressed = key.isPressed

        canvas.save()
        if (pressed) canvas.translate(0f, pressShift)

        val g = cache.getOrPut(cacheKey(width, height, face)) { buildGlass(width, height, face) }
        val rect = g.rect

        // 1. outer lift shadow + 2. convex face fill (skip the heavy lift while depressed)
        g.face.clearShadowLayer()
        if (!pressed) g.face.setShadowLayer(liftRadius, 0f, liftDy, LIFT)
        canvas.drawRoundRect(rect, radius, radius, g.face)

        // everything else is clipped to the rounded face
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // 3. inner bottom shade — the convex "side-wall" darkening (inset 0 -Npx ...)
        canvas.drawRect(g.bottomRect, g.bottomShade)
        // 4. domed specular cap across the top (::before)
        canvas.drawPath(g.capPath, g.cap)
        // 5. crisp 1px border outline (.gk border) — cyan on command keys, faint white on neutral;
        //    this edge is what makes the panes read as raised glass ("peeling"). Inset by half the
        //    stroke so the full 1px sits inside the rounded face.
        borderPaint.strokeWidth = borderWidth
        borderPaint.color = if (face == Face.CYAN) BORDER_CY else BORDER
        val h = borderWidth / 2f
        borderRect.set(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h)
        canvas.drawRoundRect(borderRect, radius, radius, borderPaint)
        // brighter top edge (border-top-color)
        edgePaint.color = if (face == Face.CYAN) TOP_EDGE_CY else TOP_EDGE
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + topEdge, edgePaint)
        // lit bottom rim — a defined glass edge so the bottom doesn't melt into the dark bg.
        // bright cyan on command keys, soft light on neutral keys (big-thumb visibility feedback).
        edgePaint.color = if (face == Face.CYAN) BOTTOM_RIM_CY else BOTTOM_RIM
        canvas.drawRect(rect.left, rect.bottom - bottomRim, rect.right, rect.bottom, edgePaint)

        // 6. pressed brightness pop (filter: brightness(1.15))
        if (pressed) {
            pressPaint.color = PRESS_POP
            canvas.drawRect(rect, pressPaint)
        }

        // 7. one-shot skewed press glint
        drawGlint(canvas, view, key, rect, pressed)

        canvas.restore() // clip
        canvas.restore() // press translate
    }

    /** skewed white band sweeping left→right once over [GLINT_MS] (.gk::after + @keyframes glint) */
    private fun drawGlint(canvas: Canvas, view: KeyboardView, key: Key, rect: RectF, pressed: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (pressed && key !in glintStart) glintStart[key] = now
        val start = glintStart[key] ?: return
        val elapsed = now - start
        if (elapsed >= GLINT_MS) { glintStart.remove(key); return }

        val p = elapsed / GLINT_MS.toFloat()
        val w = rect.width()
        val bandW = w * 0.55f                       // ::after width:55%
        val left = rect.left + (-0.60f + p * 1.95f) * w   // left travels -60% -> 135%
        val skew = w * 0.18f                          // skewX(-18deg) ≈ horizontal shear
        glintPath.reset()
        glintPath.moveTo(left + skew, rect.top)
        glintPath.lineTo(left + skew + bandW, rect.top)
        glintPath.lineTo(left + bandW, rect.bottom)
        glintPath.lineTo(left, rect.bottom)
        glintPath.close()
        glintPaint.shader = LinearGradient(
            left, 0f, left + bandW, 0f,
            intArrayOf(Color.TRANSPARENT, GLINT, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawPath(glintPath, glintPaint)
        glintPaint.shader = null
        view.postOnAnimation { view.invalidateKey(key) }
    }

    private fun buildGlass(width: Int, height: Int, face: Face): Glass {
        val w = width.toFloat(); val h = height.toFloat()
        val rect = RectF(inset, inset, w - inset, h - inset)
        val rh = rect.height(); val rw = rect.width()
        val isCyan = face == Face.CYAN

        // convex face: linear-gradient(180deg, top, mid 46%, bottom)
        val face0: Int; val face1: Int; val face2: Int
        if (isCyan) { face0 = 0xff1d4150.toInt(); face1 = 0xff143039.toInt(); face2 = 0xff101a21.toInt() }
        else        { face0 = 0xff39434f.toInt(); face1 = 0xff232b34.toInt(); face2 = 0xff161c23.toInt() }
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        facePaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            intArrayOf(face0, face1, face2), floatArrayOf(0f, 0.46f, 1f), Shader.TileMode.CLAMP
        )

        // inner bottom shade: inset 0 -5px 9px rgba(0,0,0,0.4) → dark gradient over the bottom band
        val shadeTop = rect.bottom - rh * 0.32f
        val bottomRect = RectF(rect.left, shadeTop, rect.right, rect.bottom)
        val bottomShade = Paint(Paint.ANTI_ALIAS_FLAG)
        bottomShade.shader = LinearGradient(
            0f, shadeTop, 0f, rect.bottom,
            intArrayOf(Color.TRANSPARENT, argb(0.40f, 0, 0, 0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )

        // domed specular cap (::before): inset from sides, height ~42% (34% flatter for spacebar),
        // rounded-bottom dome; white→transparent (cyan-white for cyan faces)
        val wide = isWide(width, height)
        val capInsetX = if (wide) rw * 0.02f else rw * 0.07f
        val capH = if (wide) rh * 0.34f else rh * 0.42f
        val capRect = RectF(rect.left + capInsetX, rect.top + topEdge, rect.right - capInsetX, rect.top + topEdge + capH)
        val topR = dp(8f)
        val botR = if (wide) dp(7f) else capRect.height() * 0.6f // large bottom radius = dome
        val capPath = Path()
        capPath.addRoundRect(
            capRect,
            floatArrayOf(topR, topR, topR, topR, botR, botR, botR, botR),
            Path.Direction.CW
        )
        val capTop = when {
            isCyan -> argb(0.28f, 180, 238, 255)
            wide -> argb(0.16f, 255, 255, 255)
            else -> argb(0.24f, 255, 255, 255)
        }
        val capPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        capPaint.shader = LinearGradient(
            0f, capRect.top, 0f, capRect.bottom,
            intArrayOf(capTop, capTop and 0x00ffffff), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        return Glass(facePaint, capPaint, capPath, bottomShade, bottomRect, rect)
    }

    private fun faceOf(key: Key): Face = when {
        // globe (language switch) is a NEUTRAL glass face — only its icon is tinted muted by the engine
        key.code == KeyCode.LANGUAGE_SWITCH -> Face.NEUTRAL
        key.backgroundType == Key.BACKGROUND_TYPE_FUNCTIONAL ||
            key.backgroundType == Key.BACKGROUND_TYPE_ACTION -> Face.CYAN
        else -> Face.NEUTRAL // NORMAL letters/'.', SPACEBAR
    }

    /** treat clearly-wider-than-tall keys (spacebar) as wide for the flattened sheen */
    private fun isWide(width: Int, height: Int) = width > height * 2.4f

    private fun cacheKey(w: Int, h: Int, face: Face): Long =
        (w.toLong() shl 34) or (h.toLong() shl 4) or face.ordinal.toLong()

    fun reset() { glintStart.clear() }

    companion object {
        private const val GLINT_MS = 420L
        private val LIFT = argb(0.60f, 0, 0, 0)             // drop shadow under the key
        private val TOP_EDGE = argb(0.30f, 255, 255, 255)   // inset 0 1px 0 rgba(255,255,255,0.30)
        private val TOP_EDGE_CY = argb(0.50f, 0, 212, 255)  // cyan border-top-color rgba(0,212,255,0.5)
        private val BORDER = argb(0.12f, 255, 255, 255)     // neutral border rgba(255,255,255,0.12)
        private val BORDER_CY = argb(0.30f, 0, 212, 255)    // cyan key border — the glassy edge
        private val BOTTOM_RIM = argb(0.22f, 255, 255, 255) // lit bottom rim on neutral keys
        private val BOTTOM_RIM_CY = argb(0.55f, 0, 212, 255)// bright cyan bottom rim on command keys
        private val PRESS_POP = argb(0.13f, 255, 255, 255)  // ≈ brightness(1.15)
        private val GLINT = argb(0.40f, 255, 255, 255)      // rgba(255,255,255,0.40)

        private fun argb(a: Float, r: Int, g: Int, b: Int) = Color.argb((a * 255).toInt(), r, g, b)

        /**
         * Paint the tap-preview popup as an EXACT copy of a seated/more-keys NEUTRAL glass key, so it
         * mirrors the more-keys popup keys (the look the owner approved) — same face gradient, domed
         * cap, edges, border, bottom rim, and lift shadow as the instance renderer's neutral path
         * (no press depress, no glint). Values here are kept in lockstep with the instance geometry
         * (inset/radius/edges) and the neutral face/cap/edge constants. Paints are built per-call.
         */
        @JvmStatic
        fun drawPreviewTile(canvas: Canvas, width: Int, height: Int, density: Float) {
            if (width <= 0 || height <= 0) return
            fun d(v: Float) = v * density
            val inset = d(0.5f); val radius = d(9f)
            val rect = RectF(inset, inset, width - inset, height - inset)
            val rw = rect.width(); val rh = rect.height()

            // lift shadow + neutral convex face (same as a seated neutral key)
            val face = Paint(Paint.ANTI_ALIAS_FLAG)
            face.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(0xff39434f.toInt(), 0xff232b34.toInt(), 0xff161c23.toInt()),
                floatArrayOf(0f, 0.46f, 1f), Shader.TileMode.CLAMP
            )
            face.setShadowLayer(d(8f), 0f, d(4f), LIFT)
            canvas.drawRoundRect(rect, radius, radius, face)

            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
            // inner bottom shade (side-wall darkening)
            val shadeTop = rect.bottom - rh * 0.32f
            val shade = Paint(Paint.ANTI_ALIAS_FLAG)
            shade.shader = LinearGradient(0f, shadeTop, 0f, rect.bottom,
                intArrayOf(Color.TRANSPARENT, argb(0.40f, 0, 0, 0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(rect.left, shadeTop, rect.right, rect.bottom, shade)
            // domed specular cap: inset 7%, height 42%, white 0.24
            val capH = rh * 0.42f
            val capRect = RectF(rect.left + rw * 0.07f, rect.top + d(1.5f), rect.right - rw * 0.07f, rect.top + d(1.5f) + capH)
            val capTop = argb(0.24f, 255, 255, 255)
            val cap = Paint(Paint.ANTI_ALIAS_FLAG)
            cap.shader = LinearGradient(0f, capRect.top, 0f, capRect.bottom,
                intArrayOf(capTop, capTop and 0x00ffffff), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            val tr = d(8f); val br = capH * 0.6f
            canvas.drawPath(Path().apply {
                addRoundRect(capRect, floatArrayOf(tr, tr, tr, tr, br, br, br, br), Path.Direction.CW)
            }, cap)
            // bright top edge + soft bottom rim (neutral)
            val e = Paint(Paint.ANTI_ALIAS_FLAG)
            e.color = TOP_EDGE
            canvas.drawRect(rect.left, rect.top, rect.right, rect.top + d(1.5f), e)
            e.color = BOTTOM_RIM
            canvas.drawRect(rect.left, rect.bottom - d(2f), rect.right, rect.bottom, e)
            canvas.restore()

            // 1px neutral border outline
            val b = Paint(Paint.ANTI_ALIAS_FLAG)
            b.style = Paint.Style.STROKE; b.strokeWidth = d(1f); b.color = BORDER
            val h = d(0.5f)
            canvas.drawRoundRect(RectF(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h), radius, radius, b)
        }
    }
}
