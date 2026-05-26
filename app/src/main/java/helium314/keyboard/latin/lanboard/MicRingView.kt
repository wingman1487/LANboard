package helium314.keyboard.latin.lanboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MicRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { IDLE_OK, IDLE_UNREACHABLE, IDLE_CHECKING, LISTENING, TRANSCRIBING }

    var state: State = State.IDLE_OK
        set(value) {
            if (field != value) {
                field = value
                if (value != State.LISTENING) spikes.fill(0f)
                invalidate()
            }
        }

    private val numSamples = 128
    private val spikes = FloatArray(numSamples)
    private var spinnerAngle = 0f
    private var breathT = (Math.random() * 3.4).toFloat()
    private var maxSpikeFrac = 0.4f

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val spikePathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val spikePath = Path()

    private val colorCyan = ContextCompat.getColor(context, R.color.lb_cyan)
    private val colorElectricBlue = ContextCompat.getColor(context, R.color.lb_electric_blue)
    private val colorDeepBlue = ContextCompat.getColor(context, R.color.lb_deep_blue)
    private val colorGreenPulse = ContextCompat.getColor(context, R.color.lb_green_pulse)
    private val colorRed = ContextCompat.getColor(context, R.color.lb_red)
    private val colorAmber = ContextCompat.getColor(context, R.color.lb_amber)
    private val colorIdleGrey = ContextCompat.getColor(context, R.color.lb_ring_idle_grey)

    private var baseRadius = 0f
    private var cx = 0f
    private var cy = 0f
    private var lineWidth = 2.5f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        baseRadius = min(w, h) * 0.38f
        lineWidth = min(w, h) * 0.04f
    }

    fun updateAudio(loudness: Float) {
        if (state == State.LISTENING) {
            spawnSpikes(loudness)
        }
        decay()
        invalidate()
    }

    fun renderFrame() {
        if (state == State.IDLE_OK || state == State.IDLE_CHECKING || state == State.TRANSCRIBING) {
            invalidate()
        }
    }

    private fun spawnSpikes(loudness: Float) {
        if (loudness < 0.08f) return
        val numNew = floor(1 + loudness * 6 + Math.random().toFloat() * 3).toInt()
        for (i in 0 until numNew) {
            val idx = (Math.random() * numSamples).toInt()
            val amp = loudness * (0.6f + Math.random().toFloat() * 0.4f)
            spikes[idx] = max(spikes[idx], amp)
            val left = (idx - 1 + numSamples) % numSamples
            val right = (idx + 1) % numSamples
            spikes[left] = max(spikes[left], amp * 0.55f)
            spikes[right] = max(spikes[right], amp * 0.55f)
        }
    }

    private fun decay() {
        for (i in 0 until numSamples) {
            spikes[i] *= 0.88f
            if (spikes[i] < 0.01f) spikes[i] = 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (baseRadius <= 0f) return

        when (state) {
            State.IDLE_OK -> drawIdle(canvas)
            State.IDLE_UNREACHABLE -> drawError(canvas)
            State.IDLE_CHECKING -> drawChecking(canvas)
            State.LISTENING -> drawListening(canvas)
            State.TRANSCRIBING -> drawTranscribing(canvas)
        }
    }

    private fun drawIdle(canvas: Canvas) {
        breathT += 1f / 60f
        val breath = (sin(breathT * (2.0 * Math.PI / 3.4)).toFloat() + 1f) / 2f

        val r = lerp(74f, 61f, breath * 0.7f).toInt()
        val g = lerp(83f, 220f, breath * 0.7f).toInt()
        val b = lerp(96f, 151f, breath * 0.7f).toInt()
        val alpha = ((0.55f + breath * 0.3f) * 255).toInt()

        strokePaint.color = android.graphics.Color.argb(alpha, r, g, b)
        strokePaint.strokeWidth = lineWidth
        strokePaint.setShadowLayer(breath * 8f * resources.displayMetrics.density, 0f, 0f, colorGreenPulse)
        canvas.drawCircle(cx, cy, baseRadius, strokePaint)
        strokePaint.clearShadowLayer()
    }

    private fun drawError(canvas: Canvas) {
        strokePaint.color = android.graphics.Color.argb(128, 239, 68, 68)
        strokePaint.strokeWidth = lineWidth
        canvas.drawCircle(cx, cy, baseRadius, strokePaint)
    }

    private fun drawChecking(canvas: Canvas) {
        breathT += 1f / 60f
        val pulse = (sin(breathT * (2.0 * Math.PI / 2.0)).toFloat() + 1f) / 2f
        val alpha = ((0.4f + pulse * 0.4f) * 255).toInt()

        strokePaint.color = android.graphics.Color.argb(alpha, 240, 180, 41)
        strokePaint.strokeWidth = lineWidth
        canvas.drawCircle(cx, cy, baseRadius, strokePaint)
    }

    private fun drawListening(canvas: Canvas) {
        // Baseline circle
        strokePaint.color = android.graphics.Color.argb(128, 0, 212, 255)
        strokePaint.strokeWidth = lineWidth
        canvas.drawCircle(cx, cy, baseRadius, strokePaint)

        // Spike-modulated overlay
        val maxSpikeOut = baseRadius * maxSpikeFrac
        spikePath.reset()
        for (i in 0 until numSamples) {
            val angle = (i.toFloat() / numSamples) * Math.PI.toFloat() * 2f - Math.PI.toFloat() / 2f
            val r = baseRadius + spikes[i] * maxSpikeOut
            val x = cx + cos(angle.toDouble()).toFloat() * r
            val y = cy + sin(angle.toDouble()).toFloat() * r
            if (i == 0) spikePath.moveTo(x, y) else spikePath.lineTo(x, y)
        }
        spikePath.close()

        val grad = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(colorCyan, colorElectricBlue, colorDeepBlue),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        spikePathPaint.shader = grad
        spikePathPaint.strokeWidth = lineWidth
        spikePathPaint.setShadowLayer(10f * resources.displayMetrics.density, 0f, 0f, colorCyan)
        canvas.drawPath(spikePath, spikePathPaint)
        spikePathPaint.clearShadowLayer()
        spikePathPaint.shader = null
    }

    private fun drawTranscribing(canvas: Canvas) {
        spinnerAngle += 0.08f

        // Faint full circle
        strokePaint.color = android.graphics.Color.argb(64, 0, 212, 255)
        strokePaint.strokeWidth = lineWidth
        canvas.drawCircle(cx, cy, baseRadius, strokePaint)

        // Bright sweeping arc
        val arcLen = 0.6f * 180f / Math.PI.toFloat() // ~34 degrees
        val startDeg = Math.toDegrees(spinnerAngle.toDouble()).toFloat()
        strokePaint.color = colorCyan
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.setShadowLayer(12f * resources.displayMetrics.density, 0f, 0f, colorCyan)
        canvas.drawArc(
            cx - baseRadius, cy - baseRadius,
            cx + baseRadius, cy + baseRadius,
            startDeg, arcLen, false, strokePaint
        )
        strokePaint.strokeCap = Paint.Cap.BUTT
        strokePaint.clearShadowLayer()
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
