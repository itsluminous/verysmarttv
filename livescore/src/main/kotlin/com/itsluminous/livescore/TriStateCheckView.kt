package com.itsluminous.livescore

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * The picker's box (ADR-045): an empty rounded square for [SelectionState.NONE], the square with a horizontal
 * bar for [SelectionState.PARTIAL] (some of what is under it is followed), a filled square with a tick for
 * [SelectionState.ALL]. Drawn, not a glyph, so every TV shows the same three shapes; the content description
 * names the state for accessibility. Not focusable itself: the row around it is.
 */
class TriStateCheckView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val box = RectF()
    private val path = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_DP * density
        color = WHITE
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = WHITE
    }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = MARK_DP * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = SLATE
    }
    private val bar = Paint(mark).apply { color = WHITE }

    var state: SelectionState = SelectionState.NONE
        set(value) {
            if (field == value) return
            field = value
            contentDescription = context.getString(description(value))
            invalidate()
        }

    init {
        contentDescription = context.getString(description(state))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val inset = stroke.strokeWidth / 2
        val left = (width - size) / 2 + inset
        val top = (height - size) / 2 + inset
        box.set(left, top, left + size - 2 * inset, top + size - 2 * inset)
        val radius = RADIUS_DP * density
        when (state) {
            SelectionState.NONE -> canvas.drawRoundRect(box, radius, radius, stroke)
            SelectionState.PARTIAL -> {
                canvas.drawRoundRect(box, radius, radius, stroke)
                val y = box.centerY()
                val dx = box.width() * BAR_INSET
                canvas.drawLine(box.left + dx, y, box.right - dx, y, bar)
            }
            SelectionState.ALL -> {
                canvas.drawRoundRect(box, radius, radius, fill)
                path.reset()
                path.moveTo(box.left + box.width() * TICK_X1, box.top + box.height() * TICK_Y1)
                path.lineTo(box.left + box.width() * TICK_X2, box.top + box.height() * TICK_Y2)
                path.lineTo(box.left + box.width() * TICK_X3, box.top + box.height() * TICK_Y3)
                canvas.drawPath(path, mark)
            }
        }
    }

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val SLATE = 0xFF1B2430.toInt()
        private const val STROKE_DP = 2f
        private const val MARK_DP = 2.5f
        private const val RADIUS_DP = 4f
        private const val BAR_INSET = 0.22f
        private const val TICK_X1 = 0.22f
        private const val TICK_Y1 = 0.52f
        private const val TICK_X2 = 0.42f
        private const val TICK_Y2 = 0.72f
        private const val TICK_X3 = 0.78f
        private const val TICK_Y3 = 0.30f

        /** The state's name for accessibility; pure, for `SelectionStateTest`. */
        fun description(state: SelectionState): Int =
            when (state) {
                SelectionState.NONE -> R.string.check_none
                SelectionState.PARTIAL -> R.string.check_partial
                SelectionState.ALL -> R.string.check_all
            }
    }
}
