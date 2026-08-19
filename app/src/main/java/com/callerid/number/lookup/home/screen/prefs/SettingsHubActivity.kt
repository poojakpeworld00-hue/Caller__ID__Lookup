package com.callerid.number.lookup.home.screen.prefs

import android.app.role.RoleManager
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import android.view.View
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.OpenPromoRegistry
import com.callerid.admesh.surface.InlinePromo
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.number.lookup.home.store.StorageRegistry
import com.callerid.number.lookup.home.databinding.ScreenSettingsBinding
import com.callerid.number.lookup.home.databinding.CellPrefCardBinding
import com.callerid.number.lookup.home.databinding.CellSettingRowBinding
import com.callerid.number.lookup.home.screen.blocking.BlockCenterActivity
import com.callerid.number.lookup.home.screen.shared.CoachBubble
import com.callerid.number.lookup.home.screen.locale.LanguageSelectActivity
import com.callerid.number.lookup.home.screen.locale.LanguageCatalog
import com.callerid.number.lookup.home.screen.gadgetry.SimInfoActivity
import com.callerid.number.lookup.home.kit.AppPrefs
import com.callerid.number.lookup.home.kit.InstallIdRegistry
import com.callerid.number.lookup.home.kit.openActivity
import com.callerid.number.lookup.home.kit.openPolicyLink
import com.callerid.number.lookup.home.kit.openTermLink
import com.callerid.number.lookup.home.kit.rateApp
import com.callerid.number.lookup.home.kit.shareApp

class SettingsHubActivity : FrameActivity<ScreenSettingsBinding>() {

    override val layoutId: Int = R.layout.screen_settings

    private val themeOptions =
        listOf(AppPrefs.THEME_LIGHT, AppPrefs.THEME_DARK, AppPrefs.THEME_SYSTEM)

    private var isProgrammatic = false

    private val prefs by lazy { StorageRegistry(this) }

    private val screeningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshCallScreeningCard() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.padBack.setOnClickListener { goBack() }

        InlinePromo().renderMidNative(this, binding.adNativeFrameVw, binding.adShimmerVw)

        setupThemeToggle()
        bindCard(
            binding.panelLanguage, R.drawable.sym_language, R.string.settings_language,
            currentLanguageName(), chevron = true
        ) {
            openActivity(LanguageSelectActivity.newIntent(this, standalone = true))
        }
        bindCard(
            binding.panelBlocklist, R.drawable.prefs_blocklist, R.string.settings_blocklist,
            getString(R.string.settings_blocklist_sub), chevron = true
        ) {
            openActivity<BlockCenterActivity>()
        }
        bindCard(
            binding.panelSim, R.drawable.sym_sim_card, R.string.settings_sim,
            getString(R.string.settings_sim_sub), chevron = false
        ) { openSimManagement() }

        setupCallScreening()

        val ads = PromoVault.getInstance(this)
        val showRate = ads.getBoolean("is_rateus", true)
        val showShare = ads.getBoolean("is_share", true)

        binding.rowRateVw.root.visibility = if (showRate) View.VISIBLE else View.GONE
        if (showRate) {
            bindRow(
                binding.rowRateVw,
                R.drawable.sym_star,
                R.string.settings_rate,
                R.string.settings_rate_sub
            ) {
                rateApp()
            }
        }

        binding.rowShareVw.root.visibility = if (showShare) View.VISIBLE else View.GONE
        if (showShare) {
            bindRow(
                binding.rowShareVw,
                R.drawable.prefs_share,
                R.string.settings_share,
                R.string.settings_share_sub
            ) {
                shareApp()
            }
        }

        binding.sectionSupportVw.visibility =
            if (showRate || showShare) View.VISIBLE else View.GONE

        binding.rowPrivacyVw.picIcon.setImageResource(R.drawable.sym_policy)
        binding.rowPrivacyVw.lblTitle.setText(R.string.settings_privacy)
        binding.rowPrivacyVw.root.setOnClickListener { openPolicyLink() }
        binding.rowTermsVw.picIcon.setImageResource(R.drawable.sym_terms)
        binding.rowTermsVw.lblTitle.setText(R.string.settings_terms)
        binding.rowTermsVw.root.setOnClickListener { openTermLink() }

        maybeShowCallScreeningHint()
    }

    private fun bindCard(
        card: CellPrefCardBinding,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        sub: String,
        chevron: Boolean,
        onClick: () -> Unit
    ) {
        card.picIcon.setImageResource(icon)
        card.lblTitle.setText(title)
        card.lblSub.text = sub
        card.picChevron.visibility = if (chevron) View.VISIBLE else View.GONE
        card.root.setOnClickListener { onClick() }
    }

    private fun bindRow(
        row: CellSettingRowBinding,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes sub: Int,
        onClick: () -> Unit
    ) {
        row.picIcon.setImageResource(icon)
        row.lblTitle.setText(title)
        row.lblSub.setText(sub)
        row.root.setOnClickListener { onClick() }
    }

    override fun onResume() {
        super.onResume()
        refreshCallScreeningCard()
    }

    private fun setupCallScreening() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            binding.panelCallScreening.visibility = View.GONE
            return
        }
        val rm = getSystemService(RoleManager::class.java)
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            binding.panelCallScreening.visibility = View.GONE
            return
        }
        refreshCallScreeningCard()
        binding.swcCallScreening.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammatic) return@setOnCheckedChangeListener
            if (isChecked) requestCallScreening() else openDefaultAppsSettings()
        }
    }

    private fun maybeShowCallScreeningHint() {
        if (prefs.isCallScreeningHintShown) return
        if (binding.panelCallScreening.visibility != View.VISIBLE) return
        if (binding.swcCallScreening.isChecked) return

        val card = binding.panelCallScreening

        binding.settingsScrollVw.post {
            if (isFinishing || isDestroyed) return@post
            val pad = (24 * resources.displayMetrics.density).toInt()
            binding.settingsScrollVw.scrollTo(0, (card.top - pad).coerceAtLeast(0))
            card.post {
                if (isFinishing || isDestroyed) return@post
                if (binding.swcCallScreening.isChecked) return@post
                prefs.isCallScreeningHintShown = true
                CoachBubble.show(this, card, R.layout.part_call_screening_hint)
            }
        }
    }

    private fun refreshCallScreeningCard() {
        if (!InstallIdRegistry.isRoleAvailable(this)) {
            binding.panelCallScreening.visibility = View.GONE
            return
        }
        val enabled = InstallIdRegistry.isCallerIdEnabled(this)
        binding.panelCallScreening.visibility = if (enabled) View.GONE else View.VISIBLE
        isProgrammatic = true
        binding.swcCallScreening.isChecked = enabled
        isProgrammatic = false
    }

    private fun requestCallScreening() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val rm = getSystemService(RoleManager::class.java) ?: return
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            refreshCallScreeningCard(); return
        }
        OpenPromoRegistry.skipNextAppOpenAd = true
        screeningLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    private fun openDefaultAppsSettings() {
        OpenPromoRegistry.skipNextAppOpenAd = true
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    private fun currentLanguageName(): String =
        LanguageCatalog.all.firstOrNull { it.tag == AppPrefs.selectedLanguage(this) }?.nativeName
            ?: LanguageCatalog.all.first().nativeName

    private fun openSimManagement() {
        val opened = runCatching {
            startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
        }.isSuccess
        if (!opened) openActivity<SimInfoActivity>()
    }

    private fun setupThemeToggle() {
        val card = binding.panelTheme
        val cells =
            listOf(card.segLightVw, card.segDarkVw, card.segSystemVw)
        val current = AppPrefs.selectedTheme(this).ifEmpty { AppPrefs.THEME_LIGHT }
        highlightTheme(cells, themeOptions.indexOf(current).coerceAtLeast(0))

        cells.forEachIndexed { index, cell ->
            cell.setOnClickListener {
                highlightTheme(cells, index)
                val theme = themeOptions[index]
                if (theme != AppPrefs.selectedTheme(this)) {
                    AppPrefs.setTheme(
                        this,
                        theme
                    )
                    AppCompatDelegate.setDefaultNightMode(nightModeFor(theme))
                }
            }
        }
    }

    private fun nightModeFor(theme: String): Int = when (theme) {
        AppPrefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        AppPrefs.THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> AppCompatDelegate.MODE_NIGHT_NO
    }

    private fun highlightTheme(cells: List<ImageView>, selected: Int) {
        cells.forEachIndexed { i, cell ->
            val active = i == selected
            cell.setBackgroundResource(if (active) R.drawable.form_theme_selected else 0)
            val color = ContextCompat.getColor(
                this, if (active) R.color.white else R.color.on_surface_variant
            )
            cell.imageTintList = ColorStateList.valueOf(color)
        }
    }
}
