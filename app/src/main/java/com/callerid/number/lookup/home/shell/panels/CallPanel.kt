package com.callerid.number.lookup.home.shell.panels

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.callerid.admesh.engine.PerScreenPromo
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.databinding.BoardCallerPanelBinding
import com.callerid.number.lookup.home.shell.screens.HomeBoardActivity
import com.callerid.number.lookup.home.screen.main.HomeShellFragment
import com.callerid.number.lookup.home.kit.followAdContainer
import kotlin.math.abs

/**
 * Side panel hosting the caller-ID app's four-tab home UI.
 *
 * The tabs themselves are [HomeShellFragment] — the same fragment AppHomeActivity hosts, so the
 * two surfaces never diverge. This class is only the container: insets, the bottom banner, and
 * the fling that closes the panel again.
 */
class CallPanel(
    context: Context,
    attributeSet: AttributeSet,
) : BasePanel<BoardCallerPanelBinding>(context, attributeSet) {

    private var bannerRequested = false
    private var lastBannerAt = 0L

    // the panel covers the whole screen while open, so HomeBoardActivity never sees these events
    private val gestureDetector = GestureDetectorCompat(context, object : SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            if (abs(velocityX) <= abs(velocityY)) return false

            return if (velocityX < 0) {
                // Fling LEFT pages through the tabs — Home → Recents → Contacts → Lookup.
                // Past the last tab there is nowhere left to go, so the panel slides away and
                // hands the user back to the launcher home screen.
                if (shell()?.pageForward() != true) activity?.hideCallerPanel()
                true
            } else {
                // Fling RIGHT walks back the same way. On Home it does nothing: closing on a
                // right fling would fight the gesture that opened the panel in the first place.
                shell()?.pageBack()
                true
            }
        }
    })

    override fun setupFragment(activity: HomeBoardActivity) {
        this.activity = activity
        this.binding = BoardCallerPanelBinding.bind(this)

        // Keeps the banner clear of the navigation bar. Insets are returned unchanged so the
        // shell inside still receives them for its own per-tab status-bar padding.
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }

        // Committed once and kept — the panel slides in and out rather than being recreated,
        // so the user's tab and scroll position survive closing it.
        if (activity.supportFragmentManager.findFragmentById(R.id.callerPanelContainerVw) == null) {
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.callerPanelContainerVw, HomeShellFragment.newInstance())
                .commit()
        }
    }

    /** The shell inside the panel, once its transaction has run. */
    fun shell(): HomeShellFragment? = activity?.supportFragmentManager
        ?.findFragmentById(R.id.callerPanelContainerVw) as? HomeShellFragment

    /**
     * Loads the bottom banner every time the panel slides in, matching the left panel and the
     * app drawer — both of which re-show their slot on each open.
     *
     * Not done in [setupFragment]: this view is inflated during the launcher's `onCreate` but
     * parked off screen, and a banner rendered there is an impression nobody saw.
     *
     * Throttled by [MIN_REFRESH_MS]. The panel is never recreated, so without a floor a user
     * flicking it open and shut would fire a request per flick and churn fill for impressions
     * nobody reads.
     *
     * Uses AppHomeActivity's own ScreenAds key rather than a launcher-specific one: this panel
     * shows that same home UI, so it should carry that same banner config.
     */
    fun onPanelOpened() {
        val host = activity ?: return
        val now = SystemClock.elapsedRealtime()
        if (bannerRequested && now - lastBannerAt < MIN_REFRESH_MS) return
        bannerRequested = true
        lastBannerAt = now

        val container = binding.bannerSlotVw.bannerAdFrameVw
        PerScreenPromo.renderAd(
            BANNER_SCREEN_KEY, host, container, binding.bannerSlotVw.bannerShimmerVw
        )
        // The hairline only exists to fence off an advert — drop it if the slot stays empty.
        binding.callerAdBannerDividerVw.followAdContainer(container)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // The detector runs first so a recognised fling can pre-empt the children. It only
        // claims horizontal flings; everything else falls through and the tabs still scroll.
        if (gestureDetector.onTouchEvent(event)) {
            // A page swipe usually crosses a full-width list row, and a row spans the whole
            // width — so the pointer never leaves its bounds and the row would fire its own
            // click on this same UP event, opening a contact or a call while the tab changes
            // underneath. Hand the children a cancel instead of the UP to rule that out.
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    companion object {
        /** ScreenAds key — deliberately AppHomeActivity's, see [onPanelOpened]. */
        private const val BANNER_SCREEN_KEY = "AppHomeActivity"

        /** Floor between banner reloads, so flicking the panel cannot churn fill. */
        private const val MIN_REFRESH_MS = 30_000L
    }
}
