package com.example.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Custom View: Slider exposure melingkar.
 * User bisa geser melingkar untuk atur exposure (EV -3 sampai +3).
 */
class ExposureSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ===== KONFIGURASI =====
    var minEV: Float = -3.0f
    var maxEV: Float = 3.0f
    var currentEV: Float = 0f
        set(value) {
            field = value.coerceIn(minEV, maxEV)
            updateTickHighlight()
            invalidate()
            onExposureChanged?.invoke(field)
        }

    // Callback saat user geser
    var onExposureChanged: ((Float) -> Unit)? = null

    // ===== PAINT =====
    private val ringPaint = Paint().apply {
        color = Color.parseColor("#66FFC107")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val tickPaintNormal = Paint().apply {
        color = Color.parseColor("#44FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val tickPaintHighlight = Paint().apply {
        color = Color.parseColor("#FFC107")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val dotPaint = Paint().apply {
        color = Color.parseColor("#FF8C00")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // ===== GEOMETRI =====
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private val totalTicks = 60

    // Sudut awal slider (di kiri bawah ring, seperti vivo)
    // vivo: slider mulai dari ~225° (bawah-kiri) dan berakhir ~315° (bawah-kanan)
    // Tapi karena ini full circle, kita pakai full 360° untuk exposure
    // Simpel: -90° (atas) = tengah, geser CW = naik, CCW = turun
    private val startAngle = -90f
    private var currentAngle = -90f  // posisi dot

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = (w.coerceAtMost(h) / 2f) - 70f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Ring background
        val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawArc(rect, 0f, 360f, false, ringPaint)

        // 2. Tick marks di luar ring
        for (i in 0 until totalTicks) {
            val angleDeg = i * (360f / totalTicks) - 90f
            val angleRad = Math.toRadians(angleDeg.toDouble())

            val tickInner = radius + 6f
            val tickOuter = tickInner + 14f

            val x1 = centerX + (tickInner * Math.cos(angleRad)).toFloat()
            val y1 = centerY + (tickInner * Math.sin(angleRad)).toFloat()
            val x2 = centerX + (tickOuter * Math.cos(angleRad)).toFloat()
            val y2 = centerY + (tickOuter * Math.sin(angleRad)).toFloat()

            // Highlight tick kalau mendekati EV saat ini
            val evPerTick = (maxEV - minEV) / totalTicks
            val tickEV = minEV + (i * evPerTick)
            val isNearCurrent = Math.abs(tickEV - currentEV) < evPerTick * 1.5f

            canvas.drawLine(x1, y1, x2, y2, if (isNearCurrent) tickPaintHighlight else tickPaintNormal)
        }

        // 3. Dot posisi saat ini
        val dotRadius = 10f
        val dotX = centerX + (radius * Math.cos(Math.toRadians(currentAngle.toDouble()))).toFloat()
        val dotY = centerY + (radius * Math.sin(Math.toRadians(currentAngle.toDouble()))).toFloat()
        canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
    }

    private fun updateTickHighlight() {
        // Konversi EV ke sudut
        // EV -3 → sudut -180° (kiri)
        // EV 0 → sudut -90° (atas)
        // EV +3 → sudut 0° (kanan)
        val t = (currentEV - minEV) / (maxEV - minEV)   // 0..1
        currentAngle = -180f + (t * 180f)              // -180° → 0°
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Hitung sudut jari dari center
                val dx = event.x - centerX
                val dy = event.y - centerY
                var angleDeg = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()

                // Normalisasi: -180°..0° → EV min..max
                if (angleDeg > 0) angleDeg -= 360f  // paksa ke -360..0
                angleDeg = angleDeg.coerceIn(-180f, 0f)

                val t = (angleDeg + 180f) / 180f   // 0..1
                val newEV = minEV + (t * (maxEV - minEV))

                currentEV = newEV
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
