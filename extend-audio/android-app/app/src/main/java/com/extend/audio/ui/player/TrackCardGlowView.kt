package com.extend.audio.ui.player

/** Кастомный glow-слой вокруг карточки трека, реагирующий на громкость аудио. */

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.extend.audio.R
import kotlin.math.abs
import kotlin.math.pow

class TrackCardGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val drawRect = RectF()
    private val cyan = context.getColor(R.color.brand_cyan)
    private val outerMask = BlurMaskFilter(dp(24f), BlurMaskFilter.Blur.OUTER)
    private val midMask = BlurMaskFilter(dp(12f), BlurMaskFilter.Blur.NORMAL)
    private var enabledState: Boolean = false
    private var playingState: Boolean = false
    private var levelState: Float = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun render(enabled: Boolean, playing: Boolean, level: Float) {
        val normalizedLevel = level.coerceIn(0f, 1f)
        val changed = enabledState != enabled ||
            playingState != playing ||
            abs(levelState - normalizedLevel) >= 0.045f
        if (!changed) return

        enabledState = enabled
        playingState = playing
        levelState = normalizedLevel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!enabledState && levelState <= 0f) return

        val baseLevel = when {
            !enabledState -> 0f
            playingState -> maxOf(levelState, 0.18f)
            else -> 0.1f
        }
        val energized = baseLevel.toDouble().pow(0.7).toFloat().coerceIn(0f, 1f)
        val inset = dp(6f)
        val radius = dp(24f)

        drawRect.set(
            inset,
            inset,
            width - inset,
            height - inset,
        )

        fillPaint.color = ColorUtils.setAlphaComponent(
            cyan,
            ((4 + energized * 12f).toInt()).coerceIn(0, 255),
        )
        canvas.drawRoundRect(drawRect, radius, radius, fillPaint)

        outerPaint.strokeWidth = dp(3.6f) + (energized * dp(1.8f))
        outerPaint.color = ColorUtils.setAlphaComponent(
            cyan,
            ((88 + energized * 120f).toInt()).coerceIn(0, 255),
        )
        outerPaint.maskFilter = outerMask
        canvas.drawRoundRect(drawRect, radius, radius, outerPaint)

        midPaint.strokeWidth = dp(1.9f) + (energized * dp(0.9f))
        midPaint.color = ColorUtils.setAlphaComponent(
            cyan,
            ((124 + energized * 92f).toInt()).coerceIn(0, 255),
        )
        midPaint.maskFilter = midMask
        canvas.drawRoundRect(drawRect, radius, radius, midPaint)

        innerPaint.strokeWidth = dp(1.1f)
        innerPaint.color = ColorUtils.setAlphaComponent(
            cyan,
            ((84 + energized * 72f).toInt()).coerceIn(0, 255),
        )
        innerPaint.maskFilter = null
        canvas.drawRoundRect(drawRect, radius, radius, innerPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
