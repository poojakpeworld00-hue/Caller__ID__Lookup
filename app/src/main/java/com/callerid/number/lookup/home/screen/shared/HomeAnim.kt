package com.callerid.number.lookup.home.screen.shared

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView

object HomeAnim {

    val DECELERATE = PathInterpolator(0f, 0f, 0f, 1f)

    val EMPHASIZED = PathInterpolator(0.2f, 0.6f, 0.3f, 1f)

    private const val RISE_DISTANCE_DP = 12f
    private const val RISE_DURATION = 420L

    const val STAGGER_STEP = 55L

    fun prefersReducedMotion(context: Context): Boolean {
        val scale = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )
        return scale == 0f
    }

    fun riseIn(view: View, delay: Long = 0L, distanceDp: Float = RISE_DISTANCE_DP) {
        if (prefersReducedMotion(view.context)) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = distanceDp * view.resources.displayMetrics.density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(RISE_DURATION)
            .setInterpolator(DECELERATE)
            .start()
    }

    fun attachFocusScale(surface: View, input: View, liftScale: Float = 1.015f) {
        if (prefersReducedMotion(surface.context)) return
        input.setOnFocusChangeListener { _, hasFocus ->
            val target = if (hasFocus) liftScale else 1f
            surface.animate()
                .scaleX(target).scaleY(target)
                .setDuration(150L)
                .setInterpolator(if (hasFocus) EMPHASIZED else DECELERATE)
                .start()
        }
    }

    fun animateTint(icon: ImageView, label: TextView, from: Int, to: Int, duration: Long = 160L) {
        if (prefersReducedMotion(icon.context)) {
            icon.imageTintList = android.content.res.ColorStateList.valueOf(to)
            label.setTextColor(to)
            return
        }
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            this.duration = duration
            interpolator = DECELERATE
            addUpdateListener {
                val c = it.animatedValue as Int
                icon.imageTintList = android.content.res.ColorStateList.valueOf(c)
                label.setTextColor(c)
            }
            start()
        }
    }
}
