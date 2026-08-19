package com.callerid.number.lookup.home.ui.settings

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
import com.callerid.admesh.domain.PromoVault
import com.callerid.admesh.presentation.OpenPromoRegistry
import com.callerid.admesh.presentation.InlinePromo
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.base.FrameActivity
import com.callerid.number.lookup.home.data.StorageRegistry
import com.callerid.number.lookup.home.databinding.ScreenSettingsBinding
import com.callerid.number.lookup.home.databinding.CellPrefCardBinding
import com.callerid.number.lookup.home.databinding.CellSettingRowBinding
import com.callerid.number.lookup.home.ui.blocklist.BlockCenterActivity
import com.callerid.number.lookup.home.ui.common.CoachBubble
import com.callerid.number.lookup.home.ui.language.LanguageSelectActivity
import com.callerid.number.lookup.home.ui.language.LanguageCatalog
import com.callerid.number.lookup.home.ui.tools.SimInfoActivity
import com.callerid.number.lookup.home.util.AppPrefs
import com.callerid.number.lookup.home.util.InstallIdRegistry
import com.callerid.number.lookup.home.util.openActivity
import com.callerid.number.lookup.home.util.openPolicyLink
import com.callerid.number.lookup.home.util.openTermLink
import com.callerid.number.lookup.home.util.rateApp
import com.callerid.number.lookup.home.util.shareApp

class SettingsHubActivity : FrameActivity<ScreenSettingsBinding>() {

    override val layoutId: Int = R.layout.screen_settings

    /** Theme segment order — must match cardTheme's segLight / segDark / segSystem. */
    private val themeOptions =
        listOf(AppPrefs.THEME_LIGHT, AppPrefs.THEME_DARK, AppPrefs.THEME_SYSTEM)

    /** Guards the switch listener while we set its state programmatically. */
    private var isProgrammatic = false

    private val prefs by lazy { StorageRegistry(this) }

    /** Re-syncs the call-screening switch after the role-request dialog returns. */
    private val screeningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshCallScreeningCard() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.btnBack.setOnClickListener { goBack() }

        // Native ad at the top of the settings list (bottom adaptive banner auto-loads via FrameActivity).
        InlinePromo().showMidNative(this, binding.adNativeFrame, binding.adShimmer)

        // Preferences grid — Theme is an inline segmented toggle.
        setupThemeToggle()
        bindCard(
            binding.cardLanguage, R.drawable.sym_language, R.string.settings_language,
            currentLanguageName(), chevron = true
        ) {
            openActivity(LanguageSelectActivity.newIntent(this, standalone = true))
        }
        bindCard(
            binding.cardBlocklist, R.drawable.prefs_blocklist, R.string.settings_blocklist,
            getString(R.string.settings_blocklist_sub), chevron = true
        ) {
            openActivity<BlockCenterActivity>()
        }
        bindCard(
            binding.cardSim, R.drawable.sym_sim_card, R.string.settings_sim,
            getString(R.string.settings_sim_sub), chevron = false
        ) { openSimManagement() }

        // Call-screening toggle — backed by the Android 10+ CallScreening role.
        setupCallScreening()

        // Account & support — each row is gated by its own Remote LauncherPrefs flag: true
        // (or unset) → visible, false → gone.
        val ads = PromoVault.getInstance(this)
        val showRate = ads.getBoolean("is_rateus", true)
        val showShare = ads.getBoolean("is_share", true)

        binding.rowRate.root.visibility = if (showRate) View.VISIBLE else View.GONE
        if (showRate) {
            bindRow(
                binding.rowRate,
                R.drawable.sym_star,
                R.string.settings_rate,
                R.string.settings_rate_sub
            ) {
                rateApp()
            }
        }

        binding.rowShare.root.visibility = if (showShare) View.VISIBLE else View.GONE
        if (showShare) {
            bindRow(
                binding.rowShare,
                R.drawable.prefs_share,
                R.string.settings_share,
                R.string.settings_share_sub
            ) {
                shareApp()
            }
        }

        // Both rows off would otherwise leave the heading stranded over nothing.
        binding.sectionSupport.visibility =
            if (showRate || showShare) View.VISIBLE else View.GONE

        // Legal
        binding.rowPrivacy.ivIcon.setImageResource(R.drawable.sym_policy)
        binding.rowPrivacy.tvTitle.setText(R.string.settings_privacy)
        binding.rowPrivacy.root.setOnClickListener { openPolicyLink() }
        binding.rowTerms.ivIcon.setImageResource(R.drawable.sym_terms)
        binding.rowTerms.tvTitle.setText(R.string.settings_terms)
        binding.rowTerms.root.setOnClickListener { openTermLink() }

        // First-run coach-mark nudging the user to enable the call-screening toggle.
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
        card.ivIcon.setImageResource(icon)
        card.tvTitle.setText(title)
        card.tvSub.text = sub
        card.ivChevron.visibility = if (chevron) View.VISIBLE else View.GONE
        card.root.setOnClickListener { onClick() }
    }

    private fun bindRow(
        row: CellSettingRowBinding,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes sub: Int,
        onClick: () -> Unit
    ) {
        row.ivIcon.setImageResource(icon)
        row.tvTitle.setText(title)
        row.tvSub.setText(sub)
        row.root.setOnClickListener { onClick() }
    }

    override fun onResume() {
        super.onResume()
        refreshCallScreeningCard()
    }

    // ── Call Screening (Android 10+ CallScreening role) ───────────────────

    /** Wires the switch, hiding the whole card where the role isn't available. */
    private fun setupCallScreening() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            binding.cardCallScreening.visibility = View.GONE
            return
        }
        val rm = getSystemService(RoleManager::class.java)
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            binding.cardCallScreening.visibility = View.GONE
            return
        }
        refreshCallScreeningCard()
        binding.switchCallScreening.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammatic) return@setOnCheckedChangeListener
            if (isChecked) requestCallScreening() else openDefaultAppsSettings()
        }
    }

    /**
     * First-run coach-mark: dims the whole Settings screen, spotlights the
     * call-screening card through the scrim, and shows a hint bubble beneath it
     * nudging the user to turn the toggle on. Shown only once (persisted via
     * [StorageRegistry.isCallScreeningHintShown]); a tap anywhere dismisses it.
     *
     * Skipped when the card is hidden (role unavailable / pre-Android 10) or the
     * toggle is already on.
     */
    private fun maybeShowCallScreeningHint() {
        if (prefs.isCallScreeningHintShown) return
        if (binding.cardCallScreening.visibility != View.VISIBLE) return
        if (binding.switchCallScreening.isChecked) return

        val card = binding.cardCallScreening
        // Wait for layout (native ad above can shift positions), scroll the card
        // fully into view, then spotlight it on the next frame.
        binding.settingsScroll.post {
            if (isFinishing || isDestroyed) return@post
            val pad = (24 * resources.displayMetrics.density).toInt()
            binding.settingsScroll.scrollTo(0, (card.top - pad).coerceAtLeast(0))
            card.post {
                if (isFinishing || isDestroyed) return@post
                if (binding.switchCallScreening.isChecked) return@post
                prefs.isCallScreeningHintShown = true
                CoachBubble.show(this, card, R.layout.part_call_screening_hint)
            }
        }
    }

    /**
     * Syncs the CallScreening card to the current role state: the switch mirrors
     * whether the role is held, and — once Caller ID is enabled — the whole card
     * is hidden (nothing left to manage). It shows only while Caller ID is still
     * off, and stays hidden where the role isn't available at all.
     */
    private fun refreshCallScreeningCard() {
        if (!InstallIdRegistry.isRoleAvailable(this)) {
            binding.cardCallScreening.visibility = View.GONE
            return
        }
        val enabled = InstallIdRegistry.isCallerIdEnabled(this)
        binding.cardCallScreening.visibility = if (enabled) View.GONE else View.VISIBLE
        isProgrammatic = true
        binding.switchCallScreening.isChecked = enabled
        isProgrammatic = false
    }

    /** Launches the system role-request dialog for call screening. */
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

    /** The role can't be revoked in-app — send the user to default-apps settings. */
    private fun openDefaultAppsSettings() {
        OpenPromoRegistry.skipNextAppOpenAd = true
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    private fun currentLanguageName(): String =
        LanguageCatalog.all.firstOrNull { it.tag == AppPrefs.selectedLanguage(this) }?.nativeName
            ?: LanguageCatalog.all.first().nativeName

    /** Opens the system mobile-network screen, falling back to our in-app SIM info. */
    private fun openSimManagement() {
        val opened = runCatching {
            startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
        }.isSuccess
        if (!opened) openActivity<SimInfoActivity>()
    }


    /** Inline Light / Dark / System segmented toggle inside the Theme card. */
    private fun setupThemeToggle() {
        val card = binding.cardTheme
        val cells =
            listOf(card.segLight, card.segDark, card.segSystem) // matches themeOptions order
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
                    )              // store FrameActivity.applyTheme reads
                    AppCompatDelegate.setDefaultNightMode(nightModeFor(theme)) // recreates activities
                }
            }
        }
    }

    /** Maps an AppPrefs theme string to its AppCompat night-mode constant. */
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
