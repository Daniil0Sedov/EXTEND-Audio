package com.extend.audio.ui.player

/** Кастомный view для пульсации вокруг центральной кнопки воспроизведения. */

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.extend.audio.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

class AudioPulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val midGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val cyan = context.getColor(R.color.brand_cyan)
    private val outerGlowMask = BlurMaskFilter(dp(18f), BlurMaskFilter.Blur.NORMAL)
    private val midGlowMask = BlurMaskFilter(dp(11f), BlurMaskFilter.Blur.NORMAL)
    private var isEnabledState: Boolean = false
    private var isPlaying: Boolean = false
    private var level: Float = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun render(
        enabled: Boolean,
        playing: Boolean,
        level: Float,
    ) {
        val normalizedLevel = level.coerceIn(0f, 1f)
        val changed = isEnabledState != enabled ||
            isPlaying != playing ||
            abs(this.level - normalizedLevel) >= 0.035f
        if (!changed) return

        isEnabledState = enabled
        isPlaying = playing
        this.level = normalizedLevel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isEnabledState && level <= 0f) return

        val centerX = width / 2f
        val centerY = height / 2f
        val size = min(width, height).toFloat()
        val rawLevel = when {
            !isEnabledState -> 0f
            isPlaying -> maxOf(level, 0.12f)
            else -> 0.06f
        }
        val energizedLevel = rawLevel.toDouble().pow(0.68).toFloat().coerceIn(0f, 1f)

        val buttonRadius = size * 0.238f
        val ringRadius = buttonRadius + dp(3f) + (energizedLevel * dp(5f))
        val midGlowRadius = buttonRadius + dp(7f) + (energizedLevel * dp(9f))
        val outerGlowRadius = buttonRadius + dp(12f) + (energizedLevel * dp(14f))

        outerGlowPaint.color = ColorUtils.setAlphaComponent(
            cyan,
            ((42 + (energizedLevel * 120f)).toInt()).coerceIn(0, 255),
        )
        outerGlowPaint.maskFilter = outerGlowMask
        canvas.drawCircle(centerX, centerY, outerGlowRadius, outerGlowPaint)

        midGlowPaint.color = ColorUtils.setAlphaComponent(
            cyan,
            ((76 + (energizedLevel * 135f)).toInt()).coerceIn(0, 255),
        )
        midGlowPaint.maskFilter = midGlowMask
        canvas.drawCircle(centerX, centerY, midGlowRadius, midGlowPaint)

        ringPaint.strokeWidth = dp(1.8f) + (energizedLevel * dp(1.8f))
        ringPaint.color = ColorUtils.setAlphaComponent(
            cyan,
            ((130 + (energizedLevel * 90f)).toInt()).coerceIn(0, 255),
        )
        canvas.drawCircle(centerX, centerY, ringRadius, ringPaint)

        if (isPlaying) {
            ringPaint.strokeWidth = dp(0.9f)
            ringPaint.color = ColorUtils.setAlphaComponent(
                cyan,
                ((74 + (energizedLevel * 44f)).toInt()).coerceIn(0, 255),
            )
            canvas.drawCircle(centerX, centerY, ringRadius + dp(6f) + (energizedLevel * dp(4f)), ringPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
