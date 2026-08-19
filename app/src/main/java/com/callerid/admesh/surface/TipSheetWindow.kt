package com.callerid.admesh.surface

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.kit.LogRail

object TipSheetWindow {

    private const val AUTO_DISMISS_MS = 3_000L
    private const val POLL_MS = 500L

    private val main = Handler(Looper.getMainLooper())
    private var shown: View? = null

    fun canOverlay(context: Context): Boolean = Settings.canDrawOverlays(context.applicationContext)

    fun show(context: Context, mode: String, delayMs: Long = 0L): Boolean {
        if (delayMs > 0L) {
            val app = context.applicationContext
            if (!Settings.canDrawOverlays(app)) return false
            main.postDelayed({ show(app, mode) }, delayMs)
            return true
        }
        return showNow(context, mode)
    }

    private fun showNow(context: Context, mode: String): Boolean {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) {

            LogRail.log("OverlayGuide", "no SYSTEM_ALERT_WINDOW → cannot draw over Settings ($mode)")
            return false
        }
        LogRail.log("OverlayGuide", "drawing guide as an overlay window ($mode)")

        return runCatching {
            dismiss()

            val inflated = LayoutInflater.from(app).inflate(R.layout.screen_overlay_guide, null)
            val card = inflated.findViewById<View>(R.id.overlayCardVwVw)
            (card.parent as? ViewGroup)?.removeView(card)
            TipSheetActivity.applyMode(card, mode)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.BOTTOM }

            val windowManager = app.getSystemService(WindowManager::class.java)
            windowManager.addView(card, params)
            shown = card

            ViewCompat.setOnApplyWindowInsetsListener(card) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(bars.left, v.paddingTop, bars.right, bars.bottom)
                insets
            }
            ViewCompat.requestApplyInsets(card)

            card.alpha = 0f
            card.post {
                card.translationY = card.height.toFloat()
                card.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(280L)
                    .setInterpolator(DecelerateInterpolator(1.6f))
                    .start()
            }
            card.setOnClickListener { dismiss() }

            main.postDelayed({ dismiss() }, AUTO_DISMISS_MS)
            poll(app, mode)
            true
        }.getOrElse {
            LogRail.error("OverlayGuide", "overlay window failed ($mode) — falling back", it)
            shown = null
            false
        }
    }

    private fun poll(context: Context, mode: String) {
        main.postDelayed(object : Runnable {
            override fun run() {
                if (shown == null) return
                if (TipSheetActivity.satisfied(context, mode)) dismiss() else main.postDelayed(this, POLL_MS)
            }
        }, POLL_MS)
    }

    fun dismiss() {
        val card = shown ?: return
        shown = null
        main.removeCallbacksAndMessages(null)
        runCatching {
            val windowManager = card.context.applicationContext
                .getSystemService(WindowManager::class.java)
            windowManager.removeView(card)
        }.onFailure { LogRail.error("OverlayGuide", "overlay window removal failed", it) }
    }
}
