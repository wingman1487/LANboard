package helium314.keyboard.latin.lanboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R
import kotlin.math.max
import kotlin.math.min

class AudioMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val numBars = 18
    private val bars = FloatArray(numBars)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    private val cyanColor = ContextCompat.getColor(context, R.color.lb_meter_cyan)
    private var barWidth = 3f
    private var gap = 3f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        barWidth = 3f * density
        gap = 3f * density
    }

    fun updateAudio(loudness: Float) {
        for (i in 0 until numBars) {
            val target = loudness * (0.3f + Math.random().toFloat() * 0.7f)
            val diff = target - bars[i]
            val rate = if (diff > 0) 0.6f else 0.18f
            bars[i] += diff * rate
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val totalWidth = numBars * barWidth + (numBars - 1) * gap
        val startX = (width - totalWidth) / 2f
        val centerY = height / 2f
        val minH = 2f * resources.displayMetrics.density
        val maxH = height - 4f * resources.displayMetrics.density
        val radius = barWidth / 2f

        for (i in 0 until numBars) {
            val x = startX + i * (barWidth + gap)
            val barH = minH + bars[i] * (maxH - minH)
            val edgeDist = min(i, numBars - 1 - i)
            val edgeFade = min(1f, edgeDist / 3f)
            val alpha = (0.5f + edgeFade * 0.5f)

            barPaint.color = cyanColor
            barPaint.alpha = (alpha * 255).toInt()

            val y = centerY - barH / 2f
            rect.set(x, y, x + barWidth, y + barH)
            canvas.drawRoundRect(rect, radius, radius, barPaint)
        }
    }
}
