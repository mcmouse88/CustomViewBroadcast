package com.mcmouse88.ganttchart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
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

    // Circle radius cut out from the task shape
    private val cutOutRadius = (rowHeight - taskVerticalMargin * 2) / 4

    // Alternating row colors
    private val rowColors = listOf(
        ContextCompat.getColor(context, R.color.grey_100),
        Color.WHITE
    )

    // Gradient colors
    private val gradientStartColor = ContextCompat.getColor(context, R.color.blue_700)
    private val gradientEndColor = ContextCompat.getColor(context, R.color.blue_200)

    private val contentWidth: Int
        get() = periodWidth * periods.getValue(periodType).size

    // Rect for drawing rows
    private val rowRect = Rect()

    // Bitmap for task shapes
    private lateinit var tasksBitmap: Bitmap

    private val today = LocalDate.now()

    private var periodType = PeriodType.MONTH
    private val periods = initPeriods()

    private var tasks: List<Task> = emptyList()
    private var uiTasks: List<UiTask> = emptyList()

    fun setTasks(tasks: List<Task>) {
        if (tasks != this.tasks) {
            this.tasks = tasks
            uiTasks = tasks.map(::UiTask)
            updateTasksBitmap()

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

        // And gradient size
        taskShapePaint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            0f,
            gradientStartColor,
            gradientEndColor,
            Shader.TileMode.CLAMP
        )

        // And recreate Bitmap for task shapes
        updateTasksBitmap()
    }

    private fun updateTasksBitmap() {
        if (width == 0 || height == 0) return

        tasksBitmap = createBitmap(width, height).applyCanvas {
            uiTasks.forEachIndexed { index, uiTask ->
                uiTask.updateRect(index)
                if (uiTask.isRectOnScreen) {
                    val taskRect = uiTask.rect
                    // Task shape
                    drawRoundRect(
                        taskRect,
                        taskCornerRadius,
                        taskCornerRadius,
                        taskShapePaint
                    )
                    // Cut out a piece from the shape
                    drawCircle(
                        taskRect.left,
                        taskRect.centerY(),
                        cutOutRadius,
                        cutOutPaint
                    )
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) = with(canvas) {
        drawRows()
        drawPeriods()
        drawTasks()
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
            // Separator
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

    private fun Canvas.drawTasks() {
        drawBitmap(tasksBitmap, 0f, 0f, rowPaint)
        uiTasks.forEach { uiTask ->
            if (uiTask.isRectOnScreen) {
                val taskRect = uiTask.rect
                val taskName = uiTask.task.name

                // Name position
                val textX = taskRect.left + taskTextHorizontalMargin + cutOutRadius
                val textY = taskNamePaint.getTextBaselineByCenter(taskRect.centerY())

                // Symbols count from text, which will fit in the shape
                val charCount = taskNamePaint.breakText(
                    taskName,
                    true,
                    taskRect.width() - taskTextHorizontalMargin * 2 - cutOutRadius,
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
    }

    private fun Paint.getTextBaselineByCenter(center: Float): Float {
        return center - (descent() + ascent()) / 2
    }

    private fun initPeriods(): Map<PeriodType, List<String>> {
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

    private inner class UiTask(val task: Task) {
        val rect = RectF()

        val isRectOnScreen: Boolean
            get() = rect.top < height && (rect.right > 0 || rect.left < rect.width())

        fun updateRect(index: Int) {
            fun getX(date: LocalDate): Float? {
                val periodIndex = periods.getValue(periodType).indexOf(periodType.getDateString(date))
                return if (periodIndex >= 0) {
                    periodWidth * (periodIndex + periodType.getPercentOfPeriod(date))
                } else {
                    null
                }
            }

            rect.set(
                getX(task.dateStart) ?: -taskCornerRadius,
                rowHeight * (index + 1f) + taskVerticalMargin,
                getX(task.dateEnd) ?: (width + taskCornerRadius),
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
            override fun getPercentOfPeriod(date: LocalDate): Float {
                return (date.dayOfMonth - 1f) / date.lengthOfMonth()
            }
        },

        WEEK {
            override fun increment(date: LocalDate): LocalDate = date.plusWeeks(1)

            override fun getDateString(date: LocalDate): String {
                return date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString()
            }

            override fun getPercentOfPeriod(date: LocalDate): Float {
                return (date.dayOfWeek.value - 1f) / 7
            }
        };

        abstract fun increment(date: LocalDate): LocalDate
        abstract fun getDateString(date: LocalDate): String
        abstract fun getPercentOfPeriod(date: LocalDate): Float
    }
}