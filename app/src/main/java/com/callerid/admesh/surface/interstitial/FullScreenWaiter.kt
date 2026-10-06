package com.callerid.admesh.surface.interstitial

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import com.callerid.number.lookup.home.R

object FullScreenWaiter {

    private var dialog: Dialog? = null

    /** The activity the dialog is attached to; weak, so a lingering dialog cannot keep it alive. */
    private var owner: java.lang.ref.WeakReference<Activity>? = null

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** However a load ends - or never ends - the user is never left behind the loader for longer. */
    private const val MAX_SHOW_MS = 12_000L
    private val autoHide = Runnable {
        Log.w("FullScreenWaiter", "loader still up after ${MAX_SHOW_MS}ms - hidden")
        dismissSafely()
    }

    @Suppress("DEPRECATION")
    fun show(activity: Activity, isLoader: Boolean) {
        if (!isLoader) return

        try {
            if (activity.isFinishing || activity.isDestroyed) return

            dismissSafely()

            dialog = Dialog(activity).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(false)
                setContentView(R.layout.part_fullscreen)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

                window?.setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                )

                window?.decorView?.systemUiVisibility =
                    (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

                show()
                owner = java.lang.ref.WeakReference(activity)
                main.removeCallbacks(autoHide)
                main.postDelayed(autoHide, MAX_SHOW_MS)

                window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }

        } catch (e: Exception) {
            Log.e("FullScreenWaiter", "Error showing loader", e)
        }
    }

    fun hide() {
        dismissSafely()
    }

    private fun dismissSafely() {
        try {
            dialog?.let {
                if (it.isShowing) {
                    // The dialog's own context is a theme wrapper, never the Activity itself.
                    val host = owner?.get()
                    if (host == null || (!host.isFinishing && !host.isDestroyed)) it.dismiss()
                }
            }
        } catch (e: Exception) {

        } finally {
            main.removeCallbacks(autoHide)
            dialog = null
            owner = null
        }
    }
}

