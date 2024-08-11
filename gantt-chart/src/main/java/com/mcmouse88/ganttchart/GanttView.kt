package com.mcmouse88.ganttchart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
    private val periodNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.gantt_period_name_text_size)
        color = ContextCompat.getColor(context, R.color.grey_500)
    }

    // For task shapes
    private val taskShapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // For task names
    private val taskNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.gantt_task_name_text_size)
        color = Color.WHITE
    }

    // For cutting off a semicircle from the task
    private val cutOutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    // Column width with the period
    private val periodWidth = resources.getDimensionPixelSize(R.dimen.gantt_period_width)

    // Row height
    private val rowHeight = resources.getDimensionPixelSize(R.dimen.gantt_row_height)

    // Task corner radius
    private val taskCornerRadius = resources.getDimension(R.dimen.gantt_task_corner_radius)

    // Task vertical margin inside a row
    private val taskVerticalMargin = resources.getDimension(R.dimen.gantt_task_vertical_margin)

    // Task text horizontal margin inside its shape
    private val taskTextHorizontalMargin = resources.getDimension(R.dimen.gantt_task_text_horizontal_margin)

    // Alternating row colors
    private val rowColors = listOf(
        ContextCompat.getColor(context, R.color.grey_100),
        Color.WHITE
    )

    private val contentWidth: Int
        get() = periodWidth * periods.getValue(periodType).size

    // Rect for drawing rows
    private val rowRect = Rect()

    private val today = LocalDate.now()
    private val startDate = today.minusMonths(MONTH_COUNT)
    private val endDate = today.plusMonths(MONTH_COUNT)
    private val allDaysCount = ChronoUnit.DAYS.between(startDate, endDate).toFloat()

    private var periodType = PeriodType.MONTH
    private val periods = initPeriods()

    private var tasks: List<Task> = emptyList()
    private var uiTasks: List<UiTask> = emptyList()

    fun setTasks(tasks: List<Task>) {
        if (tasks != this.tasks) {
            this.tasks = tasks
            uiTasks = tasks.mapIndexed { index, task ->
                UiTask(task).apply { updateRect(index) }
            }

            // Notify to recalculated sizes
            requestLayout()
            // Notify to redraw
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        fun calculateSize(measureSpec: Int, contentSize: Int): Int {
            // Size from Spec (constraint)
            val specSize = MeasureSpec.getSize(measureSpec)
            // Calculate the total size based on the mode from Spec
            return when (MeasureSpec.getMode(heightMeasureSpec)) {
                // We don't have any constraints - occupy content size
                MeasureSpec.UNSPECIFIED -> contentSize
                // Limit is AT_MOST - occupy exactly as specified in the spec
                MeasureSpec.EXACTLY -> specSize
                // it's possible to occupy less space than specified, but not more
                MeasureSpec.AT_MOST -> contentSize.coerceAtMost(specSize)
                // Calm down the compiler, this branch will never be reached
                else -> error("Unreachable")
            }
        }

        val width = calculateSize(widthMeasureSpec, contentWidth)

        // Height of all rows with tasks and rows with periods
        val contentHeight = rowHeight * (tasks.size + 1)
        val height = calculateSize(heightMeasureSpec, contentHeight)

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Size has been changed, it's necessary to recalculate row width
        rowRect.set(0, 0, w, rowHeight)

        // And placement of tasks
        uiTasks.forEachIndexed { index, uiTask -> uiTask.updateRect(index) }
        // And gradient size
        taskShapePaint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            0f,
            ContextCompat.getColor(context, R.color.blue_600),
            Color.WHITE,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) = with(canvas) {
        drawRows()
        drawSeparators()
        drawPeriodNames()
        drawTasks()
    }

    private fun Canvas.drawRows() {
        repeat(tasks.size + 1) { index ->
            // The row Rect is created in advance to avoid creating object during rendering
            // But it's possible to move it around.
            rowRect.offsetTo(0, rowHeight * index)
            // Alternate row colors
            rowPaint.color = rowColors[index % rowColors.size]
            drawRect(rowRect, rowPaint)
        }
    }

    private fun Canvas.drawSeparators() {
        // Separator between periods and tasks
        val horizontalSeparatorY = rowHeight.toFloat()
        drawLine(
            0f,
            horizontalSeparatorY,
            width.toFloat(),
            horizontalSeparatorY,
            separatorPaint
        )

        // Separators between periods
        repeat(periods.getValue(periodType).size) { index ->
            val separatorX = periodWidth * (index + 1f)
            drawLine(
                separatorX,
                0f,
                separatorX,
                height.toFloat(),
                separatorPaint
            )
        }
    }

    private fun Canvas.drawPeriodNames() {
        val currentPeriods = periods.getValue(periodType)
        val nameY = periodNamePaint.getTextBaselineByCenter(rowHeight / 2f)
        currentPeriods.forEachIndexed { index, periodName ->
            // Text is rendering relative to its starting point on the X axis
            val nameX = periodWidth * (index + 0.5f) - periodNamePaint.measureText(periodName) / 2
            drawText(periodName, nameX, nameY, periodNamePaint)
        }
    }

    private fun Canvas.drawTasks() {
        uiTasks.forEach { uiTask ->
            val taskRect = uiTask.rect
            val taskName = uiTask.task.name

            // Drawing shape
            drawRoundRect(taskRect, taskCornerRadius, taskCornerRadius, taskShapePaint)

            // Cutting off a piece from the shape
            drawCircle(
                taskRect.left,
                taskRect.centerY(),
                taskRect.height() / 4f,
                cutOutPaint
            )

            // Name position
            val textX = taskRect.left + taskTextHorizontalMargin
            val textY = taskNamePaint.getTextBaselineByCenter(taskRect.centerY())

            // Symbols count from text, which will fit in the shape
            val charCount = taskNamePaint.breakText(
                taskName,
                true,
                taskRect.width() - taskTextHorizontalMargin * 2,
                null
            )
            drawText(
                taskName.substring(startIndex = 0, endIndex = charCount),
                textX,
                textY,
                taskNamePaint
            )
        }
    }

    private fun Paint.getTextBaselineByCenter(center: Float): Float {
        return center - (descent() + ascent()) / 2
    }

    private fun initPeriods(): Map<PeriodType, List<String>> {
        return PeriodType.entries.associateWith { periodType ->
            var lastDate = startDate
            mutableListOf<String>().apply {
                while (lastDate <= endDate) {
                    add(periodType.getDateString(lastDate))
                    lastDate = periodType.increment(lastDate)
                }
            }
        }
    }

    private inner class UiTask(val task: Task) {
        val rect = RectF()

        fun updateRect(index: Int) {
            val startPercent = ChronoUnit.DAYS.between(startDate, task.dateStart) / allDaysCount
            val endPercent = ChronoUnit.DAYS.between(startDate, task.dateEnd) / allDaysCount

            rect.set(
                contentWidth * startPercent,
                rowHeight * (index + 1f) + taskVerticalMargin,
                contentWidth * endPercent,
                rowHeight * (index + 2f) - taskVerticalMargin
            )
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