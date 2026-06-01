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
    private val inset = dp(2.5f)           // gap between panes (CSS row gap ~5px)
    private val radius = dp(9f)            // .gk border-radius: 9px
    private val topEdge = dp(1.5f)         // inner top highlight (border-top + inset 0 1px 0)
    private val bottomEdge = dp(2f)        // .gk border-bottom: 2px solid rgba(0,0,0,.5)
    private val sideEdge = dp(1f)          // .gk border 1px rgba(255,255,255,.10)
    private val liftRadius = dp(6f)        // box-shadow 0 3px 6px
    private val liftDy = dp(3f)
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
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
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
        // 5. inner top highlight edge + side edges + bottom thickness edge
        edgePaint.color = if (face == Face.CYAN) TOP_EDGE_CY else TOP_EDGE
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + topEdge, edgePaint)
        edgePaint.color = SIDE_EDGE
        canvas.drawRect(rect.left, rect.top, rect.left + sideEdge, rect.bottom, edgePaint)
        canvas.drawRect(rect.right - sideEdge, rect.top, rect.right, rect.bottom, edgePaint)
        edgePaint.color = BOTTOM_EDGE
        canvas.drawRect(rect.left, rect.bottom - bottomEdge, rect.right, rect.bottom, edgePaint)

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
        private val LIFT = argb(0.50f, 0, 0, 0)             // 0 3px 6px rgba(0,0,0,0.5)
        private val TOP_EDGE = argb(0.30f, 255, 255, 255)   // inset 0 1px 0 rgba(255,255,255,0.30)
        private val TOP_EDGE_CY = argb(0.40f, 0, 212, 255)  // cyan inset top rgba(0,212,255,0.4)
        private val SIDE_EDGE = argb(0.10f, 255, 255, 255)  // border 1px rgba(255,255,255,0.10)
        private val BOTTOM_EDGE = argb(0.50f, 0, 0, 0)      // border-bottom 2px rgba(0,0,0,0.5)
        private val PRESS_POP = argb(0.13f, 255, 255, 255)  // ≈ brightness(1.15)
        private val GLINT = argb(0.40f, 255, 255, 255)      // rgba(255,255,255,0.40)

        private fun argb(a: Float, r: Int, g: Int, b: Int) = Color.argb((a * 255).toInt(), r, g, b)
    }
}
