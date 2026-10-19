package com.wendigo.ayhalkasi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.util.Locale

class BbtChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points: List<Fertility.Point> = emptyList()

    private val density = resources.displayMetrics.density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        strokeWidth = 2f * density
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_secondary)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.panel_stroke)
        strokeWidth = 1f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_dim)
        textSize = 9f * density
        textAlign = Paint.Align.LEFT
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_dim)
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
    }

    fun setData(points: List<Fertility.Point>) {
        this.points = points.sortedBy { it.date }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (points.size < 3) {
            canvas.drawText(
                context.getString(R.string.ttc_chart_empty),
                w / 2f,
                h / 2f,
                emptyPaint
            )
            return
        }

        val padL = 40f * density
        val padR = 12f * density
        val padTop = 12f * density
        val padBottom = 20f * density
        val top = padTop
        val bottom = h - padBottom

        val minV = points.minOf { it.value }
        val maxV = points.maxOf { it.value }
        val mid = (minV + maxV) / 2.0
        var span = (maxV - minV).coerceAtLeast(0.3)
        span *= 1.25
        val low = mid - span / 2.0
        val high = mid + span / 2.0

        labelPaint.textAlign = Paint.Align.LEFT
        for (i in 0..2) {
            val v = low + (high - low) * i / 2.0
            val y = bottom - (bottom - top) * ((v - low) / (high - low)).toFloat()
            canvas.drawLine(padL, y, w - padR, y, gridPaint)
            canvas.drawText(String.format(Locale.US, "%.1f", v), 4f * density, y + 3f * density, labelPaint)
        }

        val firstDate = points.first().date
        val lastDate = points.last().date
        val totalDays = maxOf(1L, lastDate.toEpochDay() - firstDate.toEpochDay())
        val usable = w - padL - padR

        val path = Path()
        for ((i, p) in points.withIndex()) {
            val x = padL + usable * ((p.date.toEpochDay() - firstDate.toEpochDay()).toFloat() / totalDays)
            val y = bottom - (bottom - top) * ((p.value - low) / (high - low)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
        for (p in points) {
            val x = padL + usable * ((p.date.toEpochDay() - firstDate.toEpochDay()).toFloat() / totalDays)
            val y = bottom - (bottom - top) * ((p.value - low) / (high - low)).toFloat()
            canvas.drawCircle(x, y, 2.6f * density, dotPaint)
        }

        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(Dates.short(firstDate), padL, h - 4f * density, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Dates.short(lastDate), w - padR, h - 4f * density, labelPaint)
    }
}
