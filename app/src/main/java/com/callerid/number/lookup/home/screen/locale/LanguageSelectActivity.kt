package com.callerid.number.lookup.home.screen.locale

import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.number.lookup.home.store.RegionResolver
import com.callerid.number.lookup.home.store.LanguageRegistry
import com.callerid.number.lookup.home.store.StorageRegistry
import com.callerid.number.lookup.home.databinding.ScreenLanguageBinding
import com.callerid.number.lookup.home.shell.support.OnboardRouter
import com.callerid.number.lookup.home.permit.PermitEngine
import com.callerid.number.lookup.home.permit.fullscreen.FsiPermit
import com.callerid.number.lookup.home.permit.fullscreen.FsiGateActivity
import com.callerid.number.lookup.home.screen.AppHomeActivity
import com.callerid.number.lookup.home.screen.reveal.RevealConfig
import com.callerid.number.lookup.home.screen.reveal.RevealPolicy
import com.callerid.number.lookup.home.screen.slides.SlideIntroActivity
import com.callerid.number.lookup.home.screen.consent.ConsentGateActivity
import com.callerid.number.lookup.home.kit.AppPrefs
import com.callerid.number.lookup.home.kit.LogRail
import kotlinx.coroutines.launch
import com.callerid.number.lookup.home.kit.followAdContainer

class LanguageSelectActivity : FrameActivity<ScreenLanguageBinding>() {

    override val layoutId: Int = R.layout.screen_language

    private val viewModel: LanguageViewModel by viewModels()
    private val prefs by lazy { StorageRegistry(this) }

    private val standalone by lazy { intent.getBooleanExtra(EXTRA_STANDALONE, false) }

    private lateinit var suggestedAdapter: LanguageAdapter
    private lateinit var allAdapter: LanguageAdapter

    private var forwarding = false

    private var navigated = false

    override fun initView() {

        if (!standalone) RevealPolicy.markShown(this, RevealConfig.LANGUAGE)

        ViewCompat.setOnApplyWindowInsetsListener(binding.languageRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val current = AppPrefs.language(this) ?: AppPrefs.LANGUAGE_DEFAULT
        viewModel.init(current)

        ShellPromoConfig.renderSlot(
            activity = this,
            slot = ShellPromoConfig.onboardSlot(this, ShellPromoConfig.OnboardScreen.LANGUAGE),
            container = binding.adNativeFrameVw,
            shimmer = binding.adShimmerVw,
        )
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)

        if (!standalone) {
            OnboardRouter.bindStepHeader(
                this, ShellPromoConfig.OnboardScreen.LANGUAGE, binding.root
            )
        }

        resolveRegion()

        val onPick: (LanguageItem) -> Unit = { viewModel.select(it.tag) }
        suggestedAdapter = LanguageAdapter(onPick).apply { setCurrent(current) }
        allAdapter = LanguageAdapter(onPick).apply { setCurrent(current) }

        binding.rollSuggested.layoutManager = LinearLayoutManager(this)
        binding.rollSuggested.adapter = suggestedAdapter

        binding.rollLanguages.layoutManager = LinearLayoutManager(this)
        binding.rollLanguages.adapter = allAdapter

        binding.padBack.setOnClickListener { goBack() }
        binding.padInfo.setOnClickListener { showInfoDialog() }
        binding.padContinue.setOnClickListener { onContinue() }

        if (!standalone) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (forwarding) return
                    forwarding = true
                    onContinue()
                }
            })
        }
    }

    override fun initObservers() {
        viewModel.selectedTag.observe(this) { tag ->
            suggestedAdapter.setSelected(tag)
            allAdapter.setSelected(tag)
            popConfirm()
        }

        viewModel.suggested.observe(this) { suggestedAdapter.submitList(it) }
        viewModel.others.observe(this) { allAdapter.submitList(it) }
    }

    private fun resolveRegion() {
        val device = deviceCountry()
        LogRail.log(TAG, "resolveRegion: device=$device (sync seed)")
        viewModel.useCountry(device)
        detectCountryByIp()
    }

    private fun deviceCountry(): String? {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val sim = tm?.simCountryIso?.takeIf { it.isNotBlank() }
        val network = tm?.networkCountryIso?.takeIf { it.isNotBlank() }
        val locale = resources.configuration.locales[0].country.takeIf { it.isNotBlank() }
        return (sim ?: network ?: locale)?.uppercase()
    }

    private fun detectCountryByIp() {
        lifecycleScope.launch {
            val geo = RegionResolver.detectCountry(this@LanguageSelectActivity) ?: run {
                LogRail.log(TAG, "IP geo unavailable → keeping device seed")
                return@launch
            }
            LogRail.log(TAG, "IP refine → country=${geo.iso}")
            viewModel.useCountry(geo.iso)
        }
    }

    private fun popConfirm() {
        binding.padContinue.animate().cancel()
        binding.padContinue.scaleX = 0.8f
        binding.padContinue.scaleY = 0.8f
        binding.padContinue.animate()
            .scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(3f))
            .setDuration(260L)
            .start()
    }

    private fun showInfoDialog() {
        val view =
            layoutInflater.inflate(R.layout.dlg_language_info, binding.languageRootVw, false)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<View>(R.id.padGotIt).setOnClickListener { dialog.dismiss() }

        dialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.85f).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun onContinue() {
        val tag = viewModel.selectedTag.value ?: AppPrefs.LANGUAGE_DEFAULT
        AppPrefs.setLanguage(this, tag)
        prefs.isLanguageSelected = true

        if (standalone) {
            LanguageRegistry.apply(tag)
            finish()
            return
        }

        PermitEngine.check(this) {

            val next = when {

                !standalone && OnboardRouter.isOnboardingActive(this) ->
                    OnboardRouter.nextActivity(this)
                RevealPolicy.shouldShowTerms(this) -> ConsentGateActivity::class.java
                RevealPolicy.shouldShowOnboarding(this) -> SlideIntroActivity::class.java
                else -> OnboardRouter.homeActivity()
            }

            if (next == OnboardRouter.homeActivity()) {
                OnboardRouter.markOnboardingCompleted(this)
            }

            val intent = when {
                FsiPermit.shouldShowScreen(this) ->
                    FsiGateActivity.newIntent(this, next)

                !standalone && OnboardRouter.isOnboardingActive(this) &&
                        next != OnboardRouter.homeActivity() ->
                    OnboardRouter.onboardingIntent(this, next)

                else -> Intent(this, next)
            }

            ShellPromoConfig.runOnboardInterstitial(this, ShellPromoConfig.OnboardScreen.LANGUAGE) {
                if (navigated) {
                    return@runOnboardInterstitial
                }
                navigated = true

                startActivity(intent)
                if (tag != AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
                    LanguageRegistry.apply(tag)
                }
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "LanguageSelectActivity"
        private const val EXTRA_STANDALONE = "extra_standalone"

        fun newIntent(context: Context, standalone: Boolean = false): Intent =
            Intent(context, LanguageSelectActivity::class.java)
                .putExtra(EXTRA_STANDALONE, standalone)
    }
}
