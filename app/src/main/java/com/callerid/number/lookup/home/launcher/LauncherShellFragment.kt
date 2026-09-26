package com.callerid.number.lookup.home.launcher

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.callerid.admesh.engine.PerScreenPromo
import com.callerid.admesh.engine.PromoVault
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.databinding.BoardCallerPanelBinding
import com.callerid.number.lookup.home.kit.followAdContainer
import com.callerid.number.lookup.home.onboard.OnboardRouter
import com.callerid.number.lookup.home.permit.PermitEngine
import com.callerid.number.lookup.home.screen.main.HomeShellFragment
import io.launcher.home.api.LauncherPanelContent

/**
 * What the launcher's right-hand panel hosts: the app's own home ([HomeShellFragment]) with the
 * bottom banner the old caller panel carried.
 *
 * The launcher commits this at `onCreate` and parks it off screen, so everything that must not
 * happen unseen — the banner request, permission priming — waits for [setPanelVisible].
 */
class LauncherShellFragment : Fragment(), LauncherPanelContent {

    private var _binding: BoardCallerPanelBinding? = null

    private var bannerRequested = false
    private var lastBannerAt = 0L

    private val shell: HomeShellFragment?
        get() = if (isAdded) childFragmentManager.findFragmentById(R.id.callerPanelContainerVw) as? HomeShellFragment else null

    private val host: LauncherShellHost? get() = LauncherShellHost.of(activity)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BoardCallerPanelBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (shell == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.callerPanelContainerVw, HomeShellFragment.newInstance())
                .commitNow()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun setPanelVisible(visible: Boolean) {
        host?.shellVisible = visible
        shell?.setPanelVisible(visible)
        val controller = host?.homeShellController ?: return
        if (visible) {
            renderBanner()
            if (OnboardRouter.wasOnboardingCompleted(requireContext())) {
                // QRScanner's `home` moment: permission_engine rules listing LauncherPanel, then the
                // FSI / permission-sheet priming. Primed only if the panel is still open by then.
                PermitEngine.check(requireActivity()) {
                    if (host?.shellVisible == true) controller.startFirstRunPriming()
                }
            }
        } else {
            // Sheets and dialogs are anchored to the Activity, not the panel; take them down with it.
            controller.onShellHidden()
        }
    }

    override fun handleBack(): Boolean = shell?.onBackPressed() == true

    private fun renderBanner() {
        val activity = activity ?: return
        val binding = _binding ?: return
        val container = binding.bannerSlotVw.bannerAdFrameVw
        val shimmer = binding.bannerSlotVw.bannerShimmerVw

        if (!PerScreenPromo.resolve(activity, BANNER_SCREEN_KEY).show ||
            !PromoVault.getInstance(activity).getBoolean("IsAdsON")
        ) {
            container.removeAllViews()
            container.visibility = View.GONE
            shimmer.stopShimmer()
            shimmer.visibility = View.GONE
            binding.callerAdBannerDividerVw.followAdContainer(container)
            bannerRequested = false
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (bannerRequested && now - lastBannerAt < MIN_REFRESH_MS) return
        bannerRequested = true
        lastBannerAt = now

        PerScreenPromo.renderAd(BANNER_SCREEN_KEY, activity, container, shimmer)
        binding.callerAdBannerDividerVw.followAdContainer(container)
    }

    companion object {

        /** The same ScreenAds entry the standalone home uses, so one switch covers both. */
        private const val BANNER_SCREEN_KEY = "AppHomeActivity"

        private const val MIN_REFRESH_MS = 30_000L
    }
}
