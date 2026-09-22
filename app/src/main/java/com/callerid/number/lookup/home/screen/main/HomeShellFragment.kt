package com.callerid.number.lookup.home.screen.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.callerid.admesh.surface.StoreUpdateRegistry
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.HolderFragment
import com.callerid.number.lookup.home.databinding.BoardHomeShellBinding
import com.callerid.number.lookup.home.databinding.CellNavBinding
import com.callerid.number.lookup.home.screen.shared.HomeAnim
import com.callerid.number.lookup.home.screen.directory.DirectoryFragment
import com.callerid.number.lookup.home.screen.identify.NumberLookupFragment
import com.callerid.number.lookup.home.screen.history.RecentsFragment
import com.callerid.number.lookup.home.screen.consent.OverlayKit
import com.callerid.number.lookup.home.kit.rateApp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class HomeShellFragment : HolderFragment<BoardHomeShellBinding>() {

    private data class Tab(
        val nav: CellNavBinding,
        val fragment: Fragment,
        @param:DrawableRes val selectedIcon: Int,
        @param:DrawableRes val unselectedIcon: Int,
        @param:StringRes val label: Int
    )

    private lateinit var tabs: List<Tab>
    private var currentIndex = -1

    private var statusBarTop = 0

    private val backStack = ArrayDeque<Int>()

    private var forceUpdateDialog: AlertDialog? = null

    var isShellVisible: Boolean = false
        private set

    private val controller: HomeShellDriver? get() = homeShellController

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        BoardHomeShellBinding.inflate(inflater, container, false)

    override fun initView() {
        controller?.shell = this

        ViewCompat.setOnApplyWindowInsetsListener(binding.shellRootVw) { _, insets ->
            statusBarTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            applyTopInsetForTab(currentIndex)
            insets
        }

        tabs = listOf(
            Tab(
                binding.navHomeVw, HomeMainFragment(),
                R.drawable.intro_home_selected, R.drawable.intro_home_unselected, R.string.nav_home
            ),
            Tab(
                binding.navRecentsVw, RecentsFragment(),
                R.drawable.intro_recent_selected, R.drawable.intro_recent_unselected, R.string.nav_recents
            ),
            Tab(
                binding.navContactsVw, DirectoryFragment(),
                R.drawable.intro_contact_selected, R.drawable.intro_contact_unselected, R.string.nav_contacts
            ),
            Tab(
                binding.navLookupVw, NumberLookupFragment(),
                R.drawable.intro_lookup_selected, R.drawable.intro_lookup_unselected, R.string.nav_lookup
            )
        )

        tabs.forEachIndexed { index, tab ->
            tab.nav.navLabelVw.setText(tab.label)
            tab.nav.root.setOnClickListener {
                animateIcon(tab.nav.navIconVw)
                select(index)
            }
        }

        select(0)

        binding.padEnableOverlay.setOnClickListener {
            controller?.startOverlayPermissionFlow()
        }

        // A pending lookup request (with or without a pre-filled number) selects the Lookup tab
        // once the tabs exist. Keyed on presence, not a non-blank value, so opening the tab with
        // no number still lands here.
        if (arguments?.containsKey(ARG_LOOKUP_NUMBER) == true) {
            val number = arguments?.getString(ARG_LOOKUP_NUMBER)
            arguments?.remove(ARG_LOOKUP_NUMBER)
            showLookup(number)
        }
    }

    override fun onResume() {
        super.onResume()
        updateOverlayBanner()
    }

    override fun onDestroyView() {
        dismissForceUpdateDialog()
        if (controller?.shell === this) controller?.shell = null
        super.onDestroyView()
    }

    fun setPanelVisible(visible: Boolean) {
        isShellVisible = visible

        if (!::tabs.isInitialized) return
        if (visible) {
            updateOverlayBanner()
            dashboardTab()?.onShellShown()
        } else {
            dashboardTab()?.onShellHidden()
        }
    }

    private fun dashboardTab(): HomeMainFragment? =
        tabs.firstOrNull { it.fragment is HomeMainFragment }?.fragment as? HomeMainFragment

    fun requestLookup(number: String?) {
        if (view == null) {
            arguments = (arguments ?: Bundle()).apply { putString(ARG_LOOKUP_NUMBER, number) }
            return
        }
        showLookup(number)
    }

    fun showLookup(number: String? = null) {
        if (!::tabs.isInitialized) {
            requestLookup(number)
            return
        }
        val index = tabs.indexOfFirst { it.fragment is NumberLookupFragment }
        if (index < 0) return
        select(index)
        if (!number.isNullOrBlank()) {
            (tabs[index].fragment as? NumberLookupFragment)?.requestSearch(number)
        }
    }

    fun showRecents() {
        val index = tabs.indexOfFirst { it.fragment is RecentsFragment }
        if (index >= 0) select(index)
    }

    /**
     * True while a blocking coach hint (the lookup search bubble) is on screen. The launcher's
     * caller panel reads this to freeze its swipe gesture so the panel cannot move under the hint.
     */
    fun isCoachHintActive(): Boolean =
        ::tabs.isInitialized && dashboardTab()?.isSearchHintShowing() == true

    fun pageForward(): Boolean {
        if (currentIndex < 0 || currentIndex >= tabs.lastIndex) return false
        select(currentIndex + 1)
        return true
    }

    fun pageBack(): Boolean {
        if (currentIndex <= 0) return false
        select(currentIndex - 1)
        return true
    }

    fun onBackPressed(): Boolean {

        if (backStack.isNotEmpty()) {
            select(backStack.removeLast(), recordHistory = false)
            return true
        }

        val homeIndex = tabs.indexOfFirst { it.fragment is HomeMainFragment }.coerceAtLeast(0)
        if (currentIndex != homeIndex) {
            select(homeIndex, recordHistory = false)
            return true
        }

        return false
    }

    private fun animateIcon(icon: View) {
        icon.animate().cancel()
        icon.scaleX = 0.7f
        icon.scaleY = 0.7f
        icon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun select(index: Int, recordHistory: Boolean = true) {
        if (index == currentIndex) return

        if (recordHistory && currentIndex >= 0) {
            backStack.remove(index)
            backStack.remove(currentIndex)
            backStack.addLast(currentIndex)
        }

        val tab = tabs[index]

        childFragmentManager.beginTransaction().apply {
            if (!tab.fragment.isAdded) add(R.id.fragContainer, tab.fragment)
            tabs.forEach { if (it.fragment.isAdded && it !== tab) hide(it.fragment) }
            show(tab.fragment)
        }.commit()

        tabs.forEachIndexed { i, t ->
            val active = i == index
            t.nav.navIconVw.setImageResource(if (active) t.selectedIcon else t.unselectedIcon)
            val from = t.nav.navLabelVw.currentTextColor
            val to = ContextCompat.getColor(
                requireContext(), if (active) R.color.primary else R.color.on_surface_variant
            )

            HomeAnim.animateTint(t.nav.navIconVw, t.nav.navLabelVw, from, to)
            t.nav.navIndicatorVw.visibility = if (active) View.VISIBLE else View.INVISIBLE
        }

        currentIndex = index
        applyTopInsetForTab(index)
    }

    private fun applyTopInsetForTab(index: Int) {
        if (index < 0 || view == null) return
        val fragment = tabs.getOrNull(index)?.fragment
        val immersive = fragment is HomeMainFragment ||
            fragment is RecentsFragment ||
            fragment is DirectoryFragment ||
            fragment is NumberLookupFragment
        binding.fragContainer.setPadding(0, if (immersive) 0 else statusBarTop, 0, 0)

        val window = activity?.window ?: return
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true
    }

    fun updateOverlayBanner() {
        val ctx = context ?: return
        if (view == null) return
        val coreGranted = isPermissionGranted(Manifest.permission.READ_CALL_LOG) &&
            isPermissionGranted(Manifest.permission.READ_CONTACTS)
        
        val show = coreGranted && OverlayKit.isOfferable(ctx) && !OverlayKit.isGranted(ctx)
        binding.overlayBannerVw.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED

    fun refreshHomePermissionHint() {
        dashboardTab()?.refreshPermissionHint()
    }

    fun showUpdateReadyPrompt() {
        if (view == null || isRemoving) return
        Snackbar.make(binding.shellRootVw, R.string.update_ready_msg, Snackbar.LENGTH_INDEFINITE)
            .setAnchorView(binding.footerBar)
            .setAction(R.string.update_restart) { StoreUpdateRegistry.completeUpdate() }
            .show()
    }

    fun showForceUpdateRequiredDialog() {
        if (!StoreUpdateRegistry.isForceUpdate) return
        val activity = activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        if (forceUpdateDialog?.isShowing == true) return

        forceUpdateDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_required_title)
            .setMessage(R.string.update_required_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.update_retry) { dialog, _ ->
                dialog.dismiss()
                StoreUpdateRegistry.retryCheck()
            }
            .setNegativeButton(R.string.update_open_store) { _, _ ->
                activity.rateApp()
                homeShellHost?.onShellBackExhausted()
            }
            .show()
    }

    fun dismissForceUpdateDialog() {
        forceUpdateDialog?.dismiss()
        forceUpdateDialog = null
    }

    companion object {
        private const val ARG_LOOKUP_NUMBER = "arg_lookup_number"

        fun newInstance(number: String? = null) = HomeShellFragment().apply {
            if (!number.isNullOrBlank()) {
                arguments = Bundle().apply { putString(ARG_LOOKUP_NUMBER, number) }
            }
        }
    }
}
