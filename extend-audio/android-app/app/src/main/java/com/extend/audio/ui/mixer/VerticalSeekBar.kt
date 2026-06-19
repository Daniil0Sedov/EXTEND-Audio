package com.extend.audio.ui.mixer

/** Кастомный вертикальный ползунок для отдельной полосы эквалайзера. */

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

class VerticalSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatSeekBar(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.rotate(-90f)
        canvas.translate(-height.toFloat(), 0f)
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP -> {
                val rawProgress = max - ((max * event.y) / height).toInt()
                val nextProgress = rawProgress.coerceIn(0, max)
                if (progress != nextProgress) {
                    setProgress(nextProgress, true)
                }
                if (event.action == MotionEvent.ACTION_UP) {
                    performClick()
                }
            }

            MotionEvent.ACTION_CANCEL -> return false
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
