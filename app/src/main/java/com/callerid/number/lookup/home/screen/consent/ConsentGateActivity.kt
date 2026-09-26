package com.callerid.number.lookup.home.screen.consent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.callerid.number.lookup.home.screen.AppHomeActivity
import com.callerid.number.lookup.home.screen.reveal.RevealConfig
import com.callerid.number.lookup.home.onboard.OnboardRouter
import com.callerid.number.lookup.home.screen.reveal.RevealPolicy
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.number.lookup.home.store.StorageRegistry
import com.callerid.number.lookup.home.databinding.ScreenTermsBinding
import com.callerid.number.lookup.home.screen.slides.SlideIntroActivity

class ConsentGateActivity : FrameActivity<ScreenTermsBinding>() {

    override val layoutId: Int = R.layout.screen_terms

    private val prefs by lazy { StorageRegistry(this) }

    private var awaitingOverlay = false
    private var settingsShown = false
    private var proceeded = false
    private var watchRegistered = false

    private val overlayGrantedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            proceedToNextScreen()
        }
    }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {

        proceedToNextScreen() }

    override fun initView() {

        RevealPolicy.markShown(this, RevealConfig.TERMS)

        ViewCompat.setOnApplyWindowInsetsListener(binding.termsRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.chkAgree.isChecked = true
        setAcceptEnabled(true)

        binding.chkAgree.setOnCheckedChangeListener { _, checked -> setAcceptEnabled(checked) }

        binding.lblAgree.setOnClickListener { binding.chkAgree.toggle() }
        binding.padAccept.setOnClickListener { onAccept() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Organic: Back is Back — it never accepts the terms on the user's behalf.
                if (!OnboardRouter.backMovesForward(this@ConsentGateActivity)) {
                    OnboardRouter.passBackThrough(this@ConsentGateActivity, this)
                    return
                }
                prefs.isTermsAccepted = true
                proceedToNextScreen()
            }
        })
    }

    override fun onResume() {
        super.onResume()

        if (awaitingOverlay && settingsShown) {
            awaitingOverlay = false
            settingsShown = false
            proceedToNextScreen()
        }
    }

    override fun onPause() {
        super.onPause()

        if (awaitingOverlay) settingsShown = true
    }

    override fun onDestroy() {
        stopWatch()
        super.onDestroy()
    }

    private fun setAcceptEnabled(enabled: Boolean) {
        binding.padAccept.isEnabled = enabled
        binding.padAccept.alpha = if (enabled) 1f else 0.5f
    }

    private fun onAccept() {
        if (!binding.chkAgree.isChecked) {
            Toast.makeText(this, R.string.terms_please_accept, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.isTermsAccepted = true
        openOverlayPermission()
    }

    private fun openOverlayPermission() {
        
        if (OverlayKit.isGranted(this) || !OverlayKit.isOfferable(this)) {
            proceedToNextScreen()
            return
        }

        val launched = runCatching {
            overlayLauncher.launch(OverlayKit.buildOverlayIntent(packageName))
        }.isSuccess

        if (!launched) {

            proceedToNextScreen()
            return
        }

        awaitingOverlay = true
        settingsShown = false

        ContextCompat.registerReceiver(
            this,
            overlayGrantedReceiver,
            IntentFilter(OverlayWatchService.ACTION_OVERLAY_GRANTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        watchRegistered = true
        OverlayWatchService.start(this)
    }

    private fun proceedToNextScreen() {
        if (proceeded) return
        proceeded = true
        stopWatch()

        val next = if (RevealPolicy.shouldShowOnboarding(this)) SlideIntroActivity::class.java
        else OnboardRouter.homeActivity()
        if (next == OnboardRouter.homeActivity()) {
            OnboardRouter.markOnboardingCompleted(this)
        }
        startActivity(
            Intent(this, next).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
        finish()
    }

    private fun stopWatch() {
        if (watchRegistered) {
            runCatching { unregisterReceiver(overlayGrantedReceiver) }
            watchRegistered = false
        }
        OverlayWatchService.stop(this)
    }
}
