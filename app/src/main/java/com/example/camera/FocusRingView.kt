package com.example.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Custom View: Ring fokus dengan tick marks di sekeliling.
 * Persis seperti vivo — lingkaran kuning + garis-garis melingkar.
 */
class FocusRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint().apply {
        color = Color.parseColor("#FFC107")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val tickPaintMajor = Paint().apply {
        color = Color.parseColor("#FFC107")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val tickPaintMinor = Paint().apply {
        color = Color.parseColor("#99FFC107")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val sunPaint = Paint().apply {
        color = Color.parseColor("#FFC107")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = (w.coerceAtMost(h) / 2f) - 50f  // sisakan ruang untuk tick
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Gambar ring utama
        canvas.drawCircle(centerX, centerY, radius, ringPaint)

        // 2. Gambar tick marks di sekeliling
        val totalTicks = 60   // 60 tick (setiap 6°)
        val majorEvery = 5    // setiap 5 tick = 1 major

        for (i in 0 until totalTicks) {
            val angleDeg = i * (360f / totalTicks) - 90f
            val angleRad = Math.toRadians(angleDeg.toDouble())

            val isMajor = (i % majorEvery == 0)
            val tickLength = if (isMajor) 22f else 12f
            val paint = if (isMajor) tickPaintMajor else tickPaintMinor

            val tickInner = radius + 8f
            val tickOuter = tickInner + tickLength

            val x1 = centerX + (tickInner * Math.cos(angleRad)).toFloat()
            val y1 = centerY + (tickInner * Math.sin(angleRad)).toFloat()
            val x2 = centerX + (tickOuter * Math.cos(angleRad)).toFloat()
            val y2 = centerY + (tickOuter * Math.sin(angleRad)).toFloat()

            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        // 3. Gambar icon matahari di tengah (lingkaran kuning kecil + garis)
        val sunRadius = 12f
        canvas.drawCircle(centerX, centerY, sunRadius, sunPaint)

        // 4. Garis-garis matahari (8 arah)
        val sunRayPaint = Paint().apply {
            color = Color.parseColor("#FFC107")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 8) {
            val angleRad = Math.toRadians((i * 45.0))
            val rayInner = sunRadius + 6f
            val rayOuter = sunRadius + 14f
            val x1 = centerX + (rayInner * Math.cos(angleRad)).toFloat()
            val y1 = centerY + (rayInner * Math.sin(angleRad)).toFloat()
            val x2 = centerX + (rayOuter * Math.cos(angleRad)).toFloat()
            val y2 = centerY + (rayOuter * Math.sin(angleRad)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, sunRayPaint)
        }
    }
}
