package io.github.portalappinspector.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.View

class PortalFloatingButton(context: Context) : View(context) {
    val springMotion = SpringyViewMotion(this)
    var minimizedX = 0f
    var minimizedY = 0f
    var isExpanded = false
    private val mark = PortalMarkPainter()

    init {
        isClickable = true
        isFocusable = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(20, 24, 31))
            setStroke(context.dp(1), Color.argb(72, 255, 255, 255))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mark.draw(canvas, width, height)
    }
}

class PortalMarkView(context: Context) : View(context) {
    private val mark = PortalMarkPainter()

    init {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(20, 24, 31))
            setStroke(context.dp(1), Color.argb(72, 255, 255, 255))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mark.draw(canvas, width, height)
    }
}

class PortalMarkPainter {
    private val leftRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rightRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 192, 30)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ringBounds = RectF()

    fun draw(canvas: Canvas, width: Int, height: Int) {
        val size = width.coerceAtMost(height).toFloat()
        val stroke = size * 0.08f
        leftRingPaint.strokeWidth = stroke
        rightRingPaint.strokeWidth = stroke

        canvas.save()
        canvas.rotate(15f, width * 0.43f, height * 0.5f)
        ringBounds.set(width * 0.25f, height * 0.2f, width * 0.63f, height * 0.8f)
        canvas.drawOval(ringBounds, leftRingPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(30f, width * 0.59f, height * 0.5f)
        ringBounds.set(width * 0.43f, height * 0.27f, width * 0.75f, height * 0.73f)
        canvas.drawOval(ringBounds, rightRingPaint)
        canvas.restore()
    }
}
