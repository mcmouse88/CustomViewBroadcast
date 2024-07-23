package com.mcmouse88.customviewbroadcast

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.text.DynamicLayout
import android.text.Editable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View

class AnimateTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Use [TextPaint] with working in a text layout instead of simple [Paint]
     */
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 80f }

    // Layout which calculates a multiline text
    private var textLayout: Layout? = null

    // Changeable text
    private var editable: Editable = SpannableStringBuilder()

    private val animator = ValueAnimator.ofObject(
        CustomViewGroup.StringEvaluator(),
        "Hello",
        "Hello, how have you been?"
    ).apply {
        duration = 4_000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = CustomViewGroup.TwoStepsInterpolator()

        addUpdateListener { animator ->
            val animatedValue = animator.animatedValue.toString()

            // Need to recalculate size only if changes lines count
            val prevLineCount = textLayout?.lineCount
            editable.replace(0, editable.length, animatedValue)
            if (textLayout?.lineCount != prevLineCount) {
                requestLayout()
            }

            // Need to redraw anyway
            invalidate()
        }
    }

    init {
        setBackgroundColor(Color.LTGRAY)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // We don't know max width of the text, spread all width
        val widthSpecSize = MeasureSpec.getSize(widthMeasureSpec)
        val width = if (widthSpecSize > 0) widthSpecSize else 500
        val height = textLayout?.height ?: (textPaint.descent() - textPaint.ascent()).toInt()

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == oldw) return

        // Use Dynamic Layout because the text changes
        textLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DynamicLayout.Builder
                .obtain(editable, textPaint, w)
                .build()
        } else {
            @Suppress("DEPRECATION")
            DynamicLayout(
                editable,
                textPaint,
                w,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f,
                1.0f,
                false
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        textLayout?.draw(canvas)
    }
}