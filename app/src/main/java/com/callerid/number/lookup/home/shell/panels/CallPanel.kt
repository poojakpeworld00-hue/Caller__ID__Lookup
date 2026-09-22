package com.callerid.number.lookup.home.shell.panels

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.callerid.admesh.engine.PerScreenPromo
import com.callerid.admesh.engine.PromoVault
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.databinding.BoardCallerPanelBinding
import com.callerid.number.lookup.home.shell.screens.HomeBoardActivity
import com.callerid.number.lookup.home.screen.main.HomeShellFragment
import com.callerid.number.lookup.home.kit.followAdContainer
import kotlin.math.abs

class CallPanel(
    context: Context,
    attributeSet: AttributeSet,
) : BasePanel<BoardCallerPanelBinding>(context, attributeSet) {

    private var bannerRequested = false
    private var lastBannerAt = 0L

    private val gestureDetector = GestureDetectorCompat(context, object : SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            if (abs(velocityX) <= abs(velocityY)) return false

            return if (velocityX < 0) {

                if (shell()?.pageForward() != true) activity?.hideCallerPanel()
                true
            } else {

                shell()?.pageBack()
                true
            }
        }
    })

    override fun setupFragment(activity: HomeBoardActivity) {
        this.activity = activity
        this.binding = BoardCallerPanelBinding.bind(this)

        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }

        if (activity.supportFragmentManager.findFragmentById(R.id.callerPanelContainerVw) == null) {
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.callerPanelContainerVw, HomeShellFragment.newInstance())
                .commit()
        }
    }

    fun shell(): HomeShellFragment? = activity?.supportFragmentManager
        ?.findFragmentById(R.id.callerPanelContainerVw) as? HomeShellFragment

    fun onPanelOpened() {
        val host = activity ?: return
        val container = binding.bannerSlotVw.bannerAdFrameVw

        
        if (!PerScreenPromo.resolve(host, BANNER_SCREEN_KEY).show ||
            !PromoVault.getInstance(host).getBoolean("IsAdsON")
        ) {
            container.removeAllViews()
            container.visibility = View.GONE
            binding.bannerSlotVw.bannerShimmerVw.stopShimmer()
            binding.bannerSlotVw.bannerShimmerVw.visibility = View.GONE
            binding.callerAdBannerDividerVw.followAdContainer(container)
            
            bannerRequested = false
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (bannerRequested && now - lastBannerAt < MIN_REFRESH_MS) return
        bannerRequested = true
        lastBannerAt = now

        PerScreenPromo.renderAd(
            BANNER_SCREEN_KEY, host, container, binding.bannerSlotVw.bannerShimmerVw
        )

        binding.callerAdBannerDividerVw.followAdContainer(container)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        // While the lookup coach hint is up, the panel must stay put: skip the swipe detector so a
        // horizontal drag/fling can neither page nor close the panel. Touches still pass through to
        // the hint overlay, which dismisses itself on tap — and the swipe is restored the moment the
        // hint is gone (isCoachHintActive() goes false).
        if (shell()?.isCoachHintActive() == true) {
            return super.dispatchTouchEvent(event)
        }

        if (gestureDetector.onTouchEvent(event)) {

            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    companion object {

        private const val BANNER_SCREEN_KEY = "AppHomeActivity"

        private const val MIN_REFRESH_MS = 30_000L
    }
}
