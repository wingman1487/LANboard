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

    init {
        clipToOutline = false
    }

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
    private var maxSpikeFrac = 0.35f

    /** Active preset color (§6.2): the listening spike-TIP color. The cyan baseline circle and the
     *  ring STROKE (the server-state channel) are never tinted by this. */
    var presetColor: Int = LBPresetPalette.GENERAL_CYAN
        private set
    /** General keeps the v1 single-gradient cyan→electric-blue→deep-blue look (§6.2); every other
     *  preset renders the per-spike radial gradient (cyan base → preset tip). */
    var isGeneralPreset: Boolean = true
        private set

    /** Set the active preset's spike-tip color and whether it is the General preset. */
    fun setActivePreset(colorInt: Int, isGeneral: Boolean) {
        if (colorInt == presetColor && isGeneral == isGeneralPreset) return
        presetColor = colorInt
        isGeneralPreset = isGeneral
        if (state == State.LISTENING) invalidate()
    }

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
        baseRadius = min(w, h) * 0.35f
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
        // Amplify for visualization — raw RMS from mic is typically 0.01-0.15
        val boosted = min(1f, loudness * 50f)
        if (boosted < 0.03f) return
        val numNew = floor(1 + boosted * 6 + Math.random().toFloat() * 3).toInt()
        for (i in 0 until numNew) {
            val idx = (Math.random() * numSamples).toInt()
            val amp = boosted * (0.6f + Math.random().toFloat() * 0.4f)
            spikes[idx] = max(spikes[idx], amp)
            val left = (idx - 1 + numSamples) % numSamples
            val right = (idx + 1) % numSamples
            spikes[left] = max(spikes[left], amp * 0.55f)
            spikes[right] = max(spikes[right], amp * 0.55f)
        }
    }

    private fun decay() {
        for (i in 0 until numSamples) {
            spikes[i] *= 0.75f
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

        val r = lerp(74f, 100f, breath).toInt()
        val g = lerp(83f, 255f, breath).toInt()
        val b = lerp(96f, 180f, breath).toInt()
        val alpha = ((0.5f + breath * 0.5f) * 255).toInt()

        strokePaint.color = android.graphics.Color.argb(alpha, r, g, b)
        strokePaint.strokeWidth = lineWidth
        canvas.drawCircle(cx, cy, baseRadius, strokePaint)
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
        // Baseline circle — always cyan, the signature (§6.2). Independent of preset color.
        strokePaint.color = android.graphics.Color.argb(128, 0, 212, 255)
        strokePaint.strokeWidth = lineWidth
        canvas.drawCircle(cx, cy, baseRadius, strokePaint)

        val maxSpikeOut = baseRadius * maxSpikeFrac
        if (isGeneralPreset) drawListeningGeneral(canvas, maxSpikeOut)
        else drawListeningPerSpike(canvas, maxSpikeOut)
    }

    /** General preset (§6.2): the v1 look — one closed path over all samples, stroked with a single
     *  canvas-wide cyan → electric-blue → deep-blue gradient. */
    private fun drawListeningGeneral(canvas: Canvas, maxSpikeOut: Float) {
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
        spikePathPaint.strokeCap = Paint.Cap.BUTT
        canvas.drawPath(spikePath, spikePathPaint)
        spikePathPaint.shader = null
    }

    /** Non-General presets (§6.2, Approach B): one radial line per active spike, base on the cyan
     *  perimeter → tip in the preset color, so the active preset color reads at the spike tips while
     *  cyan stays the baseline. Inactive samples are skipped to keep the per-frame gradient count low
     *  (30fps budget, §13.2). No shadow pass — matches the v1 profile; revisit if device QA wants glow. */
    private fun drawListeningPerSpike(canvas: Canvas, maxSpikeOut: Float) {
        spikePathPaint.strokeWidth = lineWidth
        spikePathPaint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until numSamples) {
            val s = spikes[i]
            if (s < 0.02f) continue
            val angle = (i.toFloat() / numSamples) * Math.PI.toFloat() * 2f - Math.PI.toFloat() / 2f
            val ca = cos(angle.toDouble()).toFloat()
            val sa = sin(angle.toDouble()).toFloat()
            val baseX = cx + ca * baseRadius
            val baseY = cy + sa * baseRadius
            val tipR = baseRadius + s * maxSpikeOut
            val tipX = cx + ca * tipR
            val tipY = cy + sa * tipR
            spikePathPaint.shader = LinearGradient(
                baseX, baseY, tipX, tipY,
                colorCyan, presetColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawLine(baseX, baseY, tipX, tipY, spikePathPaint)
        }
        spikePathPaint.shader = null
        spikePathPaint.strokeCap = Paint.Cap.BUTT
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
        canvas.drawArc(
            cx - baseRadius, cy - baseRadius,
            cx + baseRadius, cy + baseRadius,
            startDeg, arcLen, false, strokePaint
        )
        strokePaint.strokeCap = Paint.Cap.BUTT
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
