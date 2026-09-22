package com.callerid.number.lookup.home.screen.pkgresult

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.surface.InlinePromo
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.databinding.ScreenPackageResultBinding
import com.callerid.number.lookup.home.frame.FrameActivity

/**
 * The install / uninstall "complete" screen, ported from the reference app's package-result page.
 *
 * Launched by [PackageEventWatcher] on a package event. It resolves the affected app's label,
 * version and icon from the PackageManager for an install; a removed package is already gone, so
 * those come from [PackageMetadataCache] instead. It then shows the installed or removed card and
 * offers Done and — for an install — Open app.
 */
class PackageResultActivity : FrameActivity<ScreenPackageResultBinding>() {

    override val layoutId: Int = R.layout.screen_package_result

    private val installed: Boolean by lazy { intent.getBooleanExtra(EXTRA_INSTALLED, true) }
    private val affectedPackage: String by lazy { intent.getStringExtra(EXTRA_PACKAGE).orEmpty() }
    private val launchClass: String? by lazy { intent.getStringExtra(EXTRA_CLASS) }

    /** For a removed app the package is gone, so its label/version/icon come from the cache. */
    private val cachedMeta by lazy {
        if (installed) null else PackageMetadataCache.read(this, affectedPackage)
    }

    private var closed = false

    override fun initView() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyInsets()
        applyOutcomeTheme()
        bindContent()
        wireButtons()
        fillAd()
        playEntrance()
    }

    /**
     * A second package event arrives here rather than as a fresh Activity (the screen is singleTask),
     * so re-create to read the new event's package and installed state cleanly.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    /** The hero takes the status-bar inset; the action row clears the navigation bar. */
    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.pkgresContentVw) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.pkgresHeroVw.updatePadding(top = bars.top)
            (binding.pkgresActionsVw.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
                ?.let {
                    it.bottomMargin = dp(16) + bars.bottom
                    binding.pkgresActionsVw.layoutParams = it
                }
            insets
        }
        ViewCompat.requestApplyInsets(binding.pkgresContentVw)
    }

    /** Installed vs removed: the hero wash, the badge glyph, the status pill and the CTA tint. */
    private fun applyOutcomeTheme() = with(binding) {
        pkgresHeroVw.setBackgroundResource(
            if (installed) R.drawable.form_pkgres_hero_installed
            else R.drawable.form_pkgres_hero_removed
        )
        pkgresBadgeVw.setBackgroundResource(
            if (installed) R.drawable.form_pkgres_badge_installed
            else R.drawable.form_pkgres_badge_removed
        )
        pkgresBadgeIconVw.setImageResource(
            if (installed) R.drawable.sym_check else R.drawable.sym_pkgres_minus
        )
        pkgresStatusVw.apply {
            setBackgroundResource(
                if (installed) R.drawable.form_pkgres_status_installed
                else R.drawable.form_pkgres_status_removed
            )
            setText(
                if (installed) R.string.pkgres_installed_status else R.string.pkgres_removed_status
            )
            setTextColor(
                getColor(if (installed) R.color.pkgres_installed else R.color.pkgres_removed)
            )
        }
    }

    private fun bindContent() = with(binding) {
        val label = resolveLabel()

        pkgresTitleVw.text = getString(
            if (installed) R.string.pkgres_installed_title else R.string.pkgres_removed_title,
            label,
        )
        pkgresSubtitleVw.setText(
            if (installed) R.string.pkgres_installed_subtitle else R.string.pkgres_removed_subtitle
        )
        pkgresVersionVw.text = resolveVersion()
        pkgresTimeLabelVw.setText(
            if (installed) R.string.pkgres_installed_time_label
            else R.string.pkgres_removed_time_label
        )
        pkgresTimeVw.setText(R.string.pkgres_just_now)

        // Icon: from the PackageManager for an installed app, from the pre-uninstall cache for a
        // removed one (the package is gone). Empty tile only if neither has it.
        val icon = if (installed) {
            runCatching { packageManager.getApplicationIcon(affectedPackage) }.getOrNull()
        } else {
            cachedMeta?.iconFile
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
                ?.let { BitmapDrawable(resources, it) }
        }
        pkgresAppIconVw.setImageDrawable(icon)
    }

    private fun resolveLabel(): String {
        if (installed) {
            runCatching {
                val info = packageManager.getApplicationInfo(affectedPackage, 0)
                return packageManager.getApplicationLabel(info).toString()
            }
        }
        return cachedMeta?.label?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() }
            ?: affectedPackage.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    private fun resolveVersion(): String {
        if (installed) {
            runCatching {
                val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(
                        affectedPackage, PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(affectedPackage, 0)
                }
                pkg.versionName?.let { return it }
            }
        }
        return cachedMeta?.version?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(EXTRA_VERSION)?.takeIf { it.isNotBlank() }
            ?: getString(R.string.dash)
    }

    private fun wireButtons() = with(binding) {
        pkgresCloseVw.setOnClickListener { close() }
        pkgresDoneVw.setOnClickListener { close() }

        if (installed) {
            pkgresOpenVw.setOnClickListener { openApp() }
        } else {
            // Nothing to open once it is removed — let Done fill the row.
            pkgresOpenVw.visibility = View.GONE
            (pkgresDoneVw.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let {
                it.marginEnd = 0
                pkgresDoneVw.layoutParams = it
            }
        }
    }

    /** The icon settles in and the card rises — the screen arrives rather than snapping on. */
    private fun playEntrance() = with(binding) {
        pkgresAppIconVw.scaleX = 0.6f
        pkgresAppIconVw.scaleY = 0.6f
        pkgresAppIconVw.animate()
            .scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(2.2f))
            .setDuration(420L)
            .start()

        pkgresBadgeVw.alpha = 0f
        pkgresBadgeVw.animate().alpha(1f).setStartDelay(260L).setDuration(220L).start()

        listOf<View>(pkgresStatusVw, pkgresTitleVw, pkgresSubtitleVw, pkgresDetailsVw)
            .forEachIndexed { index, view ->
                view.alpha = 0f
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, dp(12).toFloat(), 0f).apply {
                    startDelay = 90L * index
                    duration = 320L
                    start()
                }
                view.animate().alpha(1f).setStartDelay(90L * index).setDuration(320L).start()
            }
    }

    private fun openApp() {
        val launch = runCatching {
            launchClass?.takeIf { it.isNotBlank() }?.let {
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(affectedPackage, it)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: packageManager.getLaunchIntentForPackage(affectedPackage)
        }.getOrNull()

        if (launch == null) {
            Toast.makeText(this, R.string.pkgres_unable_to_open, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { startActivity(launch) }.onFailure {
            Toast.makeText(this, R.string.pkgres_unable_to_open, Toast.LENGTH_SHORT).show()
        }
        finishSafely()
    }

    private fun fillAd() {
        if (!ShellPromoConfig.packageResultSettings(this).bodyNative) return
        binding.adNativeFrameVw.visibility = View.VISIBLE
        InlinePromo().renderMidNative2(this, binding.adNativeFrameVw, binding.adShimmerVw)
    }

    private fun close() {
        if (closed) return
        closed = true
        setButtonsEnabled(false)
        finishSafely()
    }

    /** Back behaves as Done, so the screen has one way out. */
    override fun performBack() = close()

    private fun setButtonsEnabled(enabled: Boolean) = with(binding) {
        pkgresCloseVw.isEnabled = enabled
        pkgresDoneVw.isEnabled = enabled
        pkgresOpenVw.isEnabled = enabled
    }

    private fun finishSafely() {
        if (!isFinishing) finish()
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    companion object {
        const val EXTRA_INSTALLED = "package_installed"
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_LABEL = "package_label"
        const val EXTRA_VERSION = "package_version"
        const val EXTRA_CLASS = "package_class"
    }
}
