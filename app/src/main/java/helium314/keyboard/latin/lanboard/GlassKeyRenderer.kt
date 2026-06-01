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
 * Paints each key as a static raised pane of glass — a convex vertical gradient face, a specular
 * cap across the top third, a bright top edge, a dark bottom "thickness" edge, and a soft lift
 * shadow — built entirely from static drawing so the grid repaints only on press (no ambient or
 * looping sheen, which §13.2 bans). The single exception is a one-shot diagonal press glint on
 * key-down that runs ~[GLINT_MS] ms and then stops.
 *
 * Character keys read as neutral glass; command/modifier keys read as cyan glass; the spacebar
 * uses a flattened sheen (a wide convex cap bulges and looks tube-like); the globe is a muted
 * utility tile. Exact colors are the §6.1 design tokens.
 *
 * This renderer is the consumer of the LANboard Dark colorset, not a fork of the theme engine:
 * it only runs when [helium314.keyboard.latin.common.Colors.glassKeys] is true (set solely by the
 * authored LANboard Dark theme); every other theme renders through the engine's flat drawables.
 *
 * One instance is owned per [KeyboardView], so the per-key glint timestamps and the Paint/Shader
 * cache are scoped to that view and torn down with it.
 */
class GlassKeyRenderer(density: Float) {

    private enum class Face { NEUTRAL, CYAN, SPACEBAR, GLOBE }

    // Density-scaled geometry (computed once per view).
    private val inset = 1.5f * density           // gap so panes read as separate tiles
    private val cornerRadius = 9f * density       // matches the Rounded style's rounded corners
    private val edgeThickness = 1.5f * density
    private val bottomEdgeThickness = 2f * density
    private val shadowRadius = 6f * density
    private val shadowDy = 3f * density
    private val pressShift = 1f * density          // depress translate-down

    /** start time (uptimeMillis) of the active press glint per key; absent once the sweep ends */
    private val glintStart = HashMap<Key, Long>()

    /** cache of face+cap Paints keyed by size+face; shaders live in 0..h local coords reused per key */
    private val cache = object : LinkedHashMap<Long, Glass>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Glass>?) = size > 96
    }

    private class Glass(val face: Paint, val cap: Paint, val capRect: RectF)

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()

    /**
     * Draw the glass background for [key] into [canvas], which onDrawKeyBackground has already
     * translated to the key's draw origin. [view] is used only to schedule the bounded glint frames.
     */
    fun drawKeyBackground(canvas: Canvas, view: KeyboardView, key: Key, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val face = faceOf(key)
        val pressed = key.isPressed

        canvas.save()
        if (pressed) canvas.translate(0f, pressShift)

        val rect = RectF(inset, inset, width - inset, height - inset)
        val glass = cache.getOrPut(cacheKey(width, height, face)) { buildGlass(rect, face) }

        // 1. lift shadow + 2. convex face
        glass.face.clearShadowLayer()
        if (!pressed) glass.face.setShadowLayer(shadowRadius, 0f, shadowDy, SHADOW)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, glass.face)

        // clip to the rounded face for the cap / edges / glint so nothing bleeds past the corners
        clipPath.reset()
        clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // 3. specular cap across the top third (flattened for the spacebar)
        canvas.drawRect(glass.capRect, glass.cap)

        // 4. bright top edge + 5. dark bottom thickness edge
        edgePaint.style = Paint.Style.FILL
        edgePaint.color = topEdgeColor(face)
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + edgeThickness, edgePaint)
        edgePaint.color = BOTTOM_EDGE
        canvas.drawRect(rect.left, rect.bottom - bottomEdgeThickness, rect.right, rect.bottom, edgePaint)

        // 6. pressed brightness pop
        if (pressed) {
            pressPaint.color = PRESS_POP
            canvas.drawRect(rect, pressPaint)
        }

        // 7. one-shot press glint
        drawGlint(canvas, view, key, rect, pressed)

        canvas.restore() // clip
        canvas.restore() // press translate
    }

    private fun drawGlint(canvas: Canvas, view: KeyboardView, key: Key, rect: RectF, pressed: Boolean) {
        val now = SystemClock.uptimeMillis()
        // arm on a fresh key-down; keep the entry until the sweep expires so it doesn't refire while held
        if (pressed && key !in glintStart) glintStart[key] = now
        val start = glintStart[key] ?: return
        val elapsed = now - start
        if (elapsed >= GLINT_MS) {
            glintStart.remove(key)
            return
        }
        val progress = elapsed / GLINT_MS.toFloat()
        val w = rect.width()
        val band = w * 0.5f
        // sweep a translucent white diagonal band left -> right across the key
        val cx = rect.left - band + progress * (w + band)
        glintPaint.shader = LinearGradient(
            cx - band, rect.top, cx + band, rect.bottom,
            intArrayOf(Color.TRANSPARENT, GLINT, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, glintPaint)
        glintPaint.shader = null
        // schedule the next frame; bounded because elapsed grows past GLINT_MS above
        view.postOnAnimation { view.invalidateKey(key) }
    }

    private fun buildGlass(rect: RectF, face: Face): Glass {
        val (top, mid, bottom) = when (face) {
            Face.CYAN -> Triple(0xff1d4150.toInt(), 0xff143039.toInt(), 0xff101a21.toInt())
            else -> Triple(0xff39434f.toInt(), 0xff232b34.toInt(), 0xff161c23.toInt())
        }
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        facePaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            intArrayOf(top, mid, bottom), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )

        // specular cap: brighter at the very top, fading to transparent. The spacebar uses a
        // shorter, near-uniform highlight so a wide convex cap doesn't bulge into a tube look.
        val capHeightFraction = if (face == Face.SPACEBAR) 0.22f else 0.34f
        val capBottom = rect.top + rect.height() * capHeightFraction
        val capRect = RectF(rect.left, rect.top, rect.right, capBottom)
        val capTopColor = when (face) {
            Face.CYAN -> argb(0.28f, 180, 238, 255)
            Face.GLOBE -> argb(0.12f, 255, 255, 255)
            else -> argb(0.24f, 255, 255, 255)
        }
        val capPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        capPaint.shader = LinearGradient(
            0f, rect.top, 0f, capBottom,
            intArrayOf(capTopColor, transparent(capTopColor)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        return Glass(facePaint, capPaint, capRect)
    }

    private fun faceOf(key: Key): Face = when {
        key.code == KeyCode.LANGUAGE_SWITCH -> Face.GLOBE
        key.backgroundType == Key.BACKGROUND_TYPE_SPACEBAR -> Face.SPACEBAR
        key.backgroundType == Key.BACKGROUND_TYPE_FUNCTIONAL ||
            key.backgroundType == Key.BACKGROUND_TYPE_ACTION -> Face.CYAN
        else -> Face.NEUTRAL // BACKGROUND_TYPE_NORMAL: letters, '.', etc.
    }

    private fun topEdgeColor(face: Face) = when (face) {
        Face.CYAN -> argb(0.50f, 0, 212, 255)
        Face.GLOBE -> argb(0.14f, 255, 255, 255)
        else -> argb(0.28f, 255, 255, 255)
    }

    private fun cacheKey(w: Int, h: Int, face: Face): Long =
        (w.toLong() shl 34) or (h.toLong() shl 4) or face.ordinal.toLong()

    /** drop cached glint state when the keyboard is rebuilt so released keys don't linger */
    fun reset() {
        glintStart.clear()
    }

    companion object {
        private const val GLINT_MS = 420L // one-shot press glint duration (§6.1: ~0.42s)
        private val SHADOW = argb(0.50f, 0, 0, 0)        // lift shadow rgba(0,0,0,0.50)
        private val BOTTOM_EDGE = argb(0.50f, 0, 0, 0)   // bottom thickness edge rgba(0,0,0,0.50)
        private val PRESS_POP = argb(0.07f, 255, 255, 255)
        private val GLINT = argb(0.40f, 255, 255, 255)   // press glint rgba(255,255,255,0.40)

        private fun argb(a: Float, r: Int, g: Int, b: Int) =
            Color.argb((a * 255).toInt(), r, g, b)

        private fun transparent(color: Int) = color and 0x00ffffff
    }
}
