package com.mcmouse88.ganttchart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.temporal.IsoFields

class GanttView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // For rows
    private val rowPaint = Paint().apply { style = Paint.Style.FILL }

    // For separators
    private val separatorPaint = Paint().apply {
        strokeWidth = resources.getDimension(R.dimen.gantt_separator_width)
        color = ContextCompat.getColor(context, R.color.grey_300)
    }

    // For period names
    private val periodNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.gantt_period_name_text_size)
        color = ContextCompat.getColor(context, R.color.grey_500)
    }

    // Column width with the period
    private val periodWidth = resources.getDimensionPixelSize(R.dimen.gantt_period_width)

    // Row height
    private val rowHeight = resources.getDimensionPixelSize(R.dimen.gantt_row_height)

    // Alternating row colors
    private val rowColors = listOf(
        ContextCompat.getColor(context, R.color.grey_100),
        Color.WHITE
    )

    private val contentWidth: Int
        get() = periodWidth * periods.getValue(periodType).size

    // Rect for drawing rows
    private val rowRect = Rect()

    private var periodType = PeriodType.MONTH
    private val periods = initPeriods()

    private var tasks: List<Task> = emptyList()

    fun setTasks(tasks: List<Task>) {
        if (tasks != this.tasks) {
            this.tasks = tasks

            // Notify to recalculated sizes
            requestLayout()
            // Notify to redraw
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            contentWidth
        } else {
            // Even if AT_MOST fill all available space, because there might be zoom
            MeasureSpec.getSize(widthMeasureSpec)
        }

        // Height of all rows with tasks and rows with periods
        val contentHeight = rowHeight * (tasks.size + 1)
        val heightSpecSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            // We don't have any constraints - occupy content size
            MeasureSpec.UNSPECIFIED -> contentHeight
            // Limit is AT_MOST - occupy exactly as specified in the spec
            MeasureSpec.EXACTLY -> heightSpecSize
            // it's possible to occupy less space than specified, but not more
            MeasureSpec.AT_MOST -> contentHeight.coerceAtMost(heightSpecSize)
            // Calm down the compiler, this branch will never be reached
            else -> error("Unreachable")
        }

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Size has been changed, it's necessary to recalculate row width
        rowRect.set(0, 0, w, rowHeight)
    }

    override fun onDraw(canvas: Canvas) = with(canvas) {
        drawRows()
        drawPeriods()
    }

    private fun Canvas.drawRows() {
        repeat(tasks.size + 1) { index ->
            // The row Rect is created in advance to avoid creating object during rendering
            // But it's possible to move it around.
            rowRect.offsetTo(0, rowHeight * index)
            if (rowRect.top < height) {
                // Alternate row colors
                rowPaint.color = rowColors[index % rowColors.size]
                drawRect(rowRect, rowPaint)
            }
        }

        // Separator between periods and tasks
        val horizontalSeparatorY = rowHeight.toFloat()
        drawLine(
            0f,
            horizontalSeparatorY,
            width.toFloat(),
            horizontalSeparatorY,
            separatorPaint
        )
    }

    private fun Canvas.drawPeriods() {
        val currentPeriods = periods.getValue(periodType)
        val nameY = periodNamePaint.getTextBaselineByCenter(rowHeight / 2f)
        currentPeriods.forEachIndexed { index, periodName ->
            // Text is rendering relative to its starting point on the X axis
            val nameX = periodWidth * (index + 0.5f) - periodNamePaint.measureText(periodName) / 2
            drawText(periodName, nameX, nameY, periodNamePaint)
            val separatorX = periodWidth * (index + 1f)
            drawLine(separatorX, 0f, separatorX, height.toFloat(), separatorPaint)
        }
    }

    private fun Paint.getTextBaselineByCenter(center: Float): Float {
        return center - (descent() + ascent()) / 2
    }

    private fun initPeriods(): Map<PeriodType, List<String>> {
        val today = LocalDate.now()
        return PeriodType.entries.associateWith { periodType ->
            val startDate = today.minusMonths(MONTH_COUNT)
            val endDate = today.plusMonths(MONTH_COUNT)
            var lastDate = startDate
            mutableListOf<String>().apply {
                while (lastDate <= endDate) {
                    add(periodType.getDateString(lastDate))
                    lastDate = periodType.increment(lastDate)
                }
            }
        }
    }

    companion object {
        private const val MONTH_COUNT = 2L
    }

    private enum class PeriodType {
        MONTH {
            override fun increment(date: LocalDate): LocalDate = date.plusMonths(1)
            override fun getDateString(date: LocalDate): String = date.month.name
        },

        WEEK {
            override fun increment(date: LocalDate): LocalDate = date.plusWeeks(1)

            override fun getDateString(date: LocalDate): String {
                return date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString()
            }
        };

        abstract fun increment(date: LocalDate): LocalDate
        abstract fun getDateString(date: LocalDate): String
    }
}