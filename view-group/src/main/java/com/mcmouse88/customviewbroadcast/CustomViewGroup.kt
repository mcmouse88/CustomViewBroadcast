package com.mcmouse88.customviewbroadcast

import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Paint
import android.text.Editable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.BaseInterpolator
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import kotlin.math.max

class CustomViewGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private val firstChild: View?
        get() = if (childCount > 0) getChildAt(0) else null

    private val secondChild: View?
        get() = if (childCount > 1) getChildAt(1) else null

    private var verticalOffset = 0

    /**
     * Use [TextPaint] with working in a text layout instead of simple [Paint]
     */
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 80f }

    // Layout which calculates a multiline text
    private var textLayout: Layout? = null

    // Changeable text
    private var editable: Editable = SpannableStringBuilder()

    /*private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4_000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE

        addUpdateListener { animator ->
            val animatedValue = animator.animatedValue as Float

            val startValue = "Hello"
            val endValue = "Hello, how have you been?"

            val lengthDiff = endValue.length - startValue.length
            val currentDiff = (lengthDiff * animatedValue).toInt()
            val result = if (currentDiff > 0) {
                endValue.substring(0, startValue.length + currentDiff)
            } else {
                startValue.substring(0, startValue.length + currentDiff)
            }

            (firstChild as? TextView)?.text = result
        }
    }*/

    private val animator = ValueAnimator.ofObject(
        StringEvaluator(),
        "Hello",
        "Hello, how have you been?"
    ).apply {
        duration = 4_000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = TwoStepsInterpolator()

        addUpdateListener { animator ->
            val animateValue = animator.animatedValue.toString()
            (firstChild as? TextView)?.text = animateValue
        }
    }

    init {
        context.withStyledAttributes(attrs, R.styleable.CustomViewGroup, defStyleAttr) {
            verticalOffset = getDimensionPixelOffset(R.styleable.CustomViewGroup_verticalOffset, 0)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    /**
     * Stop animator when activity onStopped
     */
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) {
//            animator.cancel()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        checkChildCount()

        firstChild?.let { measureChild(it, widthMeasureSpec) }
        secondChild?.let { measureChild(it, widthMeasureSpec) }

        val firstWidth = firstChild?.measuredWidth ?: 0
        val firstHeight = firstChild?.measuredHeight ?: 0
        val secondWidth = secondChild?.measuredWidth ?: 0
        val secondHeight = secondChild?.measuredHeight ?: 0

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd

        val childrenOnSameLine =
            firstWidth + secondWidth < widthSize || widthMode == MeasureSpec.UNSPECIFIED
        val width = when (widthMode) {
            MeasureSpec.UNSPECIFIED -> firstWidth + secondWidth
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> {
                if (childrenOnSameLine) {
                    firstWidth + secondWidth
                } else {
                    max(firstWidth, secondWidth)
                }
            }

            else -> error("Unreachable")
        }

        val height = if (childrenOnSameLine) {
            max(firstHeight, secondHeight)
        } else {
            firstHeight + secondHeight + verticalOffset
        }

        setMeasuredDimension(width + paddingStart + paddingEnd, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        firstChild?.layout(
            paddingStart,
            paddingTop,
            paddingStart + (firstChild?.measuredWidth ?: 0),
            paddingTop + (firstChild?.measuredHeight ?: 0)
        )

        secondChild?.layout(
            r - l - paddingEnd - (secondChild?.measuredWidth ?: 0),
            b - t - paddingBottom - (secondChild?.measuredHeight ?: 0),
            r - l - paddingEnd,
            b - t - paddingBottom
        )
    }

    /**
     * MeasureSpec Mode description
     * - UNSPECIFIED - size doesn't have any constraint, it can be whatever size
     * - AT_MOST - size have a constraint and it can be this size or less
     * - EXACTLY - it can be only this size
     */
    private fun measureChild(child: View, widthMeasureSpec: Int) {
        val specSize = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd

        val childWidthSpec = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> widthMeasureSpec
            MeasureSpec.AT_MOST -> widthMeasureSpec
            MeasureSpec.EXACTLY -> MeasureSpec.makeMeasureSpec(specSize, MeasureSpec.AT_MOST)
            else -> error("Unreachable")
        }

        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        child.measure(childWidthSpec, childHeightSpec)
    }

    private fun checkChildCount() {
        if (childCount > 2) error("CustomViewGroup should not contain more than 2 children")
    }

    /**
     * When you need to handle margin values for a custom view, you need to override several methods
     * which are tied to generating layout params
     */
    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        // From layout
        return MarginLayoutParams(context, attrs)
    }

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams {
        // transform other params to ours
        return MarginLayoutParams((p))
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        // When view was added from code to view group
        return MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    override fun checkLayoutParams(p: LayoutParams?): Boolean {
        return p is MarginLayoutParams
    }

    /**
     * The class for calculation fraction of float for the particular object
     */
    class StringEvaluator : TypeEvaluator<String> {

        override fun evaluate(fraction: Float, startValue: String, endValue: String): String {
            val coercedFraction = fraction.coerceIn(0f, 1f)

            val lengthDiff = endValue.length - startValue.length
            val currentDiff = (lengthDiff * coercedFraction).toInt()
            return if (currentDiff > 0) {
                endValue.substring(0, startValue.length + currentDiff)
            } else {
                startValue.substring(0, startValue.length + currentDiff)
            }
        }
    }

    class TwoStepsInterpolator : BaseInterpolator() {
        // input - progress of the animation due time
        // return - progress the animated value. 0 is start value, 1 is end value
        override fun getInterpolation(input: Float): Float {
            return when {
                input < 0.3f -> 0.5f * (input / 0.3f)
                input > 0.7f -> 0.5f + (0.5f * (input - 0.7f) / 0.3f)
                else -> 0.5f
            }
        }
    }
}