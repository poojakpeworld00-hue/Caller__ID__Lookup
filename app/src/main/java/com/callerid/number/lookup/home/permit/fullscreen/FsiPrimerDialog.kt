package com.callerid.number.lookup.home.permit.fullscreen

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.callerid.admesh.engine.trackEvent
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.permit.PermitEngine
import com.callerid.number.lookup.home.screen.main.HomeShellOwner

object FsiPrimerDialog {

    private var current: Dialog? = null

    fun isShowing(): Boolean = current?.isShowing == true

    fun show(
        activity: Activity,
        config: FsiSettings,
        onFinished: ((enabled: Boolean) -> Unit)? = null,
    ) {
        if (activity.isFinishing || isShowing()) return

        val view = LayoutInflater.from(activity)
            .inflate(R.layout.dlg_fsi_permission, null, false)
        view.findViewById<TextView>(R.id.fsDialogTitleVw).text = config.dialog.title
        view.findViewById<TextView>(R.id.fsDialogDescVw).text = config.dialog.desc
        view.findViewById<TextView>(R.id.fsDialogButtonVw).text = config.dialog.button

        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(view)
            setCancelable(true)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

        var enableTapped = false

        view.findViewById<TextView>(R.id.fsDialogButtonVw).setOnClickListener {
            activity.trackEvent("fsi_dialog_enable")

            enableTapped = true
            dialog.dismiss()
            PermitEngine.request(activity, "notification") {
                (activity as? HomeShellOwner)?.homeShellController?.openFsiSettings()
            }
        }
        view.findViewById<TextView>(R.id.fsDialogLaterVw).setOnClickListener {
            activity.trackEvent("fsi_dialog_not_now")
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            current = null
            onFinished?.invoke(enableTapped)
        }
        current = dialog
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        animateIn(view)
        activity.trackEvent("fsi_dialog_show")
    }

    private fun animateIn(root: View) {
        val dy = 40f * root.resources.displayMetrics.density
        root.alpha = 0f
        root.scaleX = 0.94f
        root.scaleY = 0.94f
        root.translationY = dy
        root.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setInterpolator(OvershootInterpolator(1.2f))
            .setDuration(560)
            .start()

        loopGlow(root.findViewById(R.id.fsDialogGlowVw))
        loopRing(root.findViewById(R.id.fsDialogOrbit1Vw), 0L, 0.85f, 1.7f, 0.55f, 2600L)
        loopRing(root.findViewById(R.id.fsDialogOrbit2Vw), 900L, 0.85f, 1.7f, 0.55f, 2600L)
        loopRing(root.findViewById(R.id.fsAvatarRing1Vw), 0L, 0.9f, 1.4f, 0.7f, 2200L)
        loopRing(root.findViewById(R.id.fsAvatarRing2Vw), 700L, 0.9f, 1.4f, 0.7f, 2200L)
        root.findViewById<View>(R.id.fsDialogButtonVw)?.let { loopCta(it) }
    }

    private fun loopRing(v: View?, delay: Long, from: Float, to: Float, alpha: Float, dur: Long) {
        v ?: return
        v.postDelayed({
            fun cycle() {
                if (!isShowing()) return
                v.scaleX = from; v.scaleY = from; v.alpha = alpha
                v.animate().scaleX(to).scaleY(to).alpha(0f)
                    .setInterpolator(DecelerateInterpolator())
                    .setDuration(dur)
                    .withEndAction { cycle() }
                    .start()
            }
            cycle()
        }, delay)
    }

    private fun loopGlow(v: View?) {
        v ?: return
        if (!isShowing()) return
        v.alpha = 0.5f
        v.animate().alpha(0.85f).setDuration(1300)
            .withEndAction {
                if (!isShowing()) return@withEndAction
                v.animate().alpha(0.5f).setDuration(1300)
                    .withEndAction { loopGlow(v) }
                    .start()
            }.start()
    }

    private fun loopCta(v: View) {
        if (!isShowing()) return
        v.animate().scaleX(1.02f).scaleY(1.05f).setDuration(1300)
            .withEndAction {
                if (!isShowing()) return@withEndAction
                v.animate().scaleX(1f).scaleY(1f).setDuration(1300)
                    .withEndAction { loopCta(v) }
                    .start()
            }.start()
    }

    fun dismissIfShowing() {
        runCatching { current?.dismiss() }
        current = null
    }
}
