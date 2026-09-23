package com.example.sipandstride

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * A donut-shaped progress ring, drawn by hand on the Canvas.
 *
 * It extends View and overrides onDraw(), which is the whole point of a CustomView:
 * Android gives us a Canvas and we paint on it. The constructor takes (Context, AttributeSet)
 * because that is the one Android calls when the view is created from a layout XML file.
 */
class WaterRingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var ringProgress = 0
    private var ringColor = Color.parseColor("#1E88E5")
    private var ringCaption = ""
    private var centerText = "0%"

    /**
     * A Paint holds "how to draw": colour, thickness, style, anti-aliasing.
     * They are created once here and reused, because creating objects inside onDraw()
     * would allocate memory 60 times per second.
     */
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#22000000")
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.DKGRAY
    }

    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.GRAY
    }

    /** The square the arc is drawn inside. Also created once and reused. */
    private val bounds = RectF()

    /**
     * Reads the app:... attributes that were set in the layout file.
     * obtainStyledAttributes() hands out a shared array that MUST be recycled afterwards,
     * which is why the try/finally is there.
     */
    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.WaterRingView, 0, 0).apply {
            try {
                ringProgress = getInteger(R.styleable.WaterRingView_ringProgress, 0)
                ringColor = getColor(R.styleable.WaterRingView_ringColor, ringColor)
                ringCaption = getString(R.styleable.WaterRingView_ringCaption) ?: ""
            } finally {
                recycle()
            }
        }

        progressPaint.color = ringColor
    }

    /**
     * Setters used from MainActivity. invalidate() tells Android "this view is out of date,
     * call onDraw() again on the next frame". Without it nothing on screen would change.
     */
    fun setProgress(value: Int) {
        ringProgress = value.coerceIn(0, 100)
        invalidate()
    }

    fun setCenterText(text: String) {
        centerText = text
        invalidate()
    }

    fun setCaption(text: String) {
        ringCaption = text
        invalidate()
    }

    /**
     * Forces the view to be square. A ring in a rectangle would be an ellipse.
     * super.onMeasure() first lets the normal measuring happen, then we take the smaller
     * of the two sides and report it for both.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val size = minOf(measuredWidth, measuredHeight)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Basic geometry of the view
        val w = width.toFloat()
        val h = height.toFloat()

        val stroke = w / 10f
        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke

        val cx = w / 2f
        val cy = h / 2f

        // The radius is measured to the MIDDLE of the stroke, so half a stroke has to stay
        // inside the view on each side, otherwise the ring is clipped at the edges.
        val radius = w / 2f - stroke
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 2. The grey background circle: a full 360° arc
        canvas.drawArc(bounds, -90f, 360f, false, trackPaint)

        // 3. The coloured progress arc.
        // drawArc() starts at 3 o'clock, so -90° moves the start to 12 o'clock.
        // useCenter = false draws only the curve, not a pie slice.
        val sweep = 360f * ringProgress / 100f
        if (sweep > 0f) {
            canvas.drawArc(bounds, -90f, sweep, false, progressPaint)
        }

        // 4. The percentage in the middle.
        // drawText() places the LEFT edge of the text at x, so half the text width is
        // subtracted to centre it. measureText() gives that width (Tutorial 7).
        textPaint.textSize = w / 5f
        val textWidth = textPaint.measureText(centerText)
        canvas.drawText(centerText, cx - textWidth / 2f, cy + textPaint.textSize / 3f, textPaint)

        // 5. The small caption under it
        if (ringCaption.isNotEmpty()) {
            captionPaint.textSize = w / 12f
            val captionWidth = captionPaint.measureText(ringCaption)
            canvas.drawText(ringCaption, cx - captionWidth / 2f, cy + textPaint.textSize, captionPaint)
        }
    }
}