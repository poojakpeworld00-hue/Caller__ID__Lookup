package com.callerid.number.lookup.home.shell.screens

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import com.callerid.admesh.engine.ShellPromoConfig
import android.app.WallpaperManager
import android.app.WallpaperManager.OnColorsChangedListener
import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.util.Log
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.viewbinding.ViewBinding
import kotlinx.collections.immutable.toImmutableList
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.insetsController
import org.fossify.commons.extensions.isPackageInstalled
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.extensions.realScreenSize
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.DARK_GREY
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isOreoMr1Plus
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.databinding.ScreenLauncherHomeBinding
import com.callerid.number.lookup.home.databinding.BoardAllAppsBinding
import com.callerid.number.lookup.home.databinding.BoardWidgetsBinding
import com.callerid.number.lookup.home.shell.sheets.RelabelItemDialog
import com.callerid.number.lookup.home.shell.ext.config
import com.callerid.number.lookup.home.shell.ext.getDrawableForPackageName
import com.callerid.number.lookup.home.shell.ext.getLabel
import com.callerid.number.lookup.home.shell.ext.handleGridItemPopupMenu
import com.callerid.number.lookup.home.shell.ext.hiddenIconsDB
import com.callerid.number.lookup.home.shell.ext.homeScreenGridItemsDB
import com.callerid.number.lookup.home.shell.ext.isDefaultLauncher
import com.callerid.number.lookup.home.shell.ext.launchApp
import com.callerid.number.lookup.home.shell.ext.launchAppInfo
import com.callerid.number.lookup.home.shell.ext.launchersDB
import com.callerid.number.lookup.home.shell.ext.requestSetAsDefaultLauncher
import com.callerid.number.lookup.home.shell.ext.supportsDarkText
import com.callerid.number.lookup.home.shell.ext.uninstallApp
import com.callerid.number.lookup.home.shell.panels.BasePanel
import com.callerid.number.lookup.home.shell.support.CLOCK_ROW_SPAN
import com.callerid.number.lookup.home.shell.support.ITEM_TYPE_FOLDER
import com.callerid.number.lookup.home.shell.support.ITEM_TYPE_ICON
import com.callerid.number.lookup.home.shell.support.ITEM_TYPE_SHORTCUT
import com.callerid.number.lookup.home.shell.support.OnboardRouter
import com.callerid.number.lookup.home.shell.support.SwipeCoachPrompt
import com.callerid.number.lookup.home.shell.support.ITEM_TYPE_WIDGET
import com.callerid.number.lookup.home.shell.support.IconStore
import com.callerid.number.lookup.home.shell.support.PSEUDO_WIDGET_CLOCK
import com.callerid.number.lookup.home.shell.support.PSEUDO_WIDGET_SEARCH
import com.callerid.number.lookup.home.shell.support.REQUEST_ALLOW_BINDING_WIDGET
import com.callerid.number.lookup.home.shell.support.SEARCH_BAR_ROW
import com.callerid.number.lookup.home.shell.support.REQUEST_CONFIGURE_WIDGET
import com.callerid.number.lookup.home.shell.support.REQUEST_CREATE_SHORTCUT
import com.callerid.number.lookup.home.shell.support.UNINSTALL_APP_REQUEST_CODE
import com.callerid.number.lookup.home.shell.contracts.SwipeListener
import com.callerid.number.lookup.home.shell.contracts.TileMenuListener
import com.callerid.number.lookup.home.shell.entities.AppTile
import com.callerid.number.lookup.home.shell.entities.MaskedIcon
import com.callerid.number.lookup.home.shell.entities.BoardItem
import com.callerid.number.lookup.home.shell.signals.ScreenLockAdminReceiver
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.appcompat.app.AppCompatActivity
import com.callerid.number.lookup.home.store.LanguageRegistry
import com.callerid.number.lookup.home.screen.main.HomeShellDriver
import com.google.android.material.snackbar.Snackbar
import com.callerid.admesh.surface.StoreUpdateRegistry
import com.callerid.number.lookup.home.screen.main.HomeShellOwner
import com.callerid.number.lookup.home.kit.applyNativeAdTheme

class HomeBoardActivity : ShellBaseActivity(), SwipeListener, HomeShellOwner {

    override val hostActivity: AppCompatActivity get() = this

    override val homeShellController = HomeShellDriver(this)

    override fun onShellBackExhausted() {
        hideCallerPanel()
    }

    /** The shell rides in the swipe-right panel, so it is on screen only while that is open. */
    override val isShellOnScreen: Boolean get() = isCallerPanelExpanded()

    /** The restart Snackbar, so a re-offer on the next resume does not stack a second one. */
    private var updateReadySnackbar: Snackbar? = null

    /**
     * With the panel open the shell's own Snackbar is right; with it shut the shell is parked
     * off screen, so the home grid has to carry the prompt itself or a downloaded update has
     * nowhere to be installed from — the launcher home is where these users live.
     */
    override fun showUpdateReadyPrompt() {
        if (isCallerPanelExpanded()) {
            binding.callerPanelVw.root.shell()?.showUpdateReadyPrompt()
            return
        }
        if (updateReadySnackbar?.isShown == true) return
        updateReadySnackbar = Snackbar
            .make(binding.mainHolderVw, R.string.update_ready_msg, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.update_restart) { StoreUpdateRegistry.completeUpdate() }
            .also { it.show() }
    }

    override fun bringHostToFront() {
        runCatching {
            startActivity(
                Intent(this, HomeBoardActivity::class.java)
                    .addFlags(HomeShellDriver.REORDER_FLAGS)
                    
                    .putExtra(HomeShellDriver.EXTRA_SELF_REORDER, true)
            )
        }
    }

    private var mTouchDownX = -1
    private var mTouchDownY = -1
    private var mAllAppsFragmentY = 0
    private var mWidgetsFragmentY = 0
    private var mScreenHeight = 0
    private var mScreenWidth = 0
    private var mMoveGestureThreshold = 0
    private var mIgnoreUpEvent = false
    private var mIgnoreMoveEvents = false
    private var mIgnoreXMoveEvents = false
    private var mIgnoreYMoveEvents = false
    private var mLongPressedIcon: BoardItem? = null
    private var mOpenPopupMenu: PopupMenu? = null
    private var mLastTouchCoords = Pair(-1f, -1f)
    private var mActionOnCanBindWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnWidgetConfiguredWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnAddShortcut:
            ((shortcutId: String, label: String, icon: Drawable) -> Unit)? = null
    private var wasJustPaused: Boolean = false

    private val mSwipeHintAnimators = mutableListOf<ObjectAnimator>()
    private val mSwipeHintHider = Runnable { hideSwipeHint() }
    private var mHomeHint: ShellPromoConfig.HomeHint? = null
    private var mHintRunActive = false

    private var wallpaperColorChangeListener: OnColorsChangedListener? = null
    private var wallpaperSupportsDarkText: Boolean? = null

    private lateinit var mDetector: GestureDetectorCompat
    private val binding by viewBinding(ScreenLauncherHomeBinding::inflate)

    companion object {
        private var mLastUpEvent = 0L
        private const val ANIMATION_DURATION = 150L
        private const val APP_DRAWER_CLOSE_DELAY = 300L
        private const val APP_DRAWER_STATE = "app_drawer_state"
        private const val SWIPE_HINT_ANIMATION_DURATION = 900L

        private const val SHADE_HINT_RESUME_DELAY = 2500L

        private const val TAG = "HomeBoardActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false

        LanguageRegistry.applySaved(this)

        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)

        applyNativeAdTheme()
        setupEdgeToEdge(
            padTopSystem = listOf(
                binding.allAppsFragmentVw.root,
                binding.widgetsFragmentVw.root,
                binding.leftPanelVw.root,
                binding.defaultLauncherBannerVw.root
            ),
            padBottomImeAndSystem = listOf(
                binding.allAppsFragmentVw.allAppsGridVw,
                binding.widgetsFragmentVw.widgetsListVw,
                binding.leftPanelVw.panelScrollVw
            ),

            padBottomSystem = listOf(binding.homeScreenGridVw.root, binding.leftPanelVw.adNativeFrameVw)
        )

        mDetector = GestureDetectorCompat(this, MyGestureListener(this))

        mScreenHeight = realScreenSize.y
        mScreenWidth = realScreenSize.x
        mAllAppsFragmentY = mScreenHeight
        mWidgetsFragmentY = mScreenHeight
        mMoveGestureThreshold = resources.getDimensionPixelSize(R.dimen.move_gesture_threshold)

        arrayOf(
            binding.allAppsFragmentVw.root as BasePanel<*>,
            binding.widgetsFragmentVw.root as BasePanel<*>
        ).forEach { fragment ->
            fragment.setupFragment(this)
            fragment.y = mScreenHeight.toFloat()
            fragment.beVisible()
        }

        binding.leftPanelVw.root.apply {
            setupFragment(this@HomeBoardActivity)
            x = mScreenWidth.toFloat()
            beVisible()
        }

        binding.callerPanelVw.root.apply {
            setupFragment(this@HomeBoardActivity)
            x = -mScreenWidth.toFloat()
            beVisible()
        }

        if (OnboardRouter.wasOnboardingCompleted(this)) {
            homeShellController.onHostCreated()
        }

        handleIntentAction(intent)

        binding.homeScreenGridVw.root.itemClickListener = {
            performItemClick(it)
        }

        binding.homeScreenGridVw.root.itemLongClickListener = {
            performItemLongClick(
                x = binding.homeScreenGridVw.root.getClickableRect(it).left.toFloat(),
                clickedGridItem = it
            )
        }

        binding.defaultLauncherBannerVw.root.setOnClickListener { requestSetAsDefaultLauncher() }

        setupWallpaperColorListener()

        if (OnboardRouter.resumeIfUnfinished(this)) {
            return
        }

        startSwipeHintRun()
    }

    private fun startSwipeHintRun() {
        val hint = ShellPromoConfig.boardHint(this)
        mHomeHint = hint
        if (!hint.visible) {
            return
        }

        val resuming = hint.mode == ShellPromoConfig.HintMode.ONCE &&
                config.swipeHintIndex in 1 until hint.directions.size

        mHintRunActive = when {
            resuming -> true
            hint.mode == ShellPromoConfig.HintMode.ALWAYS -> true
            hint.mode == ShellPromoConfig.HintMode.APP_LAUNCHES ->
                ShellPromoConfig.hintDue(this, hint)

            else -> !config.wasSwipeHintShown
        }

        if (mHintRunActive && !resuming) {
            config.swipeHintIndex = 0
        }
    }

    private fun showNextSwipeHint() {
        val hint = mHomeHint ?: return
        if (!mHintRunActive) {
            return
        }

        if (config.swipeHintIndex >= hint.directions.size) {
            mHintRunActive = false
            config.wasSwipeHintShown = true
            hideSwipeHint()
            return
        }

        if (isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded() || isLeftPanelExpanded() ||
            isCallerPanelExpanded()
        ) {
            return
        }

        showSwipeHint(hint.directions[config.swipeHintIndex], hint.autoHideSec)
    }

    private fun completeSwipeHint(direction: ShellPromoConfig.HintDirection) {
        if (!mHintRunActive || !binding.swipeHintVw.isVisible) {
            return
        }

        if (mHomeHint?.directions?.getOrNull(config.swipeHintIndex) != direction) {
            return
        }

        config.swipeHintIndex = config.swipeHintIndex + 1
        hideSwipeHint()

        if (direction == ShellPromoConfig.HintDirection.DOWN) {
            binding.swipeHintVw.postDelayed({ showNextSwipeHint() }, SHADE_HINT_RESUME_DELAY)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSwipeHint(direction: ShellPromoConfig.HintDirection, autoHideSec: Int) {
        val travel = resources.getDimension(R.dimen.swipe_hint_travel)

        binding.swipeHintVw.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {

                mIgnoreXMoveEvents = false
                mIgnoreYMoveEvents = false
                mIgnoreUpEvent = false
            }
            runCatching { mDetector.onTouchEvent(event) }
            true
        }

        mSwipeHintAnimators.forEach { it.cancel() }
        mSwipeHintAnimators.clear()
        binding.swipeHintVw.removeCallbacks(mSwipeHintHider)
        binding.swipeHintVw.removeAllViews()

        val row = layoutInflater.inflate(R.layout.cell_swipe_hint, binding.swipeHintVw, false)
        val chevrons = row.findViewById<View>(R.id.tip_chevrons)
        row.findViewById<TextView>(R.id.tip_label).setText(captionFor(direction))

        val (property, distance) = when (direction) {
            ShellPromoConfig.HintDirection.RIGHT -> View.TRANSLATION_X to travel
            ShellPromoConfig.HintDirection.LEFT -> View.TRANSLATION_X to -travel
            ShellPromoConfig.HintDirection.UP -> View.TRANSLATION_Y to -travel
            ShellPromoConfig.HintDirection.DOWN -> View.TRANSLATION_Y to travel
        }
        chevrons.rotation = when (direction) {
            ShellPromoConfig.HintDirection.RIGHT -> 0f
            ShellPromoConfig.HintDirection.LEFT -> 180f
            ShellPromoConfig.HintDirection.UP -> 270f
            ShellPromoConfig.HintDirection.DOWN -> 90f
        }

        if (property == View.TRANSLATION_Y) {
            val pad = resources.getDimensionPixelSize(R.dimen.swipe_hint_vertical_pad)
            row.setPadding(row.paddingLeft, pad, row.paddingRight, pad)
        }

        binding.swipeHintVw.addView(row)
        mSwipeHintAnimators += ObjectAnimator.ofFloat(chevrons, property, 0f, distance).apply {
            duration = SWIPE_HINT_ANIMATION_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        binding.swipeHintVw.beVisible()

        if (autoHideSec > 0) {
            binding.swipeHintVw.postDelayed(mSwipeHintHider, autoHideSec * 1000L)
        }
    }

    private fun performHintAction() {
        when (mHomeHint?.directions?.getOrNull(config.swipeHintIndex)) {
            ShellPromoConfig.HintDirection.RIGHT -> onFlingRight()
            ShellPromoConfig.HintDirection.LEFT -> onFlingLeft()
            ShellPromoConfig.HintDirection.UP -> onFlingUp()
            ShellPromoConfig.HintDirection.DOWN -> onFlingDown()
            null -> hideSwipeHint()
        }
    }

    private fun captionFor(direction: ShellPromoConfig.HintDirection): Int = when (direction) {
        ShellPromoConfig.HintDirection.RIGHT -> R.string.swipe_right_hint
        ShellPromoConfig.HintDirection.LEFT -> R.string.swipe_left_hint
        ShellPromoConfig.HintDirection.UP -> R.string.swipe_up_hint
        ShellPromoConfig.HintDirection.DOWN -> R.string.swipe_down_hint
    }

    private fun hideSwipeHint() {
        if (!binding.swipeHintVw.isVisible) {
            return
        }

        binding.swipeHintVw.removeCallbacks(mSwipeHintHider)
        binding.swipeHintVw.setOnTouchListener(null)
        mSwipeHintAnimators.forEach { it.cancel() }
        mSwipeHintAnimators.clear()
        binding.swipeHintVw.beGone()
    }

    private fun setupWallpaperColorListener() {
        if (isOreoMr1Plus()) {
            val wallpaperManager = WallpaperManager.getInstance(this)
            wallpaperColorChangeListener = OnColorsChangedListener { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                    wallpaperSupportsDarkText = colors?.supportsDarkText() ?: run {
                        refreshWallpaperSupportsDarkText()
                        wallpaperSupportsDarkText
                    }
                    if (!isAllAppsFragmentExpanded() && !isWidgetsFragmentExpanded()) {
                        runOnUiThread {
                            updateStatusBarIcons()
                        }
                    }
                }
            }
            wallpaperManager.addOnColorsChangedListener(
                wallpaperColorChangeListener!!,
                Handler(Looper.getMainLooper())
            )

            refreshWallpaperSupportsDarkText()
        }
    }

    private fun refreshWallpaperSupportsDarkText() {
        if (!isOreoMr1Plus()) return
        wallpaperSupportsDarkText = WallpaperManager.getInstance(this)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.supportsDarkText()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        
        if (intent.getBooleanExtra(HomeShellDriver.EXTRA_SELF_REORDER, false)) {
            return
        }

        if (OnboardRouter.resumeIfUnfinished(this)) {
            return
        }

        val wasAnyFragmentOpen = isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded()
        if (wasJustPaused) {
            if (isAllAppsFragmentExpanded()) {
                hideFragment(binding.allAppsFragmentVw)
            }
            if (isWidgetsFragmentExpanded()) {
                hideFragment(binding.widgetsFragmentVw)
            }
        } else {
            closeAppDrawer()
            closeWidgetsFragment()
        }

        if (isLeftPanelExpanded()) {
            hideLeftPanel()
        }

        if (isCallerPanelExpanded()) {
            hideCallerPanel()
        }

        binding.allAppsFragmentVw.searchBarVw.closeSearch()

        val alreadyOnHome = intent.flags and FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
        if (alreadyOnHome && !wasAnyFragmentOpen) {
            binding.homeScreenGridVw.root.skipToPage(0)
        }

        handleIntentAction(intent)
    }

    override fun onStart() {
        super.onStart()
        binding.homeScreenGridVw.root.appWidgetHost.startListening()
    }

    override fun onResume() {
        super.onResume()
        wasJustPaused = false

        
        SwipeCoachPrompt.dismiss()

        LanguageRegistry.applySaved(this)

        homeShellController.onHostResume()
        refreshWallpaperSupportsDarkText()
        Handler(Looper.getMainLooper()).postDelayed({
            if (isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded()) {
                updateStatusBarIcons(getProperBackgroundColor())
            } else {
                updateStatusBarIcons()
            }
        }, ANIMATION_DURATION)

        showNextSwipeHint()

        with(binding.mainHolderVw) {
            onGlobalLayout {
                binding.allAppsFragmentVw.root.setupViews()
                binding.widgetsFragmentVw.root.setupViews()
            }
        }

        ensureBackgroundThread {
            if (IconStore.launchers.isEmpty()) {
                val hiddenIcons = hiddenIconsDB.getHiddenIcons().map {
                    it.getIconIdentifier()
                }

                IconStore.launchers = launchersDB.getAppLaunchers().filter {
                    val showIcon = !hiddenIcons.contains(it.getLauncherIdentifier())
                    if (!showIcon) {
                        try {
                            launchersDB.deleteById(it.id!!)
                        } catch (_: Exception) {
                        }
                    }
                    showIcon
                }.toMutableList() as ArrayList<AppTile>
            }

            binding.allAppsFragmentVw.root.gotLaunchers(IconStore.launchers)
            binding.leftPanelVw.root.gotLaunchers(IconStore.launchers)
            refreshLaunchers()
        }

        binding.defaultLauncherBannerVw.root.beVisibleIf(!isDefaultLauncher())

        binding.homeScreenGridVw.root.resizeGrid(
            newRowCount = config.homeRowCount,
            newColumnCount = config.homeColumnCount
        )
        binding.homeScreenGridVw.root.updateColors()
        binding.allAppsFragmentVw.root.onResume()
    }

    override fun onStop() {
        super.onStop()
        try {
            binding.homeScreenGridVw.root.appWidgetHost.stopListening()
        } catch (_: Exception) {
        }

        wasJustPaused = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isOreoMr1Plus() && wallpaperColorChangeListener != null) {
            WallpaperManager.getInstance(this)
                .removeOnColorsChangedListener(wallpaperColorChangeListener!!)
        }

        binding.swipeHintVw.removeCallbacks(mSwipeHintHider)
        mSwipeHintAnimators.forEach { it.cancel() }
        mSwipeHintAnimators.clear()

        homeShellController.onHostDestroy()
    }

    override fun onPause() {
        super.onPause()
        wasJustPaused = true
    }

    override fun onBackPressedCompat(): Boolean {
        return if (isLeftPanelExpanded()) {
            if (binding.leftPanelVw.root.hasQuery()) {
                binding.leftPanelVw.root.resetSearch()
            } else {
                hideLeftPanel()
            }
            true
        } else if (isCallerPanelExpanded()) {

            if (binding.callerPanelVw.root.shell()?.onBackPressed() != true) {
                hideCallerPanel()
            }
            true
        } else if (isAllAppsFragmentExpanded()) {
            if (!binding.allAppsFragmentVw.root.onBackPressed()) {
                hideFragment(binding.allAppsFragmentVw)
                true
            } else {
                true
            }
        } else if (isWidgetsFragmentExpanded()) {
            if (binding.widgetsFragmentVw.searchBarVw.isSearchOpen) {
                clearWidgetsSearch()
            } else {
                hideFragment(binding.widgetsFragmentVw)
            }
            true
        } else if (binding.homeScreenGridVw.resizeFrameVw.isVisible) {
            binding.homeScreenGridVw.root.hideResizeLines()
            true
        } else {

            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)

        when (requestCode) {
            UNINSTALL_APP_REQUEST_CODE -> {
                ensureBackgroundThread {
                    refreshLaunchers()
                }
            }

            REQUEST_ALLOW_BINDING_WIDGET -> mActionOnCanBindWidget?.invoke(resultCode == RESULT_OK)
            REQUEST_CONFIGURE_WIDGET -> mActionOnWidgetConfiguredWidget?.invoke(resultCode == RESULT_OK)
            REQUEST_CREATE_SHORTCUT -> {
                if (resultCode == RESULT_OK && resultData != null) {
                    val launcherApps =
                        applicationContext.getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
                    if (launcherApps.hasShortcutHostPermission()) {
                        val item = launcherApps.getPinItemRequest(resultData)
                        val shortcutInfo = item?.shortcutInfo ?: return
                        if (item.accept()) {
                            val shortcutId = shortcutInfo.id
                            val label = shortcutInfo.getLabel()
                            val icon = launcherApps.getShortcutBadgedIconDrawable(
                                shortcutInfo,
                                resources.displayMetrics.densityDpi
                            )
                            mActionOnAddShortcut?.invoke(shortcutId, label, icon)
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.allAppsFragmentVw.root.onConfigurationChanged()
        binding.widgetsFragmentVw.root.onConfigurationChanged()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }

        if (mLongPressedIcon != null && event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            mLastUpEvent = System.currentTimeMillis()
        }

        try {
            mDetector.onTouchEvent(event)
        } catch (_: Exception) {
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownX = event.x.toInt()
                mTouchDownY = event.y.toInt()
                mAllAppsFragmentY = binding.allAppsFragmentVw.root.y.toInt()
                mWidgetsFragmentY = binding.widgetsFragmentVw.root.y.toInt()
                mIgnoreUpEvent = false
            }

            MotionEvent.ACTION_MOVE -> {

                val hasFingerMoved = if (mTouchDownX == -1 || mTouchDownY == -1) {
                    mTouchDownX = event.x.toInt()
                    mTouchDownY = event.y.toInt()
                    false
                } else {
                    hasFingerMoved(event)
                }

                if (mLongPressedIcon != null && (mOpenPopupMenu != null) && hasFingerMoved) {
                    mOpenPopupMenu?.dismiss()
                    mOpenPopupMenu = null
                    binding.homeScreenGridVw.root.itemDraggingStarted(mLongPressedIcon!!)
                    hideFragment(binding.allAppsFragmentVw)
                }

                if (mLongPressedIcon != null && hasFingerMoved) {
                    binding.homeScreenGridVw.root.draggedItemMoved(event.x.toInt(), event.y.toInt())
                }

                if (hasFingerMoved && !mIgnoreMoveEvents) {
                    val diffY = mTouchDownY - event.y
                    val diffX = mTouchDownX - event.x

                    if (abs(diffY) > abs(diffX) && !mIgnoreYMoveEvents) {
                        mIgnoreXMoveEvents = true
                        if (isWidgetsFragmentExpanded()) {
                            val newY = mWidgetsFragmentY - diffY
                            binding.widgetsFragmentVw.root.y = min(
                                a = max(0f, newY), b = mScreenHeight.toFloat()
                            )
                        } else if (mLongPressedIcon == null) {
                            val newY = mAllAppsFragmentY - diffY
                            binding.allAppsFragmentVw.root.y = min(
                                a = max(0f, newY), b = mScreenHeight.toFloat()
                            )
                        }
                    } else if (abs(diffX) > abs(diffY) && !mIgnoreXMoveEvents) {
                        mIgnoreYMoveEvents = true
                        binding.homeScreenGridVw.root.setSwipeMovement(diffX)
                    }
                }

                mLastTouchCoords = Pair(event.x, event.y)
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                mTouchDownX = -1
                mTouchDownY = -1
                mIgnoreMoveEvents = false
                mLongPressedIcon = null
                mLastTouchCoords = Pair(-1f, -1f)
                resetFragmentTouches()
                binding.homeScreenGridVw.root.itemDraggingStopped()

                if (!mIgnoreUpEvent) {
                    if (!mIgnoreYMoveEvents) {
                        if (binding.allAppsFragmentVw.root.y < mScreenHeight * 0.5) {
                            showFragment(binding.allAppsFragmentVw)
                        } else if (isAllAppsFragmentExpanded()) {
                            hideFragment(binding.allAppsFragmentVw)
                        }

                        if (binding.widgetsFragmentVw.root.y < mScreenHeight * 0.5) {
                            showFragment(binding.widgetsFragmentVw)
                        } else if (isWidgetsFragmentExpanded()) {
                            hideFragment(binding.widgetsFragmentVw)
                        }
                    }

                }

                
                if (!mIgnoreXMoveEvents) {
                    binding.homeScreenGridVw.root.finalizeSwipe()
                }

                mIgnoreXMoveEvents = false
                mIgnoreYMoveEvents = false
            }
        }

        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(APP_DRAWER_STATE, isAllAppsFragmentExpanded())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (savedInstanceState.getBoolean(APP_DRAWER_STATE)) {
            showFragment(binding.allAppsFragmentVw, 0L)
        }
    }

    private fun handleIntentAction(intent: Intent) {
        if (intent.action == LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) {
            val launcherApps =
                applicationContext.getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            val item = launcherApps.getPinItemRequest(intent)
            val shortcutInfo = item?.shortcutInfo ?: return

            ensureBackgroundThread {
                val shortcutId = shortcutInfo.id
                val label = shortcutInfo.getLabel()
                val icon = launcherApps.getShortcutBadgedIconDrawable(
                    shortcutInfo,
                    resources.displayMetrics.densityDpi
                )
                val (page, rect) = findFirstEmptyCell()
                val gridItem = BoardItem(
                    id = null,
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom,
                    page = page,
                    packageName = shortcutInfo.`package`,
                    activityName = "",
                    title = label,
                    type = ITEM_TYPE_SHORTCUT,
                    className = "",
                    widgetId = -1,
                    shortcutId = shortcutId,
                    icon = icon.toBitmap(),
                    docked = false,
                    parentId = null,
                    drawable = icon
                )

                runOnUiThread {
                    binding.homeScreenGridVw.root.skipToPage(page)
                }

                Thread.sleep(2000)

                try {
                    item.accept()
                    binding.homeScreenGridVw.root.storeAndShowGridItem(gridItem)
                } catch (_: IllegalStateException) {
                }
            }
        }
    }

    private fun findFirstEmptyCell(): Pair<Int, Rect> {
        val gridItems = homeScreenGridItemsDB.getAllItems() as ArrayList<BoardItem>
        val maxPage = gridItems.maxOf { it.page }
        val occupiedCells = ArrayList<Triple<Int, Int, Int>>()
        gridItems.toImmutableList().filter { it.parentId == null }.forEach { item ->
            for (xCell in item.left..item.right) {
                for (yCell in item.top..item.bottom) {
                    occupiedCells.add(Triple(item.page, xCell, yCell))
                }
            }
        }

        for (page in 0 until maxPage) {
            for (checkedYCell in 0 until config.homeColumnCount) {
                for (checkedXCell in 0 until config.homeRowCount - 1) {
                    val wantedCell = Triple(page, checkedXCell, checkedYCell)
                    if (!occupiedCells.contains(wantedCell)) {
                        return Pair(
                            first = page,
                            second = Rect(
                                wantedCell.second,
                                wantedCell.third,
                                wantedCell.second,
                                wantedCell.third
                            )
                        )
                    }
                }
            }
        }

        return Pair(maxPage + 1, Rect(0, 0, 0, 0))
    }

    private fun hasFingerMoved(event: MotionEvent): Boolean {
        return mTouchDownX != -1 && mTouchDownY != -1 &&
                (abs(mTouchDownX - event.x) > mMoveGestureThreshold || abs(mTouchDownY - event.y) > mMoveGestureThreshold)
    }

    private fun refreshLaunchers() {
        val launchers = getAllAppLaunchers()
        binding.allAppsFragmentVw.root.gotLaunchers(launchers)
        binding.leftPanelVw.root.gotLaunchers(launchers)
        binding.widgetsFragmentVw.root.getAppWidgets()

        IconStore.launchers.map { it.packageName }.forEach { packageName ->
            if (!launchers.map { it.packageName }.contains(packageName)) {
                launchersDB.deleteApp(packageName)
                homeScreenGridItemsDB.deleteByPackageName(packageName)
            }
        }

        IconStore.launchers = launchers

        if (!config.wasHomeScreenInit) {
            ensureBackgroundThread {
                getDefaultAppPackages(launchers)
                config.wasHomeScreenInit = true
                seedHomeWidgetsIfNeeded()
                binding.homeScreenGridVw.root.fetchGridItems()
            }
        } else if (!config.wasSearchBarSeeded || !config.wasClockSeeded ||
            !config.wasHomeWidgetsRepaired
        ) {
            ensureBackgroundThread {
                seedHomeWidgetsIfNeeded()
                binding.homeScreenGridVw.root.fetchGridItems()
            }
        } else {
            binding.homeScreenGridVw.root.fetchGridItems()
        }
    }

    fun isAllAppsFragmentExpanded() = binding.allAppsFragmentVw.root.y != mScreenHeight.toFloat()

    private fun isWidgetsFragmentExpanded() =
        binding.widgetsFragmentVw.root.y != mScreenHeight.toFloat()

    fun isLeftPanelExpanded() = binding.leftPanelVw.root.x != mScreenWidth.toFloat()

    private fun showLeftPanel() {

        binding.leftPanelVw.root.onPanelShown()
        showSidePanel(binding.leftPanelVw.root)
    }

    fun openAppSearch() {
        showLeftPanel()
        Handler(Looper.getMainLooper()).postDelayed({
            if (isLeftPanelExpanded()) {
                binding.leftPanelVw.root.focusSearch()
            }
        }, ANIMATION_DURATION)
    }

    fun openClockApp() {
        val intents = listOf(
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(AlarmClock.ACTION_SET_ALARM)
        )

        if (!startFirstResolvable(intents)) launchClockPackage(intents)
    }

    fun openCalendarApp() {
        val todayUri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(System.currentTimeMillis().toString())
            .build()

        val intents = listOf(
            Intent(Intent.ACTION_VIEW, todayUri),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
        )

        startFirstResolvable(intents)
    }

    /**
     * True once an intent actually started. A target can exist and still refuse the start — OnePlus'
     * deskclock guards ACTION_SHOW_ALARMS with com.android.alarm.permission.SET_ALARM — so every
     * failure falls through to the next candidate instead of taking the launcher down.
     */
    private fun startFirstResolvable(intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (e: Exception) {
                Log.w(TAG, "start refused for ${intent.action}", e)
            }
        }
        return false
    }

    /** Last resort: open the clock app by its launcher entry, which needs no action permission. */
    private fun launchClockPackage(intents: List<Intent>) {
        val clockPackage = intents.firstNotNullOfOrNull { intent ->
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }
        val launch = clockPackage?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launch == null) {
            toast(org.fossify.commons.R.string.no_app_found)
            return
        }
        runCatching { startActivity(launch) }
            .onFailure { Log.w(TAG, "clock launcher entry refused", it) }
    }

    fun hideLeftPanel() {

        hideSidePanel(binding.leftPanelVw.root, mScreenWidth.toFloat()) {
            binding.leftPanelVw.root.resetSearch()
            showNextSwipeHint()
        }
    }

    private fun showSidePanel(panel: View) {

        applyNativeAdTheme()
        hideSwipeHint()
        animateSidePanelTo(panel, 0f)
        window.navigationBarColor = resources.getColor(R.color.semitransparent_navigation)
        binding.homeScreenGridVw.root.fragmentExpanded()
        binding.homeScreenGridVw.root.hideResizeLines()
        binding.homeScreenGridVw.root.animate()
            .alpha(0f)
            .setDuration(ANIMATION_DURATION)
            .start()

        @SuppressLint("AccessibilityFocus")
        panel.performAccessibilityAction(
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
            null
        )

        Handler(Looper.getMainLooper()).postDelayed({
            updateStatusBarIcons(getProperBackgroundColor())
        }, ANIMATION_DURATION)
    }

    private fun hideSidePanel(panel: View, parkedX: Float, onParked: (() -> Unit)? = null) {
        animateSidePanelTo(panel, parkedX) {

            panel.x = parkedX
            onParked?.invoke()
        }
        window.navigationBarColor = Color.TRANSPARENT
        binding.homeScreenGridVw.root.fragmentCollapsed()
        updateStatusBarIcons()
        hideKeyboard()
    }

    private fun animateSidePanelTo(panel: View, x: Float, onEnd: (() -> Unit)? = null) {
        ObjectAnimator.ofFloat(panel, "x", x).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            if (onEnd != null) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = onEnd()
                })
            }
            start()
        }
    }

    fun startHandlingTouches(touchDownY: Int) {
        mLongPressedIcon = null
        mTouchDownY = touchDownY
        mAllAppsFragmentY = binding.allAppsFragmentVw.root.y.toInt()
        mWidgetsFragmentY = binding.widgetsFragmentVw.root.y.toInt()
        mIgnoreUpEvent = false
    }

    private fun showFragment(fragment: ViewBinding, animationDuration: Long = ANIMATION_DURATION) {

        applyNativeAdTheme()
        ObjectAnimator.ofFloat(fragment.root, "y", 0f).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            start()
        }

        window.navigationBarColor = resources.getColor(R.color.semitransparent_navigation)
        binding.homeScreenGridVw.root.fragmentExpanded()
        binding.homeScreenGridVw.root.hideResizeLines()

        @SuppressLint("AccessibilityFocus")
        fragment.root.performAccessibilityAction(
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
            null
        )

        if (fragment is BoardAllAppsBinding) {

            fragment.root.onDrawerShown()

            if (config.showSearchBar && config.autoShowKeyboardInAppDrawer) {
                fragment.root.post {
                    showKeyboard(fragment.searchBarVw.binding.topToolbarSearch)
                }
            }
        }

        binding.homeScreenGridVw.root.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .start()

        Handler(Looper.getMainLooper()).postDelayed({
            updateStatusBarIcons(getProperBackgroundColor())
        }, animationDuration)
    }

    private fun hideFragment(fragment: ViewBinding, animationDuration: Long = ANIMATION_DURATION) {
        ObjectAnimator.ofFloat(fragment.root, "y", mScreenHeight.toFloat()).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            start()
        }

        window.navigationBarColor = Color.TRANSPARENT
        binding.homeScreenGridVw.root.fragmentCollapsed()
        updateStatusBarIcons()
        if (fragment is BoardWidgetsBinding) {
            clearWidgetsSearch()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (fragment is BoardAllAppsBinding) {
                fragment.allAppsGridVw.scrollToPosition(0)
                fragment.root.touchDownY = -1
            } else if (fragment is BoardWidgetsBinding) {
                fragment.widgetsListVw.scrollToPosition(0)
                fragment.root.touchDownY = -1
            }

            showNextSwipeHint()
        }, animationDuration)
    }

    fun homeScreenLongPressed(eventX: Float, eventY: Float) {
        if (isAllAppsFragmentExpanded() || isWidgetsFragmentExpanded() || binding.swipeHintVw.isVisible) {
            return
        }

        val (x, y) = binding.homeScreenGridVw.root.intoViewSpaceCoords(eventX, eventY)
        mIgnoreMoveEvents = true
        val clickedGridItem = binding.homeScreenGridVw.root.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            performItemLongClick(x, clickedGridItem)
            return
        }

        binding.mainHolderVw.performHapticFeedback()
        showMainLongPressMenu(x, y)
    }

    fun homeScreenClicked(eventX: Float, eventY: Float) {

        if (binding.swipeHintVw.isVisible) {
            performHintAction()
            return
        }

        binding.homeScreenGridVw.root.hideResizeLines()
        val (x, y) = binding.homeScreenGridVw.root.intoViewSpaceCoords(eventX, eventY)
        val clickedGridItem = binding.homeScreenGridVw.root.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            performItemClick(clickedGridItem)
        }
        if (clickedGridItem?.type != ITEM_TYPE_FOLDER) {
            binding.homeScreenGridVw.root.closeFolder(redraw = true)
        }
    }

    fun homeScreenDoubleTapped(eventX: Float, eventY: Float) {
        if (binding.swipeHintVw.isVisible) {
            return
        }

        val (x, y) = binding.homeScreenGridVw.root.intoViewSpaceCoords(eventX, eventY)
        val clickedGridItem = binding.homeScreenGridVw.root.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            return
        }

        val devicePolicyManager =
            getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isLockDeviceAdminActive = devicePolicyManager.isAdminActive(
            ComponentName(this, ScreenLockAdminReceiver::class.java)
        )
        if (isLockDeviceAdminActive) {
            devicePolicyManager.lockNow()
        }
    }

    fun closeAppDrawer(delayed: Boolean = false) {
        if (isAllAppsFragmentExpanded()) {
            val close = {
                binding.allAppsFragmentVw.root.y = mScreenHeight.toFloat()
                binding.allAppsFragmentVw.allAppsGridVw.scrollToPosition(0)
                binding.allAppsFragmentVw.root.touchDownY = -1
                binding.homeScreenGridVw.root.fragmentCollapsed()
                updateStatusBarIcons()
            }
            if (delayed) {
                Handler(Looper.getMainLooper()).postDelayed(close, APP_DRAWER_CLOSE_DELAY)
            } else {
                close()
            }
        }
    }

    fun closeWidgetsFragment(delayed: Boolean = false) {
        if (isWidgetsFragmentExpanded()) {
            val close = {
                binding.widgetsFragmentVw.root.y = mScreenHeight.toFloat()
                binding.widgetsFragmentVw.widgetsListVw.scrollToPosition(0)
                clearWidgetsSearch()
                binding.widgetsFragmentVw.root.touchDownY = -1
                binding.homeScreenGridVw.root.fragmentCollapsed()
                updateStatusBarIcons()
            }
            if (delayed) {
                Handler(Looper.getMainLooper()).postDelayed(close, APP_DRAWER_CLOSE_DELAY)
            } else {
                close()
            }
        }
    }

    fun clearWidgetsSearch() {
        binding.widgetsFragmentVw.searchBarVw.closeSearch()
    }

    private fun performItemClick(clickedGridItem: BoardItem) {
        when (clickedGridItem.type) {
            ITEM_TYPE_ICON -> launchApp(clickedGridItem.packageName, clickedGridItem.activityName)
            ITEM_TYPE_FOLDER -> openFolder(clickedGridItem)
            ITEM_TYPE_SHORTCUT -> {
                val id = clickedGridItem.shortcutId
                val packageName = clickedGridItem.packageName
                val userHandle = android.os.Process.myUserHandle()
                val shortcutBounds = binding.homeScreenGridVw.root.getClickableRect(clickedGridItem)
                val launcherApps =
                    applicationContext.getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
                launcherApps.startShortcut(packageName, id, shortcutBounds, null, userHandle)
            }
        }
    }

    private fun openFolder(folder: BoardItem) {
        binding.homeScreenGridVw.root.openFolder(folder)
    }

    private fun performItemLongClick(x: Float, clickedGridItem: BoardItem) {
        if (clickedGridItem.type == ITEM_TYPE_ICON || clickedGridItem.type == ITEM_TYPE_SHORTCUT || clickedGridItem.type == ITEM_TYPE_FOLDER) {
            binding.mainHolderVw.performHapticFeedback()
        }

        val anchorY = binding.homeScreenGridVw.root.sideMargins.top +
                (clickedGridItem.top * binding.homeScreenGridVw.root.cellHeight.toFloat())
        showHomeIconMenu(x, anchorY, clickedGridItem, false)
    }

    fun showHomeIconMenu(
        x: Float,
        y: Float,
        gridItem: BoardItem,
        isOnAllAppsFragment: Boolean,
    ) {
        binding.homeScreenGridVw.root.hideResizeLines()
        mLongPressedIcon = gridItem
        val anchorY = if (isOnAllAppsFragment || gridItem.type == ITEM_TYPE_WIDGET) {
            val iconSize = realScreenSize.x / config.drawerColumnCount
            y - iconSize / 2f
        } else {
            val clickableRect = binding.homeScreenGridVw.root.getClickableRect(gridItem)
            clickableRect.top.toFloat() - binding.homeScreenGridVw.root.getCurrentIconSize() / 2f
        }

        binding.homeScreenPopupMenuAnchorVw.x = x
        binding.homeScreenPopupMenuAnchorVw.y = anchorY

        if (mOpenPopupMenu == null) {
            mOpenPopupMenu = handleGridItemPopupMenu(
                anchorView = binding.homeScreenPopupMenuAnchorVw,
                gridItem = gridItem,
                isOnAllAppsFragment = isOnAllAppsFragment,
                listener = menuListener
            )
        }
    }

    fun widgetLongPressedOnList(gridItem: BoardItem) {
        mLongPressedIcon = gridItem
        hideFragment(binding.widgetsFragmentVw)
        binding.homeScreenGridVw.root.itemDraggingStarted(mLongPressedIcon!!)
    }

    private fun showMainLongPressMenu(x: Float, y: Float) {
        binding.homeScreenGridVw.root.hideResizeLines()
        binding.homeScreenPopupMenuAnchorVw.x = x
        binding.homeScreenPopupMenuAnchorVw.y =
            y - resources.getDimension(R.dimen.long_press_anchor_button_offset_y) * 2
        val contextTheme = ContextThemeWrapper(this, getPopupMenuTheme())
        PopupMenu(
            contextTheme,
            binding.homeScreenPopupMenuAnchorVw,
            Gravity.TOP or Gravity.END
        ).apply {
            inflate(R.menu.menu_home_screen)
            menu.findItem(R.id.set_as_defaultVw).isVisible = !isDefaultLauncher()
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.widgetsVw -> showWidgetsFragment()
                    R.id.wallpapersVw -> launchWallpapersIntent()
                    R.id.launcher_settingsVw -> launchSettings()
                    R.id.set_as_defaultVw -> requestSetAsDefaultLauncher()
                }
                true
            }
            show()
        }
    }

    private fun resetFragmentTouches() {
        binding.widgetsFragmentVw.root.apply {
            touchDownY = -1
            ignoreTouches = false
        }

        binding.allAppsFragmentVw.root.apply {
            touchDownY = -1
            ignoreTouches = false
        }
    }

    private fun showWidgetsFragment() {
        showFragment(binding.widgetsFragmentVw)
    }

    private fun hideIcon(item: BoardItem) {
        ensureBackgroundThread {
            val hiddenIcon = MaskedIcon(null, item.packageName, item.activityName, item.title, null)
            hiddenIconsDB.insert(hiddenIcon)

            runOnUiThread {
                binding.allAppsFragmentVw.root.onIconHidden(item)
            }
        }
    }

    private fun renameItem(homeScreenGridItem: BoardItem) {
        RelabelItemDialog(this, homeScreenGridItem) {
            binding.homeScreenGridVw.root.fetchGridItems()
        }
    }

    private fun launchWallpapersIntent() {
        try {
            Intent(Intent.ACTION_SET_WALLPAPER).apply {
                startActivity(this)
            }
        } catch (_: ActivityNotFoundException) {
            toast(org.fossify.commons.R.string.no_app_found)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun launchSettings() {
        startActivity(
            Intent(this@HomeBoardActivity, BoardSettingsActivity::class.java)
        )
    }

    val menuListener: TileMenuListener = object : TileMenuListener {
        override fun onAnyClick() {
            resetFragmentTouches()
        }

        override fun hide(gridItem: BoardItem) {
            hideIcon(gridItem)
        }

        override fun rename(gridItem: BoardItem) {
            renameItem(gridItem)
        }

        override fun resize(gridItem: BoardItem) {
            binding.homeScreenGridVw.root.widgetLongPressed(gridItem)
        }

        override fun appInfo(gridItem: BoardItem) {
            launchAppInfo(gridItem.packageName)
        }

        override fun remove(gridItem: BoardItem) {
            binding.homeScreenGridVw.root.removeAppIcon(gridItem)
        }

        override fun uninstall(gridItem: BoardItem) {
            uninstallApp(gridItem.packageName)
        }

        override fun onDismiss() {
            mOpenPopupMenu = null
            resetFragmentTouches()
        }

        override fun beforeShow(menu: Menu) {
            var visibleMenuItems = 0
            for (item in menu.iterator()) {
                if (item.isVisible) {
                    visibleMenuItems++
                }
            }
            val yOffset =
                resources.getDimension(R.dimen.long_press_anchor_button_offset_y) * (visibleMenuItems - 1)
            binding.homeScreenPopupMenuAnchorVw.y -= yOffset
        }
    }

    private class MyGestureListener(
        private val flingListener: SwipeListener,
    ) : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            (flingListener as HomeBoardActivity).homeScreenClicked(event.x, event.y)
            return super.onSingleTapUp(event)
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            (flingListener as HomeBoardActivity).homeScreenDoubleTapped(event.x, event.y)
            return super.onDoubleTap(event)
        }

        override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {

            if (System.currentTimeMillis() - mLastUpEvent < 500L) {
                return true
            }

            if (abs(velocityY) > abs(velocityX)) {
                if (velocityY > 0) {
                    flingListener.onFlingDown()
                } else {
                    flingListener.onFlingUp()
                }
            } else if (abs(velocityX) > abs(velocityY)) {
                if (velocityX > 0) {
                    flingListener.onFlingRight()
                } else {
                    flingListener.onFlingLeft()
                }
            }

            return true
        }

        override fun onLongPress(event: MotionEvent) {
            (flingListener as HomeBoardActivity).homeScreenLongPressed(event.x, event.y)
        }
    }

    override fun onFlingUp() {
        if (mIgnoreYMoveEvents) {
            return
        }

        if (!isWidgetsFragmentExpanded()) {
            mIgnoreUpEvent = true
            completeSwipeHint(ShellPromoConfig.HintDirection.UP)
            showFragment(binding.allAppsFragmentVw)
        }
    }

    @SuppressLint("WrongConstant")
    override fun onFlingDown() {
        if (mIgnoreYMoveEvents) {
            return
        }

        mIgnoreUpEvent = true
        if (isAllAppsFragmentExpanded()) {
            hideFragment(binding.allAppsFragmentVw)
        } else if (isWidgetsFragmentExpanded()) {
            hideFragment(binding.widgetsFragmentVw)
        } else {
            completeSwipeHint(ShellPromoConfig.HintDirection.DOWN)
            try {
                Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(getSystemService("statusbar"))
            } catch (_: Exception) {
            }
        }
    }

    /**
     * True when the horizontal gesture in flight is already dragging the home grid between pages.
     *
     * A page drag ends with velocity, so the same gesture also arrives as a fling. Letting the
     * fling claim it set `mIgnoreUpEvent`, which suppressed the [MotionEvent.ACTION_UP] branch
     * that calls `finalizeSwipe()` — the grid stayed frozen part-way between two pages and the
     * abandoned swipe offset was applied to whatever gesture came next. Paging owns the gesture
     * once it has started; the panels still get every fling the grid cannot page on, which is
     * any fling on a single-page home screen, a right fling on the first page and a left fling
     * on the last.
     */
    private fun isPagingTheHomeScreen() = binding.homeScreenGridVw.root.isPageSwipeInProgress()

    override fun onFlingRight() {
        
        if (mIgnoreXMoveEvents || isPagingTheHomeScreen()) {
            return
        }

        mIgnoreUpEvent = true

        if (!isAllAppsFragmentExpanded() && !isWidgetsFragmentExpanded()) {
            completeSwipeHint(ShellPromoConfig.HintDirection.RIGHT)

            ShellPromoConfig.run(this, ShellPromoConfig.Surface.SWIPE_RIGHT) {
                showCallerPanel()
            }
        } else {
            binding.homeScreenGridVw.root.prevPage(redraw = true)
        }
    }

    override fun onFlingLeft() {
        
        if (mIgnoreXMoveEvents || isPagingTheHomeScreen()) {
            return
        }

        mIgnoreUpEvent = true

        if (!isAllAppsFragmentExpanded() && !isWidgetsFragmentExpanded()) {
            completeSwipeHint(ShellPromoConfig.HintDirection.LEFT)

            ShellPromoConfig.run(this, ShellPromoConfig.Surface.SWIPE_LEFT) {
                showLeftPanel()
            }
        } else {
            binding.homeScreenGridVw.root.nextPage(redraw = true)
        }
    }

    fun isCallerPanelExpanded() = binding.callerPanelVw.root.x != -mScreenWidth.toFloat()

    fun showCallerPanelExternally() = showCallerPanel()

    private fun showCallerPanel() {
        showSidePanel(binding.callerPanelVw.root)

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isCallerPanelExpanded()) return@postDelayed
            binding.callerPanelVw.root.onPanelOpened()
            binding.callerPanelVw.root.shell()?.setPanelVisible(true)
            if (OnboardRouter.wasOnboardingCompleted(this)) {
                homeShellController.startFirstRunPriming()
            }
        }, ANIMATION_DURATION)
    }

    fun hideCallerPanel() {
        
        homeShellController.onShellHidden()

        binding.callerPanelVw.root.shell()?.setPanelVisible(false)

        hideSidePanel(binding.callerPanelVw.root, -mScreenWidth.toFloat()) { showNextSwipeHint() }
    }

    @SuppressLint("WrongConstant")
    fun getAllAppLaunchers(): ArrayList<AppTile> {
        val hiddenIcons = hiddenIconsDB.getHiddenIcons().map {
            it.getIconIdentifier()
        }

        val allApps = ArrayList<AppTile>()
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val simpleLauncher = applicationContext.packageName
        val microG = "com.google.android.gms"
        val list = packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
        for (info in list) {
            val componentInfo = info.activityInfo.applicationInfo
            val packageName = componentInfo.packageName
            if (packageName == simpleLauncher || packageName == microG) {
                continue
            }

            val activityName = info.activityInfo.name
            if (hiddenIcons.contains("$packageName/$activityName")) {
                continue
            }

            val label = info.loadLabel(packageManager).toString()
            val drawable = info.loadIcon(packageManager)
                ?: getDrawableForPackageName(packageName)
                ?: continue

            val bitmap = drawable.toBitmap(
                width = max(drawable.intrinsicWidth, 1),
                height = max(drawable.intrinsicHeight, 1),
                config = Bitmap.Config.ARGB_8888
            )
            val placeholderColor = calculateAverageColor(bitmap)
            allApps.add(
                AppTile(
                    id = null,
                    title = label,
                    packageName = packageName,
                    activityName = activityName,
                    order = 0,
                    thumbnailColor = placeholderColor,
                    drawable = bitmap.toDrawable(resources)
                )
            )
        }

        launchersDB.insertAll(allApps)
        return allApps
    }

    /**
     * Puts the clock and the search pill on page 0 the first time the home screen is built.
     *
     * Per widget, and only marked done once the widget is actually there. The first version
     * set both flags before placing anything and gave up on the clock entirely whenever the
     * top rows were not free — so one crowded first run, or one failure to allocate a widget
     * id, left the home screen with a search pill and no clock, permanently: the flags said
     * the job was finished. Anything that does not get placed here is retried on the next
     * launch instead.
     */
    private fun seedHomeWidgetsIfNeeded() {
        try {
            val lastColumn = config.homeColumnCount - 1
            
            val dockRow = config.homeRowCount - 1

            val pageItems = homeScreenGridItemsDB.getAllItems()
                .filter { it.page == 0 && !it.docked && it.parentId == null }
                .toMutableList()

            repairSeedFlagsOnce(pageItems)

            if (config.wasSearchBarSeeded && config.wasClockSeeded) {
                return
            }

            
            val occupiedRows = pageItems
                .filter { it.className != PSEUDO_WIDGET_CLOCK && it.className != PSEUDO_WIDGET_SEARCH }
                .flatMap { it.top..it.bottom }
                .toMutableSet()

            if (!config.wasClockSeeded) {
                val existing = pageItems.firstOrNull { it.className == PSEUDO_WIDGET_CLOCK }
                if (existing != null) {
                    
                    config.wasClockSeeded = true
                    occupiedRows += existing.top..existing.bottom
                } else {
                    val top = firstFreeRowBand(occupiedRows, CLOCK_ROW_SPAN, dockRow)
                    if (top != null) {
                        val bottom = top + CLOCK_ROW_SPAN - 1
                        runCatching {
                            insertPseudoWidget(
                                className = PSEUDO_WIDGET_CLOCK,
                                titleRes = R.string.pseudo_widget_clock,
                                left = 0,
                                top = top,
                                right = lastColumn,
                                bottom = bottom,
                            )
                        }.onSuccess {
                            config.wasClockSeeded = true
                            occupiedRows += top..bottom
                        }.onFailure {
                            Log.e(TAG, "clock could not be seeded — retrying next launch", it)
                        }
                    } else {
                        Log.w(TAG, "no free rows for the clock — retrying next launch")
                    }
                }
            }

            if (!config.wasSearchBarSeeded) {
                val existing = pageItems.firstOrNull { it.className == PSEUDO_WIDGET_SEARCH }
                if (existing != null) {
                    config.wasSearchBarSeeded = true
                } else {
                    
                    val row = SEARCH_BAR_ROW.takeIf { it < dockRow && it !in occupiedRows }
                        ?: firstFreeRowBand(occupiedRows, rows = 1, dockRow = dockRow)
                    if (row != null) {
                        runCatching {
                            insertPseudoWidget(
                                className = PSEUDO_WIDGET_SEARCH,
                                titleRes = R.string.pseudo_widget_search_bar,
                                left = 0,
                                top = row,
                                right = lastColumn,
                                bottom = row,
                            )
                        }.onSuccess {
                            config.wasSearchBarSeeded = true
                            occupiedRows += row
                        }.onFailure {
                            Log.e(TAG, "search bar could not be seeded — retrying next launch", it)
                        }
                    } else {
                        Log.w(TAG, "no free row for the search bar — retrying next launch")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seed default home widgets", e)
        }
    }

    /**
     * Undoes the old seeding bug's verdict, once per install.
     *
     * That version set both flags before placing anything, so a first run that failed — the
     * top rows occupied, or one widget-id allocation throwing — recorded "seeded" for a home
     * screen that never got a clock, and no later launch would try again. This clears the flag
     * for a pseudo-widget that is genuinely not on page 0, which lets the seeding below place
     * it, and then marks the repair done so it never second-guesses the user again.
     *
     * The cost is one case: someone who deliberately deleted the clock or the search pill
     * before this build gets it back a single time. Worth it against a home screen that is
     * permanently missing the thing every other launcher has.
     */
    private fun repairSeedFlagsOnce(pageItems: List<BoardItem>) {
        if (config.wasHomeWidgetsRepaired) {
            return
        }
        config.wasHomeWidgetsRepaired = true

        if (config.wasClockSeeded && pageItems.none { it.className == PSEUDO_WIDGET_CLOCK }) {
            Log.i(TAG, "clock marked seeded but missing — seeding it again")
            config.wasClockSeeded = false
        }
        if (config.wasSearchBarSeeded && pageItems.none { it.className == PSEUDO_WIDGET_SEARCH }) {
            Log.i(TAG, "search bar marked seeded but missing — seeding it again")
            config.wasSearchBarSeeded = false
        }
    }

    /**
     * The top row of the first run of [rows] consecutive free rows above the dock, or null when
     * the page has no room for it. Both pseudo-widgets are full width, so a row is either free
     * or it is not — there is no column to search.
     */
    private fun firstFreeRowBand(occupiedRows: Set<Int>, rows: Int, dockRow: Int): Int? {
        var top = 0
        while (top + rows - 1 < dockRow) {
            if ((top until top + rows).none { it in occupiedRows }) {
                return top
            }
            top++
        }
        return null
    }

    private fun insertPseudoWidget(
        className: String,
        titleRes: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val item = BoardItem(
            id = null,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            page = 0,
            packageName = packageName,
            activityName = "",
            title = getString(titleRes),
            type = ITEM_TYPE_WIDGET,
            className = className,
            widgetId = binding.homeScreenGridVw.root.appWidgetHost.allocateAppWidgetId(),
            shortcutId = "",
            icon = null,
            docked = false,
            parentId = null
        )

        homeScreenGridItemsDB.insert(item)
    }

    private fun getDefaultAppPackages(appLaunchers: ArrayList<AppTile>) {
        val homeScreenGridItems = ArrayList<BoardItem>()

        try {
            val defaultDialerPackage =
                (getSystemService(TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
            appLaunchers.firstOrNull { it.packageName == defaultDialerPackage }?.apply {
                val dialerIcon =
                    BoardItem(
                        id = null,
                        left = 0,
                        top = config.homeRowCount - 1,
                        right = 0,
                        bottom = config.homeRowCount - 1,
                        page = 0,
                        packageName = defaultDialerPackage,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                homeScreenGridItems.add(dialerIcon)
            }
        } catch (_: Exception) {
        }

        try {
            val defaultSMSMessengerPackage = Telephony.Sms.getDefaultSmsPackage(this)
            appLaunchers.firstOrNull { it.packageName == defaultSMSMessengerPackage }?.apply {
                val messengerIcon =
                    BoardItem(
                        id = null,
                        left = 1,
                        top = config.homeRowCount - 1,
                        right = 1,
                        bottom = config.homeRowCount - 1,
                        page = 0,
                        packageName = defaultSMSMessengerPackage,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                homeScreenGridItems.add(messengerIcon)
            }
        } catch (_: Exception) {
        }

        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, "http://".toUri())
            val resolveInfo =
                packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultBrowserPackage = resolveInfo!!.activityInfo.packageName
            appLaunchers.firstOrNull { it.packageName == defaultBrowserPackage }?.apply {
                val browserIcon =
                    BoardItem(
                        id = null,
                        left = 2,
                        top = config.homeRowCount - 1,
                        right = 2,
                        bottom = config.homeRowCount - 1,
                        page = 0,
                        packageName = defaultBrowserPackage,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                homeScreenGridItems.add(browserIcon)
            }
        } catch (_: Exception) {
        }

        try {
            val potentialStores = arrayListOf(
                "com.android.vending", "org.fdroid.fdroid", "com.aurora.store"
            )
            val storePackage = potentialStores.firstOrNull {
                isPackageInstalled(it) && appLaunchers.map { it.packageName }.contains(it)
            }
            if (storePackage != null) {
                appLaunchers.firstOrNull { it.packageName == storePackage }?.apply {
                    val storeIcon = BoardItem(
                        id = null,
                        left = 3,
                        top = config.homeRowCount - 1,
                        right = 3,
                        bottom = config.homeRowCount - 1,
                        page = 0,
                        packageName = storePackage,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                    homeScreenGridItems.add(storeIcon)
                }
            }
        } catch (_: Exception) {
        }

        try {
            val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE")
            val resolveInfo =
                packageManager.resolveActivity(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultCameraPackage = resolveInfo!!.activityInfo.packageName
            appLaunchers.firstOrNull { it.packageName == defaultCameraPackage }?.apply {
                val cameraIcon =
                    BoardItem(
                        id = null,
                        left = 4,
                        top = config.homeRowCount - 1,
                        right = 4,
                        bottom = config.homeRowCount - 1,
                        page = 0,
                        packageName = defaultCameraPackage,
                        activityName = "",
                        title = title,
                        type = ITEM_TYPE_ICON,
                        className = "",
                        widgetId = -1,
                        shortcutId = "",
                        icon = null,
                        docked = true,
                        parentId = null
                    )
                homeScreenGridItems.add(cameraIcon)
            }
        } catch (_: Exception) {
        }

        homeScreenGridItemsDB.insertAll(homeScreenGridItems)
    }

    fun handleWidgetBinding(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        appWidgetInfo: AppWidgetProviderInfo,
        callback: (canBind: Boolean) -> Unit,
    ) {
        mActionOnCanBindWidget = null
        val canCreateWidget =
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, appWidgetInfo.provider)
        if (canCreateWidget) {
            callback(true)
        } else {
            mActionOnCanBindWidget = callback
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, appWidgetInfo.provider)
                startActivityForResult(this, REQUEST_ALLOW_BINDING_WIDGET)
            }
        }
    }

    fun handleWidgetConfigureScreen(
        appWidgetHost: AppWidgetHost,
        appWidgetId: Int,
        callback: (canBind: Boolean) -> Unit,
    ) {
        mActionOnWidgetConfiguredWidget = callback
        appWidgetHost.startAppWidgetConfigureActivityForResult(
            this,
            appWidgetId,
            0,
            REQUEST_CONFIGURE_WIDGET,
            null
        )
    }

    fun handleShorcutCreation(
        activityInfo: ActivityInfo,
        callback: (shortcutId: String, label: String, icon: Drawable) -> Unit,
    ) {
        mActionOnAddShortcut = callback
        val componentName = ComponentName(activityInfo.packageName, activityInfo.name)
        Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
            component = componentName
            startActivityForResult(this, REQUEST_CREATE_SHORTCUT)
        }
    }

    private fun updateStatusBarIcons(backgroundColor: Int? = null) {
        val isLightBackground = when {
            backgroundColor != null -> backgroundColor.getContrastColor() == DARK_GREY
            wallpaperSupportsDarkText != null -> wallpaperSupportsDarkText!!
            else -> {
                refreshWallpaperSupportsDarkText()
                wallpaperSupportsDarkText ?: false
            }
        }
        window.insetsController().apply {
            isAppearanceLightStatusBars = isLightBackground
            isAppearanceLightNavigationBars = isLightBackground
        }
    }

    private fun calculateAverageColor(bitmap: Bitmap): Int {
        var red = 0
        var green = 0
        var blue = 0
        val height = bitmap.height
        val width = bitmap.width
        var n = 0
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var i = 0
        while (i < pixels.size) {
            val color = pixels[i]
            red += Color.red(color)
            green += Color.green(color)
            blue += Color.blue(color)
            n++
            i += 1
        }

        return Color.rgb(red / n, green / n, blue / n)
    }
}
