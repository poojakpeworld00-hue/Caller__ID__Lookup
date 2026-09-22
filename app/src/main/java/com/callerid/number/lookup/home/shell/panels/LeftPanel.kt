package com.callerid.number.lookup.home.shell.panels

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import androidx.core.view.GestureDetectorCompat
import androidx.core.widget.doAfterTextChanged
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.showKeyboard
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.surface.InlinePromo
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.shell.screens.HomeBoardActivity
import com.callerid.number.lookup.home.shell.lists.DrawerAppsAdapter
import com.callerid.number.lookup.home.databinding.BoardLeftPanelBinding
import com.callerid.number.lookup.home.shell.ext.launchApp
import com.callerid.number.lookup.home.shell.entities.AppTile
import com.callerid.number.lookup.home.shell.entities.appLauncherComparator
import kotlin.math.abs

class LeftPanel(
    context: Context,
    attributeSet: AttributeSet,
) : BasePanel<BoardLeftPanelBinding>(context, attributeSet) {

    private var launchers = emptyList<AppTile>()
    private var resultsCap = COLLAPSED_RESULTS
    private val nativePromo = InlinePromo()

    private var adSlot = ShellPromoConfig.Slot(
        enabled = false,
        adType = ShellPromoConfig.SlotAd.NONE,
        nativeType = "mid2",
        bannerType = "adaptive",
        adUnitId = "",
    )

    private var suggestedSlot = ShellPromoConfig.Slot(
        enabled = false,
        adType = ShellPromoConfig.SlotAd.NONE,
        nativeType = "native_banner",
        bannerType = "adaptive",
        adUnitId = "",
    )

    private lateinit var suggestedAdapter: DrawerAppsAdapter
    private lateinit var recentAdapter: DrawerAppsAdapter
    private lateinit var resultsAdapter: DrawerAppsAdapter
    private lateinit var searchInAdapter: DrawerAppsAdapter

    private val gestureDetector = GestureDetectorCompat(context, object : SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            if (velocityX > 0 && abs(velocityX) > abs(velocityY)) {
                activity?.hideLeftPanel()
                return true
            }

            return false
        }
    })

    override fun setupFragment(activity: HomeBoardActivity) {
        this.activity = activity
        this.binding = BoardLeftPanelBinding.bind(this)

        adSlot = ShellPromoConfig.sidePanelSlot(activity)
        suggestedSlot = ShellPromoConfig.sidePanelSuggestedSlot(activity)

        if (adSlot.needsNativePreload || suggestedSlot.needsNativePreload) {
            nativePromo.fetchNativeAds(activity)
        }

        suggestedAdapter = DrawerAppsAdapter(R.layout.cell_panel_grid_app, ::launchLauncher)
        recentAdapter = DrawerAppsAdapter(R.layout.cell_panel_grid_app, ::launchLauncher)
        resultsAdapter = DrawerAppsAdapter(R.layout.cell_panel_result, ::launchLauncher)
        searchInAdapter = DrawerAppsAdapter(R.layout.cell_panel_search_in, ::searchInApp)

        binding.panelSuggestedGridVw.adapter = suggestedAdapter
        binding.panelRecentGridVw.adapter = recentAdapter
        binding.panelResultsListVw.adapter = resultsAdapter
        binding.panelSearchInListVw.adapter = searchInAdapter

        binding.panelSearchVw.doAfterTextChanged {
            resultsCap = COLLAPSED_RESULTS
            updateSections()
        }

        binding.panelSearchClearVw.setOnClickListener {
            binding.panelSearchVw.setText("")
        }

        binding.panelFreeUpSpaceVw.setOnClickListener {
            val host = activity ?: return@setOnClickListener
            host.hideLeftPanel()
            // Opens the same "Free up space" page as the edge pill, which carries the button-lock
            // and rewarded-ad flow (see CleanerActivity).
            host.openCleaner()
        }

        binding.panelSeeMoreVw.setOnClickListener {
            resultsCap = if (resultsCap == COLLAPSED_RESULTS) {
                EXPANDED_RESULTS
            } else {
                COLLAPSED_RESULTS
            }
            updateSections()
        }

        binding.panelSearchVw.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_GO -> launchFirstResult()

                else -> false
            }
        }
    }

    fun onPanelShown() {
        val activity = activity ?: return

        val freshAd = ShellPromoConfig.sidePanelSlot(activity)
        val freshSuggested = ShellPromoConfig.sidePanelSuggestedSlot(activity)
        if (freshAd != adSlot || freshSuggested != suggestedSlot) {
            adSlot = freshAd
            suggestedSlot = freshSuggested
            if (adSlot.needsNativePreload || suggestedSlot.needsNativePreload) {
                nativePromo.fetchNativeAds(activity)
            }
        }

        
        ShellPromoConfig.refreshSlot(activity, adSlot, binding.adNativeFrameVw, binding.adShimmerVw)
        ShellPromoConfig.refreshSlot(
            activity = activity,
            slot = suggestedSlot,
            container = binding.adSuggestedFrameVw,
            shimmer = binding.adSuggestedShimmerVw,
        )
    }

    fun focusSearch() {
        binding.panelSearchVw.requestFocus()
        activity?.showKeyboard(binding.panelSearchVw)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        gestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    fun gotLaunchers(appLaunchers: List<AppTile>) {
        launchers = appLaunchers.sortedWith(appLauncherComparator)
        activity?.runOnUiThread {
            updateSections()
        }
    }

    fun hasQuery() = getQuery().isNotEmpty()

    fun resetSearch() {
        binding.panelSearchVw.setText("")
        binding.panelSearchVw.clearFocus()
        binding.panelScrollVw.scrollTo(0, 0)
    }

    private fun getQuery() = binding.panelSearchVw.text.toString().trim()

    private fun matchingLaunchers(query: String) = launchers.filter {
        it.title.normalizeString().contains(query.normalizeString(), ignoreCase = true)
    }

    private fun launchFirstResult(): Boolean {
        val query = getQuery()
        if (query.isEmpty()) {
            return false
        }

        val first = matchingLaunchers(query).firstOrNull() ?: return false
        launchLauncher(first)
        return true
    }

    private fun updateSections() {
        val query = getQuery()
        val hasQuery = query.isNotEmpty()

        binding.panelSearchClearVw.beVisibleIf(hasQuery)
        binding.panelFreeUpSpaceVw.beVisibleIf(!hasQuery)
        binding.panelSuggestedHeaderVw.beVisibleIf(!hasQuery)
        binding.panelSuggestedGridVw.beVisibleIf(!hasQuery)

        if (hasQuery) {
            val results = matchingLaunchers(query)

            binding.panelRecentHeaderVw.beGone()
            binding.panelRecentGridVw.beGone()
            binding.panelResultsHeaderVw.beVisibleIf(results.isNotEmpty())
            binding.panelResultsListVw.beVisibleIf(results.isNotEmpty())
            binding.panelNoResultsVw.beVisibleIf(results.isEmpty())
            binding.panelSeeMoreVw.beVisibleIf(results.size > COLLAPSED_RESULTS)
            binding.panelSeeMoreVw.setText(
                if (resultsCap == COLLAPSED_RESULTS) R.string.see_more else R.string.see_less
            )
            resultsAdapter.submitList(results.take(resultsCap))

            val searchTargets = launchers.filter { it.packageName in SEARCH_IN_PACKAGES }
            binding.panelSearchInHeaderVw.beVisibleIf(searchTargets.isNotEmpty())
            binding.panelSearchInListVw.beVisibleIf(searchTargets.isNotEmpty())
            searchInAdapter.submitList(searchTargets)
        } else {
            val recent = launchers.drop(SUGGESTED_COUNT).take(RECENT_COUNT)
            binding.panelResultsHeaderVw.beGone()
            binding.panelResultsListVw.beGone()
            binding.panelNoResultsVw.beGone()
            binding.panelSearchInHeaderVw.beGone()
            binding.panelSearchInListVw.beGone()
            binding.panelRecentHeaderVw.beVisibleIf(recent.isNotEmpty())
            binding.panelRecentGridVw.beVisibleIf(recent.isNotEmpty())

            suggestedAdapter.submitList(launchers.take(SUGGESTED_COUNT))
            recentAdapter.submitList(recent)
        }
    }

    private fun launchLauncher(launcher: AppTile) {
        val activity = activity ?: return

        ShellPromoConfig.run(activity, ShellPromoConfig.Surface.APP_CLICK) {
            activity.launchApp(launcher.packageName, launcher.activityName)
            activity.hideLeftPanel()
        }
    }

    private fun searchInApp(launcher: AppTile) {
        val query = getQuery()
        val uri = when (launcher.packageName) {
            PACKAGE_MAPS -> "geo:0,0?q=${Uri.encode(query)}"
            PACKAGE_PLAY_STORE -> "market://search?q=${Uri.encode(query)}"
            else -> "https://www.google.com/search?q=${Uri.encode(query)}"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(launcher.packageName)
        }

        try {
            activity?.startActivity(intent)
            activity?.hideLeftPanel()
        } catch (_: ActivityNotFoundException) {

            launchLauncher(launcher)
        }
    }

    companion object {
        private const val SUGGESTED_COUNT = 8
        private const val RECENT_COUNT = 4
        private const val COLLAPSED_RESULTS = 5

        private const val EXPANDED_RESULTS = Int.MAX_VALUE

        private const val PACKAGE_CHROME = "com.android.chrome"
        private const val PACKAGE_MAPS = "com.google.android.apps.maps"
        private const val PACKAGE_PLAY_STORE = "com.android.vending"
        private val SEARCH_IN_PACKAGES =
            setOf(PACKAGE_CHROME, PACKAGE_MAPS, PACKAGE_PLAY_STORE)
    }
}
