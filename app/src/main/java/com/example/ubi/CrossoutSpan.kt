package com.example.ubi

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

class CrossoutSpan : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val width = paint.measureText(text, start, end)

        // 기존 텍스트 그림
        canvas.drawText(text, start, end, x, y.toFloat(), paint)

        // 줄 그리기
        val slope = 15f
        for (i in 0..2) {
            val offsetStartY = -paint.textSize * 0.3f + (Math.random().toFloat() - 0.5f) * slope
            val offsetEndY = -paint.textSize * 0.3f + (Math.random().toFloat() - 0.5f) * slope
            val xStart = x + i * 2f
            val xEnd = x + width - i * 2f
            canvas.drawLine(xStart, y + offsetStartY, xEnd, y + offsetEndY, paint)
        }
    }
}