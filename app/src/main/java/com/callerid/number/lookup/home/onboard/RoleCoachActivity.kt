package com.callerid.number.lookup.home.onboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.callerid.admesh.surface.TipSheetActivity
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.kit.LogRail

class RoleCoachActivity : AppCompatActivity() {

    private val autoFinish = Runnable { if (!isFinishing) finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A dedicated centred + dimmed layout, not the shared bottom-sheet guide: this hint sits
        // in the middle of the system "Default home app" page with that page dimmed behind it.
        setContentView(R.layout.screen_home_hint)
        LogRail.log(TAG, "hint shown over the home-app list")

        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        val root = findViewById<android.view.View>(R.id.rowMain)
        TipSheetActivity.applyMode(root, TipSheetActivity.MODE_HOME)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        runCatching {
            findViewById<ImageView>(R.id.guideRowIconIvVw)
                ?.setImageDrawable(packageManager.getApplicationIcon(applicationInfo))
            findViewById<TextView>(R.id.guideRowNameTvVw)?.text =
                packageManager.getApplicationLabel(applicationInfo)
        }

        bindDismissOnTouch(root)
        startTapAnimation()

        visible = this
        root.postDelayed(autoFinish, AUTO_FINISH_MS)
    }

    /**
     * The pointing-hand tap and the radio's pulse, in phase, so the row reads as "tap this one".
     * Both views are the layout's — null-safe so a restyle that drops either one cannot crash it.
     */
    private fun startTapAnimation() {
        findViewById<View>(R.id.hintHand)
            ?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.hand_tap_loop))
        findViewById<View>(R.id.hintRadio)
            ?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.tap_ring_pulse))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindDismissOnTouch(root: android.view.View) {
        root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !isFinishing) finish()
            true
        }
    }

    override fun onDestroy() {
        findViewById<android.view.View>(R.id.rowMain)?.removeCallbacks(autoFinish)
        if (visible === this) visible = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DefaultHomeHint"

        /** Long enough to find one row in a list of launchers — see TipSheetWindow.AUTO_DISMISS_MS. */
        private const val AUTO_FINISH_MS = 12_000L

        private var visible: RoleCoachActivity? = null

        fun intent(context: Context): Intent =
            Intent(context, RoleCoachActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun dismiss() {
            visible?.takeUnless { it.isFinishing }?.finish()
            visible = null
        }
    }
}
