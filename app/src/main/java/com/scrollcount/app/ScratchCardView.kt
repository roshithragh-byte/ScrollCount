package com.scrollcount.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ScratchCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var overlayBitmap: Bitmap? = null
    private var overlayCanvas: Canvas? = null
    private val touchPath = Path()
    private var isRevealed = false

    var onRevealedListener: (() -> Unit)? = null

    private val erasePaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        color = Color.TRANSPARENT
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 68f
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#48E0C1")
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val subTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#94A3B8")
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            createFoilOverlay(w, h)
        }
    }

    private fun createFoilOverlay(w: Int, h: Int) {
        overlayBitmap?.recycle()
        overlayBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        overlayCanvas = Canvas(overlayBitmap!!)

        val foilPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(
                    Color.parseColor("#161F30"),
                    Color.parseColor("#26354D"),
                    Color.parseColor("#101827")
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        overlayCanvas?.drawRect(0f, 0f, w.toFloat(), h.toFloat(), foilPaint)

        // Subtle diagonal shimmer lines
        val stripePaint = Paint().apply {
            color = Color.parseColor("#2248E0C1")
            strokeWidth = 4f
        }
        var x = -h.toFloat()
        while (x < w + h) {
            overlayCanvas?.drawLine(x, 0f, x + h, h.toFloat(), stripePaint)
            x += 44f
        }

        overlayCanvas?.drawText("SCRATCH TO REVEAL", w / 2f, h / 2f - 8f, textPaint)
        overlayCanvas?.drawText("Drag finger across foil", w / 2f, h / 2f + 34f, subTextPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isRevealed) {
            overlayBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isRevealed) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchPath.reset()
                touchPath.moveTo(x, y)
                overlayCanvas?.drawCircle(x, y, 34f, erasePaint)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                touchPath.lineTo(x, y)
                overlayCanvas?.drawPath(touchPath, erasePaint)
                invalidate()
                checkScratchedPercentage()
            }
            MotionEvent.ACTION_UP -> {
                touchPath.reset()
                checkScratchedPercentage()
            }
        }
        return true
    }

    private fun checkScratchedPercentage() {
        val bmp = overlayBitmap ?: return
        if (isRevealed) return

        val w = bmp.width
        val h = bmp.height
        val step = 10
        var totalSamples = 0
        var clearedSamples = 0

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                totalSamples++
                if (Color.alpha(bmp.getPixel(x, y)) == 0) {
                    clearedSamples++
                }
                x += step
            }
            y += step
        }

        if (totalSamples > 0 && (clearedSamples.toFloat() / totalSamples) >= 0.60f) {
            isRevealed = true
            invalidate()
            onRevealedListener?.invoke()
        }
    }

    fun resetCard() {
        isRevealed = false
        if (width > 0 && height > 0) {
            createFoilOverlay(width, height)
        }
        invalidate()
    }
}
