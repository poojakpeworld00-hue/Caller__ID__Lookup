package com.callerid.number.lookup.home.launcher.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.views.MyGridLayoutManager
import com.callerid.admesh.domain.ShellPromoConfig
import com.callerid.admesh.presentation.InlinePromo
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.launcher.activities.HomeBoardActivity
import com.callerid.number.lookup.home.launcher.adapters.AppTileAdapter
import com.callerid.number.lookup.home.databinding.BoardAllAppsBinding
import com.callerid.number.lookup.home.launcher.extensions.applyDrawerSkin
import com.callerid.number.lookup.home.launcher.extensions.config
import com.callerid.number.lookup.home.launcher.extensions.launchApp
import com.callerid.number.lookup.home.launcher.extensions.setupDrawerBackground
import com.callerid.number.lookup.home.launcher.helpers.ITEM_TYPE_ICON
import com.callerid.number.lookup.home.launcher.interfaces.DrawerListener
import com.callerid.number.lookup.home.launcher.models.AppTile
import com.callerid.number.lookup.home.launcher.models.BoardItem
import com.callerid.number.lookup.home.launcher.models.appLauncherComparator

class AppDrawerPanel(
    context: Context,
    attributeSet: AttributeSet
) : BasePanel<BoardAllAppsBinding>(context, attributeSet), DrawerListener {

    private var lastTouchCoords = Pair(0f, 0f)
    var touchDownY = -1
    var ignoreTouches = false

    private var launchers = emptyList<AppTile>()
    private val nativePromo = InlinePromo()

    /** `launcher_ads.app_drawer.bottom_native`, resolved once with the drawer. */
    private var adSlot = ShellPromoConfig.Slot(
        enabled = false,
        adType = ShellPromoConfig.SlotAd.NONE,
        nativeType = "mid2",
        bannerType = "adaptive",
        adUnitId = "",
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(activity: HomeBoardActivity) {
        this.activity = activity
        this.binding = BoardAllAppsBinding.bind(this)

        binding.allAppsGrid.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }

        // Warm the slot only — the renderers draw what is already preloaded, and at this point
        // nothing is. The show happens in onDrawerShown(), each time the drawer comes up.
        adSlot = ShellPromoConfig.appDrawerSlot(activity)
        if (adSlot.needsNativePreload) {
            nativePromo.loadNativeADs(activity)
        }

        // The frame is declared in the layout for the view binding, but it belongs to the app
        // list: lifted out here and handed to the adapter as row 0, so it scrolls away with the
        // apps instead of holding a strip of the drawer permanently.
        (binding.adNativeFrame.parent as? ViewGroup)?.removeView(binding.adNativeFrame)
    }

    /** The ad frame the adapter carries as a list row — null when the slot is switched off. */
    private fun adHeaderView(): View? = binding.adNativeFrame.takeIf { adSlot.visible }

    /** Called every time the drawer is flung open. */
    fun onDrawerShown() {
        val activity = activity ?: return
        refreshSlot(activity)
        ShellPromoConfig.showSlot(activity, adSlot, binding.adNativeFrame, binding.adShimmer)
    }

    /**
     * Re-reads `app_drawer.bottom_native` before showing.
     *
     * The slot used to be resolved once in [setupFragment], which runs in the launcher's
     * onCreate — and a home-screen activity is effectively never recreated, so a Remote LauncherPrefs
     * change to the row position, ad type or enabled flag never reached a running device no
     * matter how promptly the blob itself was updated.
     *
     * Only the row hand-off is guarded, since [AppTileAdapter.setAdSlot] rebuilds the grid.
     */
    private fun refreshSlot(activity: HomeBoardActivity) {
        val fresh = ShellPromoConfig.appDrawerSlot(activity)
        if (fresh == adSlot) return

        adSlot = fresh
        if (adSlot.needsNativePreload) nativePromo.loadNativeADs(activity)
        (binding.allAppsGrid.adapter as? AppTileAdapter)?.setAdSlot(adHeaderView(), adSlot.position)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupDrawerBackground(context.getColor(R.color.all_app_bg))
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onResume() {
        if (binding.allAppsGrid.layoutManager == null || binding.allAppsGrid.adapter == null) {
            return
        }

        val layoutManager = binding.allAppsGrid.layoutManager as MyGridLayoutManager
        if (layoutManager.spanCount != context.config.drawerColumnCount) {
            onConfigurationChanged()
            // Force redraw due to changed item size
            (binding.allAppsGrid.adapter as AppTileAdapter).notifyDataSetChanged()
        }
    }

    fun onConfigurationChanged() {
        binding.allAppsGrid.scrollToPosition(0)
        binding.allAppsFastscroller.resetManualScrolling()
        setupViews()

        val layoutManager = binding.allAppsGrid.layoutManager as MyGridLayoutManager
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
                    // some devices ACTION_MOVE keeps triggering for the whole long press duration, but we are interested in real moves only, when coords change
                    if (lastTouchCoords.first != event.x || lastTouchCoords.second != event.y) {
                        touchDownY = -1
                        return true
                    }
                }

                // pull the whole fragment down if it is scrolled way to the top and the user pulls it even further
                if (touchDownY != -1) {
                    val distance = event.y.toInt() - touchDownY
                    shouldIntercept =
                        distance > 0 && binding.allAppsGrid.computeVerticalScrollOffset() == 0
                    if (shouldIntercept) {
                        // Hiding is expensive, only do it if focused
                        if (binding.searchBar.hasFocus()) {
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

    private fun getAdapter() = binding.allAppsGrid.adapter as? AppTileAdapter

    private fun setupAdapter(launchers: List<AppTile>) {
        activity?.runOnUiThread {
            val layoutManager = binding.allAppsGrid.layoutManager as MyGridLayoutManager
            layoutManager.spanCount = context.config.drawerColumnCount

            if (getAdapter() == null) {
                AppTileAdapter(activity!!, this) { clicked ->
                    val host = activity
                    val launcher = clicked as AppTile

                    // Same app_click gate as the swipe-left panel. The launch itself is in the
                    // callback, which ShellPromoConfig.run invokes on every path, so a tap is
                    // never swallowed when there is no ad to show.
                    val openApp = {
                        host?.launchApp(launcher.packageName, launcher.activityName)
                        if (host?.config?.closeAppDrawer == true) {
                            host.closeAppDrawer(delayed = true)
                        }
                        ignoreTouches = false
                        touchDownY = -1
                    }

                    if (host == null) openApp()
                    else ShellPromoConfig.run(host, ShellPromoConfig.Surface.APP_CLICK) { openApp() }
                }.apply {
                    setAdSlot(adHeaderView(), adSlot.position)
                    binding.allAppsGrid.itemAnimator = null
                    binding.allAppsGrid.adapter = this
                }
            }

            // The ad row is full width; without this it would be squeezed into one grid cell.
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

        binding.allAppsFastscroller.updateColors(context.getProperPrimaryColor())
        binding.allAppsGrid.addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Hiding is expensive, only do it if focused
                if (binding.searchBar.hasFocus() && dy > 0 && binding.allAppsGrid.computeVerticalScrollOffset() > 0) {
                    activity?.hideKeyboard()
                }
            }
        })

        setupDrawerBackground(context.getColor(R.color.all_app_bg))

        binding.searchBar.beVisibleIf(context.config.showSearchBar)
        binding.searchBar.requireToolbar().beGone()
        binding.searchBar.updateColors()
        binding.searchBar.applyDrawerSkin()
        binding.searchBar.setupMenu()

        binding.searchBar.onSearchTextChangedListener = {
            submitList(launchers)
        }

        binding.searchBar.binding.topToolbarSearch.setOnEditorActionListener { _, actionId, _ ->
            if (binding.searchBar.getCurrentQuery().isEmpty()) return@setOnEditorActionListener false
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
        binding.noResultsPlaceholder.beVisibleIf(itemCount != null && itemCount == 0)
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

        binding.searchBar.closeSearch()
    }

    fun onBackPressed(): Boolean {
        if (binding.searchBar.isSearchOpen) {
            binding.searchBar.closeSearch()
            return true
        }

        return false
    }

    private fun submitList(items: List<AppTile>) {
        val searchQuery = binding.searchBar.getCurrentQuery()
        val filtered = if (searchQuery.isNotEmpty()) {
            items.filter {
                it.title.normalizeString()
                    .contains(searchQuery.normalizeString(), ignoreCase = true)
            }
        } else {
            items
        }

        getAdapter()?.submitList(filtered) {
            showNoResultsPlaceholderIfNeeded()
        }
    }
}
