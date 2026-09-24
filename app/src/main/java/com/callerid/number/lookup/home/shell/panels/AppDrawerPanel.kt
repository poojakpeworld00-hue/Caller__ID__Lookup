package com.callerid.number.lookup.home.shell.panels

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.views.MyGridLayoutManager
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.surface.InlinePromo
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.shell.screens.HomeBoardActivity
import com.callerid.number.lookup.home.shell.lists.AppTileAdapter
import com.callerid.number.lookup.home.databinding.BoardAllAppsBinding
import com.callerid.number.lookup.home.shell.ext.applyDrawerSkin
import com.callerid.number.lookup.home.shell.ext.config
import com.callerid.number.lookup.home.shell.ext.launchApp
import com.callerid.number.lookup.home.shell.ext.setupDrawerBackground
import com.callerid.number.lookup.home.shell.support.ITEM_TYPE_ICON
import com.callerid.number.lookup.home.shell.contracts.DrawerListener
import com.callerid.number.lookup.home.shell.entities.AppTile
import com.callerid.number.lookup.home.shell.entities.BoardItem
import com.callerid.number.lookup.home.shell.entities.appLauncherComparator

class AppDrawerPanel(
    context: Context,
    attributeSet: AttributeSet
) : BasePanel<BoardAllAppsBinding>(context, attributeSet), DrawerListener {

    private var lastTouchCoords = Pair(0f, 0f)
    var touchDownY = -1
    var ignoreTouches = false

    private var launchers = emptyList<AppTile>()
    private val nativePromo = InlinePromo()

    private var adSlot = ShellPromoConfig.Slot(
        enabled = false,
        adType = ShellPromoConfig.SlotAd.NONE,
        nativeType = "mid2",
        bannerType = "adaptive",
        adUnitId = "",
    )

    /** The measured height of the native ad, and the grid's own bottom padding before the ad. */
    private var adHeightPx = 0
    private var basePaddingBottom = -1

    /**
     * The navigation-bar (and gesture) inset. The drawer is edge-to-edge, so without reserving it
     * the docked ad drew partly under the navigation bar and the grid's bottom padding fell short by
     * that much — which is what left the last rows of icons stranded behind the ad. Kept separate
     * from [basePaddingBottom] so the ad-height maths stays clean.
     */
    private var navBottomInset = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(activity: HomeBoardActivity) {
        this.activity = activity
        this.binding = BoardAllAppsBinding.bind(this)

        binding.allAppsGridVw.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }

        adSlot = ShellPromoConfig.drawerSlot(activity)
        if (adSlot.needsNativePreload) {
            nativePromo.fetchNativeAds(activity)
        }

        if (basePaddingBottom < 0) basePaddingBottom = binding.allAppsGridVw.paddingBottom

        // Reserve the navigation-bar inset for the grid and re-dock the ad above it, so the last
        // rows of icons can scroll clear of both the ad and the system bar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.allAppsGridVw) { _, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            if (nav != navBottomInset) {
                navBottomInset = nav
                updateGridBottomPadding()
                syncStickyAd()
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.allAppsGridVw)

        // Keep the sticky ad in step with the scroll: it tracks the gap row while that row is on
        // screen and docks to the bottom once the gap scrolls off the top.
        binding.allAppsGridVw.addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = syncStickyAd()
        })

        // A native loads its media asynchronously and grows the card after the first layout, so
        // this fires on every height change — including back down to nothing when the render path
        // gives up, which is what has to close the gap again.
        binding.adNativeFrameVw.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            syncAdGap()
        }
    }

    /** Focuses the drawer's search box and lifts the keyboard — the home "Search apps" pill's target. */
    fun focusSearch() {
        if (!context.config.showSearchBar) return
        val input = binding.searchBarVw.binding.topToolbarSearch
        input.requestFocus()
        activity?.showKeyboard(input)
    }

    fun onDrawerShown() {
        val activity = activity ?: return
        refreshNavInset()
        // Warm the app-tap ad sequence so a format is ready by the time an app is tapped.
        ShellPromoConfig.preloadDrawerAds(activity)
        refreshSlot(activity)
        if (!adSlot.visible) {
            // Slot turned off (an RC change, or the default-home variant flipping) — give the space
            // back rather than leaving yesterday's gap in the list.
            onAdMeasured(0)
            return
        }
        // Invisible, not gone, so the frame lays out and the native inside can measure; syncStickyAd
        // flips it visible once it is placed.
        if (binding.adNativeFrameVw.visibility == View.GONE) {
            binding.adNativeFrameVw.visibility = View.INVISIBLE
        }
        ShellPromoConfig.refreshSlot(activity, adSlot, binding.adNativeFrameVw, binding.adShimmerVw)
        // The render path posts its work, and a re-render whose card is the same height as the last
        // one changes no bounds — so reconcile once more after it has run rather than relying only
        // on the layout listener.
        binding.adNativeFrameVw.post { syncAdGap() }
        syncStickyAd()
    }

    /**
     * Fallback for [navBottomInset]: a parent that consumes the insets can stop the grid's listener
     * ever firing, so read them off the root window each time the drawer opens.
     */
    private fun refreshNavInset() {
        val nav = ViewCompat.getRootWindowInsets(binding.allAppsGridVw)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: return
        if (nav == navBottomInset) return
        navBottomInset = nav
        updateGridBottomPadding()
    }

    private fun refreshSlot(activity: HomeBoardActivity) {
        val fresh = ShellPromoConfig.drawerSlot(activity)
        if (fresh == adSlot) return

        adSlot = fresh
        if (adSlot.needsNativePreload) nativePromo.fetchNativeAds(activity)
    }

    /**
     * True only when the frame holds a *rendered* ad, rather than the shimmer placeholder that the
     * layout ships with.
     *
     * This is the whole point of the gap: the frame's XML child (the shimmer wrapping the mid-native
     * template) measures full height the moment the drawer lays out, long before anyone knows
     * whether an ad will fill. Sizing the gap from that left a hole the size of an ad card with
     * nothing in it whenever the render path bailed out — no network, no fill, or simply the
     * `MidNativeCounter` skip — because those paths call `removeAllViews()` and leave the frame
     * empty.
     */
    private fun frameHoldsAd(): Boolean {
        val frame = binding.adNativeFrameVw
        for (i in 0 until frame.childCount) {
            val child = frame.getChildAt(i)
            if (child !== binding.adShimmerVw && child.visibility == View.VISIBLE) return true
        }
        return false
    }

    /**
     * Reconciles the gap row with what the ad frame actually holds: the ad's measured height when
     * there is one, nothing at all when there isn't. Called on every frame layout, so it both opens
     * the gap once the native lands and closes it again if the ad goes away.
     */
    private fun syncAdGap() {
        val height = if (adSlot.visible && frameHoldsAd()) binding.adNativeFrameVw.height else 0
        onAdMeasured(height)
    }

    /**
     * Sizes the gap row and the grid's bottom padding to the ad, then places the overlay. A height
     * of 0 closes the gap and hides the overlay, so no ad means no reserved space.
     */
    private fun onAdMeasured(height: Int) {
        if (height == adHeightPx) {
            syncStickyAd()
            return
        }
        adHeightPx = height
        if (height <= 0) {
            getAdapter()?.setAdGap(false, AD_ROW, 0)
            updateGridBottomPadding()
            binding.adNativeFrameVw.visibility = View.INVISIBLE
            return
        }
        // Gap row from Remote Config (`app_drawer.bottom_native.position`), falling back to the
        // default first-row placement when it is left at 0.
        getAdapter()?.setAdGap(true, adSlot.position.takeIf { it > 0 } ?: AD_ROW, adHeightPx)
        updateGridBottomPadding()
        binding.allAppsGridVw.post { syncStickyAd() }
    }

    /** Grid bottom padding = its own base + the nav-bar inset + the docked ad's height. Without the
     *  nav inset the reserved space fell short and the last rows sat behind the ad / the system bar. */
    private fun updateGridBottomPadding() {
        val grid = binding.allAppsGridVw
        grid.setPadding(
            grid.paddingLeft,
            grid.paddingTop,
            grid.paddingRight,
            basePaddingBottom.coerceAtLeast(0) + navBottomInset + adHeightPx,
        )
    }

    /**
     * Positions the sticky ad overlay each scroll/layout: aligned to the gap row (clipped to its
     * visible part) while the gap is on screen, docked to the bottom once the gap has scrolled off
     * the top, and hidden while the gap is still below the fold or a search is in progress.
     */
    private fun syncStickyAd() {
        val rv = binding.allAppsGridVw
        val overlay = binding.adNativeFrameVw
        val adapter = getAdapter()
        if (!adSlot.visible || adHeightPx <= 0 || adapter == null ||
            binding.searchBarVw.getCurrentQuery().isNotEmpty()
        ) {
            overlay.visibility = View.INVISIBLE
            return
        }

        val adPos = adapter.adGapPosition()
        // The visible floor, not the raw height: below this the ad would be under the navigation bar.
        val recyclerH = rv.height - navBottomInset
        if (adPos < 0 || recyclerH <= 0) {
            overlay.visibility = View.INVISIBLE
            return
        }

        val holder = rv.findViewHolderForAdapterPosition(adPos)
        if (holder != null) {
            val slotTop = holder.itemView.top
            val slotBottom = slotTop + adHeightPx
            if (slotBottom > rv.paddingTop && slotTop < recyclerH) {
                val topClip = maxOf(0, -slotTop)
                val bottomClip = minOf(adHeightPx, recyclerH - slotTop)
                if (bottomClip <= topClip) {
                    dockStickyAdToBottom()
                    return
                }
                overlay.translationY = slotTop.toFloat()
                val width = if (overlay.width > 0) overlay.width else rv.width
                overlay.clipBounds = Rect(0, topClip, width, bottomClip)
                overlay.visibility = View.VISIBLE
                return
            }
            if (slotBottom <= rv.paddingTop) {
                dockStickyAdToBottom()
                return
            }
        }

        // Gap not laid out: dock once it is above the first visible item (scrolled past), hide
        // while it is still below (not scrolled to yet).
        val firstVisible = (rv.layoutManager as? MyGridLayoutManager)?.findFirstVisibleItemPosition() ?: -1
        if (firstVisible == -1 || firstVisible <= adPos) {
            overlay.visibility = View.INVISIBLE
        } else {
            dockStickyAdToBottom()
        }
    }

    private fun dockStickyAdToBottom() {
        val rv = binding.allAppsGridVw
        val overlay = binding.adNativeFrameVw
        // Dock above the navigation bar, matching the grid's reserved bottom inset, so the ad never
        // sits under the system bar and the last rows clear it cleanly.
        overlay.translationY = maxOf(0, rv.height - adHeightPx - navBottomInset).toFloat()
        overlay.clipBounds = null
        overlay.visibility = View.VISIBLE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupDrawerBackground(context.getColor(R.color.all_app_bg))
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onResume() {
        if (binding.allAppsGridVw.layoutManager == null || binding.allAppsGridVw.adapter == null) {
            return
        }

        val layoutManager = binding.allAppsGridVw.layoutManager as MyGridLayoutManager
        if (layoutManager.spanCount != context.config.drawerColumnCount) {
            onConfigurationChanged()

            (binding.allAppsGridVw.adapter as AppTileAdapter).notifyDataSetChanged()
        }
    }

    fun onConfigurationChanged() {
        binding.allAppsGridVw.scrollToPosition(0)
        binding.allAppsFastscrollerVw.resetManualScrolling()
        setupViews()

        val layoutManager = binding.allAppsGridVw.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = context.config.drawerColumnCount
        setupAdapter(launchers)
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onInterceptTouchEvent(event)
        }

        var shouldIntercept = false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = event.y.toInt()
            }

            MotionEvent.ACTION_MOVE -> {
                if (ignoreTouches) {

                    if (lastTouchCoords.first != event.x || lastTouchCoords.second != event.y) {
                        touchDownY = -1
                        return true
                    }
                }

                if (touchDownY != -1) {
                    val distance = event.y.toInt() - touchDownY
                    shouldIntercept =
                        distance > 0 && binding.allAppsGridVw.computeVerticalScrollOffset() == 0
                    if (shouldIntercept) {

                        if (binding.searchBarVw.hasFocus()) {
                            activity?.hideKeyboard()
                        }
                        activity?.startHandlingTouches(touchDownY)
                        touchDownY = -1
                    }
                }
            }
        }

        lastTouchCoords = Pair(event.x, event.y)
        return shouldIntercept
    }

    fun gotLaunchers(appLaunchers: List<AppTile>) {
        launchers = appLaunchers.sortedWith(appLauncherComparator)

        setupAdapter(launchers)
    }

    private fun getAdapter() = binding.allAppsGridVw.adapter as? AppTileAdapter

    private fun setupAdapter(launchers: List<AppTile>) {
        activity?.runOnUiThread {
            val layoutManager = binding.allAppsGridVw.layoutManager as MyGridLayoutManager
            layoutManager.spanCount = context.config.drawerColumnCount

            if (getAdapter() == null) {
                AppTileAdapter(activity!!, this) { clicked ->
                    val host = activity
                    val launcher = clicked as AppTile

                    val openApp = {
                        host?.launchApp(launcher.packageName, launcher.activityName)
                        if (host?.config?.closeAppDrawer == true) {
                            host.closeAppDrawer(delayed = true)
                        }
                        ignoreTouches = false
                        touchDownY = -1
                    }

                    // The click-time gate (`app_drawer.click`), off unless Remote Config turns it
                    // on. When it is off this is a straight openApp() and the only ad is the one
                    // launchApp arms for the way back; when it is on, both fire — the app opens
                    // after the click ad closes, and the return ad still shows on the way back.
                    // That is how the reference splits it per cohort (appLaunchInterEnabled).
                    if (host == null) openApp()
                    else ShellPromoConfig.runDrawerClickAdFlow(host) { openApp() }
                }.apply {
                    binding.allAppsGridVw.itemAnimator = null
                    binding.allAppsGridVw.adapter = this
                }
            }

            layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (getAdapter()?.isAdRow(position) == true) layoutManager.spanCount else 1
            }

            submitList(launchers.toMutableList())
        }
    }

    fun onIconHidden(item: BoardItem) {
        val itemToRemove = launchers.firstOrNull {
            it.getLauncherIdentifier() == item.getItemIdentifier()
        }

        if (itemToRemove != null) {
            val position = launchers.indexOfFirst {
                it.getLauncherIdentifier() == item.getItemIdentifier()
            }

            launchers = launchers.toMutableList().apply {
                removeAt(position)
            }

            submitList(launchers.toMutableList())
        }
    }

    fun setupViews() {
        if (activity == null) {
            return
        }

        binding.allAppsFastscrollerVw.updateColors(context.getProperPrimaryColor())
        binding.allAppsGridVw.addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {

                if (binding.searchBarVw.hasFocus() && dy > 0 && binding.allAppsGridVw.computeVerticalScrollOffset() > 0) {
                    activity?.hideKeyboard()
                }
            }
        })

        setupDrawerBackground(context.getColor(R.color.all_app_bg))

        binding.searchBarVw.beVisibleIf(context.config.showSearchBar)
        binding.searchBarVw.requireToolbar().beGone()
        binding.searchBarVw.updateColors()
        binding.searchBarVw.applyDrawerSkin()
        binding.searchBarVw.setupMenu()

        binding.searchBarVw.onSearchTextChangedListener = {
            submitList(launchers)
        }

        binding.searchBarVw.binding.topToolbarSearch.setOnEditorActionListener { _, actionId, _ ->
            if (binding.searchBarVw.getCurrentQuery().isEmpty()) return@setOnEditorActionListener false
            when (actionId) {
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_GO -> getAdapter()?.launchFirstApp() == true
                else -> false
            }
        }
    }

    private fun showNoResultsPlaceholderIfNeeded() {
        val itemCount = getAdapter()?.itemCount
        binding.noResultsPlaceholderVw.beVisibleIf(itemCount != null && itemCount == 0)
    }

    override fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppTile) {
        val gridItem = BoardItem(
            id = null,
            left = -1,
            top = -1,
            right = -1,
            bottom = -1,
            page = 0,
            packageName = appLauncher.packageName,
            activityName = appLauncher.activityName,
            title = appLauncher.title,
            type = ITEM_TYPE_ICON,
            className = "",
            widgetId = -1,
            shortcutId = "",
            icon = null,
            docked = false,
            parentId = null,
            drawable = appLauncher.drawable
        )

        activity?.showHomeIconMenu(x, y, gridItem, true)
        ignoreTouches = true

        binding.searchBarVw.closeSearch()
    }

    fun onBackPressed(): Boolean {
        if (binding.searchBarVw.isSearchOpen) {
            binding.searchBarVw.closeSearch()
            return true
        }

        return false
    }

    private companion object {
        /**
         * The grid row the ad gap opens under — one, i.e. below the first complete row of icons,
         * matching the reference launcher's default drawer-ad position.
         */
        const val AD_ROW = 1
    }

    private fun submitList(items: List<AppTile>) {
        val searchQuery = binding.searchBarVw.getCurrentQuery()
        val filtered = if (searchQuery.isNotEmpty()) {
            items.filter {
                it.title.normalizeString()
                    .contains(searchQuery.normalizeString(), ignoreCase = true)
            }
        } else {
            withPromoTiles(items)
        }

        getAdapter()?.submitList(filtered) {
            showNoResultsPlaceholderIfNeeded()
        }
    }

    /**
     * Inserts the Remote-Config promo tiles at their configured positions among the apps. Only
     * called for the unfiltered list, so promos never show while searching. Each promo is a
     * single-span tile like an app, so the grid and the swipe gestures are unaffected.
     */
    private fun withPromoTiles(apps: List<AppTile>): List<AppTile> {
        val host = activity ?: return apps
        val promos = ShellPromoConfig.drawerPromoItems(host)
        if (promos.isEmpty()) return apps

        val out = apps.toMutableList()
        promos.sortedBy { it.position }.forEach { promo ->
            val index = promo.position.coerceIn(0, out.size)
            out.add(
                index,
                AppTile(
                    id = null,
                    title = promo.title,
                    packageName = "promo:${promo.link}",
                    activityName = "",
                    order = 0,
                    thumbnailColor = 0,
                    drawable = null,
                    isPromo = true,
                    iconUrl = promo.icon,
                    link = promo.link,
                )
            )
        }
        return out
    }
}
