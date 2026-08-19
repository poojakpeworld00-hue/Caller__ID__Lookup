package com.callerid.number.lookup.home.screen.boot

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.lighthouse.push.LightHouse
import io.lighthouse.push.extended.LightHouseRichPush
import com.callerid.admesh.model.OnFeedReady
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.OpenPromoRegistry
import com.callerid.admesh.surface.showAppRedirectPopup
import com.callerid.number.lookup.home.screen.AppHomeActivity
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.number.lookup.home.store.StorageRegistry
import com.callerid.number.lookup.home.databinding.ScreenSplashBinding
import com.callerid.number.lookup.home.shell.screens.HelloStepActivity
import com.callerid.number.lookup.home.shell.support.OnboardRouter
import com.callerid.number.lookup.home.screen.reveal.RevealPolicy
import com.callerid.number.lookup.home.screen.locale.LanguageSelectActivity
import com.callerid.number.lookup.home.screen.slides.SlideIntroActivity
import com.callerid.number.lookup.home.screen.consent.ConsentGateActivity
import com.callerid.number.lookup.home.kit.openActivity
import java.security.MessageDigest

class LaunchGateActivity : FrameActivity<ScreenSplashBinding>() {

    override val layoutId: Int = R.layout.screen_splash
    private val handler = Handler(Looper.getMainLooper())

    private val splashAnimators = mutableListOf<Animator>()

    private val prefs by lazy { StorageRegistry(this) }

    private val proceeded = java.util.concurrent.atomic.AtomicBoolean(false)

    private val dataReady = java.util.concurrent.atomic.AtomicBoolean(false)
    private val animMinElapsed = java.util.concurrent.atomic.AtomicBoolean(false)

    private companion object {

        const val SPLASH_FLOW_TAG = "SplashFlow"

        const val ANIM_MIN_MS = 3600L

        const val ANIM_MIN_REDUCED_MS = 700L

        const val WATCHDOG_TIMEOUT_MS = 20_000L
    }

    override fun initView() {

        if (LightHouseRichPush.handleFromSplash(this, binding.root)) return

        prefs.appLaunchCount = prefs.appLaunchCount + 1

        window.statusBarColor = ContextCompat.getColor(this, R.color.splash_grad_start)
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.splashRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.lblSecureVersion.text =
            getString(R.string.splash_secure_version, BuildConfig.VERSION_NAME)

        setupSplashAnimation()
        val animMin = if (animationsDisabled()) ANIM_MIN_REDUCED_MS else ANIM_MIN_MS
        handler.postDelayed({
            Log.d(SPLASH_FLOW_TAG, "animation min elapsed (${animMin}ms) → maybeProceed")
            animMinElapsed.set(true)
            maybeProceed()
        }, animMin)

        printHashKey(this)
        Log.d(SPLASH_FLOW_TAG, "SplashActivity.initView → getData()")

        getData(this, true, object : OnFeedReady {
            override fun onSuccess() {
                Log.d(SPLASH_FLOW_TAG, "getData onSuccess → dataReady")
                OpenPromoRegistry.loadAd(this@LaunchGateActivity)
                dataReady.set(true)
                maybeProceed()
            }

            override fun onError() {
                Log.d(SPLASH_FLOW_TAG, "getData onError → dataReady")
                dataReady.set(true)
                maybeProceed()
            }
        })

    }

    private fun maybeProceed() {
        if (dataReady.get() && animMinElapsed.get()) {
            Log.d(SPLASH_FLOW_TAG, "both gates met (data + animation) → proceedNow")
            proceedNow()
        }
    }

    private fun proceedNow() {

        if (!proceeded.compareAndSet(false, true)) {
            Log.d(SPLASH_FLOW_TAG, "proceedNow ignored (already proceeded)")
            return
        }
        handler.removeCallbacksAndMessages(null)
        if (isFinishing || isDestroyed) {
            Log.d(SPLASH_FLOW_TAG, "proceedNow skipped (finishing/destroyed)")
            return
        }
        val redirectLink = PromoVault.getInstance(this).getString("In_App_Update_Link")
        if (!redirectLink.isNullOrEmpty()) {
            Log.d(SPLASH_FLOW_TAG, "proceedNow → showAppRedirectPopup")

            showAppRedirectPopup { launchNext() }
        } else {
            Log.d(SPLASH_FLOW_TAG, "proceedNow → launchNext")
            launchNext()
        }
    }

    private fun launchNext() {
        if (isFinishing || isDestroyed) {
            Log.d(SPLASH_FLOW_TAG, "launchNext skipped (finishing/destroyed)")
            return
        }
        Log.d(SPLASH_FLOW_TAG, "launchNext → ensureDataDisclosure")

        OpenPromoRegistry.skipNextAppOpenAd = true
        LightHouse.ensureDataDisclosure(this) {
            if (isFinishing || isDestroyed) return@ensureDataDisclosure
            LightHouse.subscribeAsync()

            val launcherOnboarding = !OnboardRouter.wasOnboardingCompleted(this)
            val next = nextScreen()
            Log.d(SPLASH_FLOW_TAG, "ensureDataDisclosure done → launching ${next.simpleName}")

            val intent = if (launcherOnboarding && next != OnboardRouter.homeActivity()) {
                OnboardRouter.onboardingIntent(this, next)
            } else {
                Intent(this, next)
            }

            openActivity(intent, isShowAd = false)
            finish()
        }
    }

    private val orbitOffsets by lazy {
        listOf(
            binding.incomingWrapVw to (dp(-52f) to dp(-92f)),
            binding.fraudWrapVw to (dp(-52f) to dp(92f)),
            binding.avatarWrapVw to (dp(96f) to dp(24f)),
        )
    }

    private fun alive() = !isFinishing && !isDestroyed

    private fun animationsDisabled() = Settings.Global.getFloat(
        contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) == 0f

    private fun setupSplashAnimation() {

        orbitOffsets.forEach { (v, off) ->
            v.translationX = off.first
            v.translationY = off.second
        }

        if (animationsDisabled()) {

            listOf(
                binding.compositionVw, binding.iconTileVw, binding.flagIncoming,
                binding.flagFraud, binding.avatarInnerVw, binding.lblAppName,
                binding.lblTagline, binding.progTrack, binding.footerVw
            ).forEach { it.alpha = 1f }
            binding.scanRingVw.alpha = 0.9f
            binding.progTrack.post {
                if (alive()) setFillWidth((binding.progTrack.width * 0.94f).toInt())
            }
            return
        }

        start(fadeIn(binding.compositionVw, delay = 0L, dur = 460L))
        binding.iconTileVw.scaleX = 0.8f
        binding.iconTileVw.scaleY = 0.8f
        start(fadeScaleIn(binding.iconTileVw, delay = 0L, dur = 570L))
        handler.postDelayed({ if (alive()) start(breathe(binding.iconTileVw, delay = 0L)) }, 620L)
        handler.postDelayed({ if (alive()) start(orbitBreathe(binding.orbitRingVw)) }, 800L)

        start(riseIn(binding.lblAppName, delay = 250L, dyDp = 14f))
        start(riseIn(binding.lblTagline, delay = 330L, dyDp = 14f))
        start(fadeIn(binding.footerVw, delay = 300L, dur = 520L))
        start(fadeIn(binding.progTrack, delay = 300L, dur = 520L))
        startProgressFill(delay = 340L)

        popIn(binding.flagIncoming, delay = 630L)
        handler.postDelayed({ if (alive()) startScanSpin() }, 880L)
        popIn(binding.flagFraud, delay = 2140L)
        popIn(binding.avatarInnerVw, delay = 2650L)

        floatLoop(binding.flagIncoming, delay = 1400L, dyDp = 5f, dur = 3000L)
        floatLoop(binding.flagFraud, delay = 2700L, dyDp = -5f, dur = 3400L)
        floatLoop(binding.avatarInnerVw, delay = 3200L, dyDp = 5f, dur = 3800L)
    }

    private fun start(anim: Animator) {
        splashAnimators += anim
        anim.start()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun fadeScaleIn(v: View, delay: Long, dur: Long) = AnimatorSet().apply {
        startDelay = delay
        duration = dur
        interpolator = OvershootInterpolator(1.4f)
        playTogether(
            ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f),
            ObjectAnimator.ofFloat(v, View.SCALE_X, 0.8f, 1f),
            ObjectAnimator.ofFloat(v, View.SCALE_Y, 0.8f, 1f),
        )
    }

    private fun fadeIn(v: View, delay: Long, dur: Long) =
        ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f).apply {
            startDelay = delay
            duration = dur
        }

    private fun riseIn(v: View, delay: Long, dyDp: Float) = AnimatorSet().apply {
        startDelay = delay
        duration = 420L
        interpolator = OvershootInterpolator(1.1f)
        playTogether(
            ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f),
            ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, dp(dyDp), 0f),
        )
    }

    private fun breathe(v: View, delay: Long) = ValueAnimator.ofFloat(1f, 1.08f).apply {
        startDelay = delay
        duration = 1600L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        addUpdateListener {
            val s = it.animatedValue as Float
            v.scaleX = s
            v.scaleY = s
        }
    }

    private fun popIn(v: View, delay: Long) {
        v.scaleX = 0.4f
        v.scaleY = 0.4f
        v.alpha = 0f
        start(AnimatorSet().apply {
            startDelay = delay
            duration = 460L
            interpolator = OvershootInterpolator(2.2f)
            playTogether(
                ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(v, View.SCALE_X, 0.4f, 1f),
                ObjectAnimator.ofFloat(v, View.SCALE_Y, 0.4f, 1f),
            )
        })
    }

    private fun orbitBreathe(v: View) = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 5000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        addUpdateListener {
            val p = it.animatedValue as Float
            v.scaleX = 1f + 0.05f * p
            v.scaleY = 1f + 0.05f * p
            v.alpha = 0.85f - 0.35f * p
        }
    }

    private fun startScanSpin() {
        binding.scanRingVw.alpha = 0f
        start(ObjectAnimator.ofFloat(binding.scanRingVw, View.ALPHA, 0f, 1f).apply { duration = 420L })
        start(ObjectAnimator.ofFloat(binding.scanRingVw, View.ROTATION, 0f, 360f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        })
    }

    private fun floatLoop(v: View, delay: Long, dyDp: Float, dur: Long) {
        start(ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, dp(dyDp), 0f).apply {
            startDelay = delay
            duration = dur
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        })
    }

    private fun setFillWidth(px: Int) {
        val lp = binding.progFill.layoutParams
        lp.width = px
        binding.progFill.layoutParams = lp
    }

    private fun startProgressFill(delay: Long) {
        binding.progTrack.post {
            if (!alive()) return@post
            val track = binding.progTrack.width
            if (track <= 0) return@post
            val from = binding.progFill.width.coerceAtLeast(dp(8f).toInt())
            val to = (track * 0.94f).toInt()
            start(ValueAnimator.ofInt(from, to).apply {
                startDelay = delay
                duration = 4200L
                interpolator = DecelerateInterpolator()
                addUpdateListener { setFillWidth(it.animatedValue as Int) }
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        splashAnimators.forEach { it.cancel() }
        splashAnimators.clear()
        handler.removeCallbacksAndMessages(null)
    }

    private fun nextScreen(): Class<*> = when {

        !OnboardRouter.wasOnboardingCompleted(this) -> OnboardRouter.firstScreen(this)
        RevealPolicy.shouldShowLanguage(this) -> LanguageSelectActivity::class.java
        RevealPolicy.shouldShowTerms(this) -> ConsentGateActivity::class.java
        RevealPolicy.shouldShowOnboarding(this) -> SlideIntroActivity::class.java
        else -> OnboardRouter.homeActivity()
    }
    private fun route() {
        val next = when {
            !prefs.isLanguageSelected -> LanguageSelectActivity::class.java
            !prefs.isTermsAccepted -> ConsentGateActivity::class.java
            !prefs.isOnboardingDone -> SlideIntroActivity::class.java
            else -> AppHomeActivity::class.java
        }
        startActivity(Intent(this, next))
        finish()
    }

    @Suppress("DEPRECATION", "PackageManagerGetSignatures")
    private fun printHashKey(context: Context) {
        try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                info.signingInfo?.apkContentsSigners
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            } ?: return

            signatures.forEach { sig ->
                val md = MessageDigest.getInstance("SHA")
                md.update(sig.toByteArray())
                Log.d("KeyHash", Base64.encodeToString(md.digest(), Base64.DEFAULT))
            }
        } catch (e: Exception) {
        }
    }
}
