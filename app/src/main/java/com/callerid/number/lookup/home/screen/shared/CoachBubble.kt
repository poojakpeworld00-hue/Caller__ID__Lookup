package com.callerid.number.lookup.home.screen.shared

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout

class CoachBubble private constructor(context: Context) : FrameLayout(context) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SCRIM_COLOR }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val holeRect = RectF()
    private val holeRadius = dp(14f)
    private var onDismiss: (() -> Unit)? = null

    init {
        setWillNotDraw(false)

        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        setOnClickListener { dismiss() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawRoundRect(holeRect, holeRadius, holeRadius, holePaint)
    }

    fun dismiss() {
        (parent as? ViewGroup)?.removeView(this)
        onDismiss?.invoke()
        onDismiss = null
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val SCRIM_COLOR = 0xB3000000.toInt()

        fun show(
            activity: Activity,
            target: View,
            bubbleRes: Int,
            onDismiss: (() -> Unit)? = null
        ): CoachBubble = show(activity.window.decorView as ViewGroup, target, bubbleRes, onDismiss)

        fun show(
            host: ViewGroup,
            target: View,
            bubbleRes: Int,
            onDismiss: (() -> Unit)? = null
        ): CoachBubble {
            val overlay = CoachBubble(host.context).apply { this.onDismiss = onDismiss }

            val targetLoc = IntArray(2)
            val hostLoc = IntArray(2)
            target.getLocationOnScreen(targetLoc)
            host.getLocationOnScreen(hostLoc)
            val left = (targetLoc[0] - hostLoc[0]).toFloat()
            val top = (targetLoc[1] - hostLoc[1]).toFloat()

            val pad = overlay.dp(6f)
            overlay.holeRect.set(
                left - pad,
                top - pad,
                left + target.width + pad,
                top + target.height + pad
            )

            val bubble = LayoutInflater.from(host.context).inflate(bubbleRes, overlay, false)
            bubble.layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (top + target.height + overlay.dp(8f)).toInt()
            }
            bubble.setOnClickListener { overlay.dismiss() }
            overlay.addView(bubble)

            host.addView(overlay, fillParams(host))
            return overlay
        }

        private fun fillParams(host: ViewGroup): ViewGroup.LayoutParams =
            if (host is ConstraintLayout) {
                ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                }
            } else {
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
    }
}
