package com.callerid.number.lookup.home.permit.fullscreen

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.facebook.shimmer.ShimmerFrameLayout
import com.callerid.admesh.engine.trackEvent
import com.callerid.admesh.surface.InlinePromo
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.permit.PermitEngine
import com.callerid.number.lookup.home.screen.AppHomeActivity
import com.callerid.number.lookup.home.kit.LogRail
import com.callerid.number.lookup.home.kit.followAdContainer

class FsiGateActivity : AppCompatActivity() {

    private val config by lazy { FsiSettings.load(this) }
    private val returnWatcher by lazy { FsiReturnGuard(this) }

    private val fsiSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {

        continueIfBackFromSettings("settingsResult")
    }

    private var navigated = false

    private val grantPollHandler = Handler(Looper.getMainLooper())
    private var grantPolling = false
    private val grantPoll = object : Runnable {
        override fun run() {
            if (navigated || isDestroyed) { grantPolling = false; return }
            if (FsiPermit.isGranted(this@FsiGateActivity)) {
                LogRail.log("FSI", "grant poll: GRANTED → continue to next")
                grantPolling = false
                trackEvent("fsi_screen_granted")
                continueToNext()
            } else {
                grantPollHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    private fun startGrantPoll() {
        if (grantPolling) return
        grantPolling = true
        grantPollHandler.removeCallbacks(grantPoll)
        grantPollHandler.postDelayed(grantPoll, POLL_INTERVAL_MS)
    }

    private fun stopGrantPoll() {
        grantPolling = false
        grantPollHandler.removeCallbacks(grantPoll)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawableResource(R.color.fsi_bg_edge)

        if (returningFromSettings || FsiPermit.isGranted(this)) {
            LogRail.log("FSI", "Screen onCreate: returning/granted → continue (no render)")
            continueToNext()
            return
        }

        setContentView(R.layout.screen_fsi_permission)
        setupSystemBars()

        findViewById<TextView>(R.id.fsScreenTitleVw).text = config.screen.title
        findViewById<TextView>(R.id.fsScreenDescVw).text = config.screen.desc
        findViewById<TextView>(R.id.fsScreenButtonVw).text = config.screen.button

        FsiPermit.markScreenShown(this)
        trackEvent("fsi_screen_show")
        returnWatcher.register()

        findViewById<TextView>(R.id.fsScreenButtonVw).setOnClickListener {

            PermitEngine.request(this, "notification") {
                trackEvent("fsi_screen_enable")

                pendingFsiSettings = true
                pendingNext = intent.getStringExtra(EXTRA_NEXT)
                findViewById<View>(R.id.fsScreenRootVw).visibility = View.INVISIBLE
                FsiPermit.openSettings(this, fsiSettingsLauncher)

                startGrantPoll()
            }
        }
        findViewById<TextView>(R.id.fsScreenSkipVw).setOnClickListener {
            trackEvent("fsi_screen_skip")
            continueToNext()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                trackEvent("fsi_screen_skip")
                continueToNext()
            }
        })

        playIntroAnimation()

        val adFrame = findViewById<FrameLayout>(R.id.adNativeFrameVw)
        InlinePromo().renderMidNative(
            this,
            adFrame,
            findViewById<ShimmerFrameLayout>(R.id.adShimmerVw),
        )
        findViewById<View>(R.id.adNativeDividerVw).followAdContainer(adFrame)
    }

    private fun setupSystemBars() {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val barColor = ContextCompat.getColor(this, R.color.fsi_bg_edge)
        @Suppress("DEPRECATION")
        window.statusBarColor = barColor
        @Suppress("DEPRECATION")
        window.navigationBarColor = barColor
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }

        val root = findViewById<View>(R.id.fsScreenRootVw)
        val basePaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, basePaddingBottom + bars.bottom)
            insets
        }
    }

    private fun playIntroAnimation() {
        val hero = findViewById<View>(R.id.fsHeroVw)
        val glow = findViewById<View>(R.id.fsGlowVw)
        val preview = findViewById<View>(R.id.fsCallPreviewVw)
        val cta = findViewById<View>(R.id.fsScreenButtonVw)

        preview.alpha = 0f
        preview.scaleX = 0.9f
        preview.scaleY = 0.9f
        preview.translationY = dp(18f)
        preview.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setStartDelay(60)
            .setInterpolator(OvershootInterpolator(1.3f))
            .setDuration(620)
            .start()

        loopFloat(hero)
        loopGlow(glow)
        loopRing(findViewById(R.id.fsOrbit1Vw), 0L, 0.85f, 1.75f, 0.6f, 2600L)
        loopRing(findViewById(R.id.fsOrbit2Vw), 900L, 0.85f, 1.75f, 0.6f, 2600L)
        loopRing(findViewById(R.id.fsAvatarRing1Vw), 300L, 0.9f, 1.4f, 0.7f, 2200L)
        loopRing(findViewById(R.id.fsAvatarRing2Vw), 1000L, 0.9f, 1.4f, 0.7f, 2200L)
        loopCta(cta)

        listOf(
            findViewById<View>(R.id.fsScreenTitleVw),
            findViewById<View>(R.id.fsScreenDescVw),
            findViewById<View>(R.id.fsScreenFeaturesVw),
        ).forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = dp(20f)
            v.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(360L + i * 90L)
                .setDuration(440)
                .start()
        }
    }

    private fun loopFloat(v: View) {
        if (isFinishing || isDestroyed) return
        v.animate().translationY(-dp(8f))
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .setDuration(2000)
            .withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                v.animate().translationY(0f)
                    .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                    .setDuration(2000)
                    .withEndAction { loopFloat(v) }
                    .start()
            }.start()
    }

    private fun loopGlow(v: View) {
        if (isFinishing || isDestroyed) return
        v.alpha = 0.55f
        v.animate().alpha(0.9f).setDuration(1300)
            .withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                v.animate().alpha(0.55f).setDuration(1300)
                    .withEndAction { loopGlow(v) }.start()
            }.start()
    }

    private fun loopRing(v: View?, delay: Long, from: Float, to: Float, alpha: Float, dur: Long) {
        v ?: return
        v.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            fun cycle() {
                if (isFinishing || isDestroyed) return
                v.scaleX = from; v.scaleY = from; v.alpha = alpha
                v.animate().scaleX(to).scaleY(to).alpha(0f)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .setDuration(dur)
                    .withEndAction { cycle() }
                    .start()
            }
            cycle()
        }, delay)
    }

    private fun loopCta(v: View) {
        if (isFinishing || isDestroyed) return
        v.animate().scaleX(1.015f).scaleY(1.03f).setDuration(1300)
            .withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                v.animate().scaleX(1f).scaleY(1f).setDuration(1300)
                    .withEndAction { loopCta(v) }.start()
            }.start()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onResume() {
        super.onResume()
        FsiPermit.stopWatch(this)
        continueIfBackFromSettings("onResume")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        continueIfBackFromSettings("onNewIntent")
    }

    private fun continueIfBackFromSettings(where: String) {
        if (navigated) return
        val granted = FsiPermit.isGranted(this)
        if (returningFromSettings || granted) {
            LogRail.log("FSI", "Screen $where: back from settings, granted=$granted → continue")
            if (granted) trackEvent("fsi_screen_granted")
            continueToNext()
        }
    }

    override fun onPause() {
        super.onPause()

        if (pendingFsiSettings) {
            pendingFsiSettings = false
            returningFromSettings = true
        }
    }

    override fun onDestroy() {
        stopGrantPoll()
        returnWatcher.unregister()
        FsiPermit.stopWatch(this)
        super.onDestroy()
    }

    private fun continueToNext() {
        if (navigated) return
        navigated = true
        stopGrantPoll()
        returningFromSettings = false

        val nextName = intent.getStringExtra(EXTRA_NEXT) ?: pendingNext
        pendingNext = null
        val nextClass = nextName
            ?.let { runCatching { Class.forName(it) }.getOrNull() }
            ?: AppHomeActivity::class.java
        LogRail.log("FSI", "Screen continueToNext → ${nextClass.simpleName}")

        startActivity(
            Intent(this, nextClass).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
        finish()
    }

    companion object {
        private const val EXTRA_NEXT = "extra_fsi_next"

        private const val POLL_INTERVAL_MS = 350L

        @Volatile
        private var returningFromSettings = false

        @Volatile
        private var pendingFsiSettings = false

        @Volatile
        private var pendingNext: String? = null

        fun newIntent(context: Context, next: Class<*>): Intent =
            Intent(context, FsiGateActivity::class.java)
                .putExtra(EXTRA_NEXT, next.name)
    }
}
