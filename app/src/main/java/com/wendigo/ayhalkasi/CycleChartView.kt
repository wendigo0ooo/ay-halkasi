package com.wendigo.ayhalkasi

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class CycleChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lengths: List<Int> = emptyList()
    private var avg: Int = 0

    private val density = resources.displayMetrics.density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.accent(context)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.panel_stroke)
        strokeWidth = 1f * density
    }
    private val avgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.secondary(context)
        strokeWidth = 1.4f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_dim)
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_dim)
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
    }

    fun setData(lengths: List<Int>, average: Int) {
        this.lengths = lengths.takeLast(8)
        this.avg = average
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (lengths.isEmpty()) {
            canvas.drawText(
                context.getString(R.string.chart_empty),
                w / 2f,
                h / 2f,
                emptyPaint
            )
            return
        }

        val padL = 10f * density
        val padR = 10f * density
        val padTop = 18f * density
        val padBottom = 20f * density
        val chartTop = padTop
        val chartBottom = h - padBottom

        val maxV = maxOf(lengths.max(), avg) + 3
        val minV = (minOf(lengths.min(), avg) - 3).coerceAtLeast(1)
        val span = (maxV - minV).coerceAtLeast(1)

        val n = lengths.size
        val slot = (w - padL - padR) / n
        val barW = slot * 0.5f
        val radius = barW * 0.28f

        for (i in 0 until n) {
            val v = lengths[i]
            val cx = padL + slot * (i + 0.5f)
            val top = chartBottom - (chartBottom - chartTop) * ((v - minV).toFloat() / span)
            val rect = RectF(cx - barW / 2f, top, cx + barW / 2f, chartBottom)
            canvas.drawRoundRect(rect, radius, radius, barPaint)
            canvas.drawText(v.toString(), cx, top - 5f * density, valuePaint)
        }

        if (avg > 0) {
            val y = chartBottom - (chartBottom - chartTop) * ((avg - minV).toFloat() / span)
            canvas.drawLine(padL, y, w - padR, y, avgPaint)
        }
        canvas.drawLine(padL, chartBottom, w - padR, chartBottom, axisPaint)
    }
}
