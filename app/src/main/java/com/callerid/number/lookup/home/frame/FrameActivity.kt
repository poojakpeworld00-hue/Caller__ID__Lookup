package com.callerid.number.lookup.home.frame

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.callerid.admesh.engine.PerScreenPromo
import com.callerid.number.lookup.home.R
import com.callerid.admesh.engine.trackEvent
import com.callerid.admesh.engine.logPermissionResult
import com.callerid.admesh.surface.PromoAnchorActivity
import com.callerid.admesh.surface.interstitial.BackInterstitial
import com.callerid.admesh.surface.interstitial.FlowInterstitial
import com.callerid.number.lookup.home.store.LanguageRegistry
import com.callerid.number.lookup.home.store.StorageRegistry
import com.callerid.number.lookup.home.kit.AppPrefs
import com.callerid.number.lookup.home.kit.applyNativeAdTheme
import com.callerid.number.lookup.home.kit.followAdContainer
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_DARK
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_LIGHT
import com.callerid.number.lookup.home.kit.AppPrefs.THEME_SYSTEM
import java.util.Locale
import kotlin.sequences.ifEmpty

abstract class FrameActivity<DB : ViewDataBinding> : PromoAnchorActivity() {

    protected lateinit var binding: DB
        private set

    @get:LayoutRes
    protected abstract val layoutId: Int

    override fun onCreate(savedInstanceState: Bundle?) {
        applyLocale()
        applyTheme(AppPrefs.selectedTheme(this).ifEmpty { THEME_LIGHT })
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, layoutId)

        trackEvent("screen_${this::class.java.simpleName.lowercase(Locale.ROOT)}")
        binding.lifecycleOwner = this

        applyNativeAdTheme()

        onBackPressedDispatcher.addCallback(this, backAdCallback)

        initView()
        initObservers()

        showBottomBanner()
    }

    private val backAdCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = goBack()
    }

    protected fun goBack() {
        BackInterstitial().renderBackInterstitial(this) { performBack() }
    }

    protected open fun performBack() {
        if (!isFinishing) finish()
    }

    protected open fun showBottomBanner() {
        val container = binding.root.findViewById<FrameLayout>(R.id.bannerAdFrameVw) ?: return
        val shimmer = binding.root.findViewById<ShimmerFrameLayout>(R.id.bannerShimmerVw)
        PerScreenPromo.renderAd(this::class.java.simpleName, this, container, shimmer)

        binding.root.findViewById<View>(R.id.adBannerDividerVw)?.followAdContainer(container)
    }

    protected open fun initView() {}

    protected open fun initObservers() {}

    protected fun requestPermissionManaged(
        permission: String,
        launcher: ActivityResultLauncher<String>
    ) {
        val prefs = StorageRegistry(this)
        when {
            !prefs.hasRequestedPermission(permission) -> {
                prefs.markPermissionRequested(permission)
                launcher.launch(permission)
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission) ->
                launcher.launch(permission)
            else -> openAppSettings()
        }
    }

    protected fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }
    }

    private var pendingCallNumber: String? = null

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        logPermissionResult(Manifest.permission.CALL_PHONE, granted)
        val number = pendingCallNumber
        pendingCallNumber = null
        if (number != null) if (granted) startCall(number) else openDialer(number)
    }

    protected fun placeCall(number: String) {
        if (number.isBlank()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCall(number)
        } else {
            pendingCallNumber = number
            requestPermissionManaged(Manifest.permission.CALL_PHONE, callPermissionLauncher)
        }
    }

    private fun startCall(number: String) {
        val placed = runCatching {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))); true
        }.getOrDefault(false)
        if (!placed) openDialer(number)
    }

    private fun openDialer(number: String) {
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
    }

    override fun onResume() {
        super.onResume()
        applyLocale()
        FlowInterstitial.handleTabReturn(this)

    }

    protected fun applyLocale() = LanguageRegistry.applySaved(this)

    protected open fun applyTheme(theme: String) {
        when (theme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
