package com.callerid.number.lookup.home.screen.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import android.content.res.ColorStateList
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.HolderFragment
import com.callerid.admesh.engine.logPermissionResult
import com.callerid.number.lookup.home.store.RegionResolver
import com.callerid.number.lookup.home.store.StorageRegistry
import kotlinx.coroutines.launch
import com.callerid.number.lookup.home.databinding.BoardHomeBinding
import com.callerid.number.lookup.home.databinding.CellQuickActionBinding
import com.callerid.number.lookup.home.screen.blocking.BlockCenterActivity
import com.callerid.number.lookup.home.screen.shared.CallLineAdapter
import com.callerid.admesh.surface.InlinePromoStrip
import com.callerid.number.lookup.home.screen.shared.CoachBubble
import com.callerid.number.lookup.home.screen.shared.HomeAnim
import com.callerid.number.lookup.home.kit.openActivity
import com.callerid.number.lookup.home.screen.dialpad.DialPadActivity
import com.callerid.number.lookup.home.screen.identify.DialCountries
import com.callerid.number.lookup.home.screen.identify.CountryPickActivity
import com.callerid.number.lookup.home.screen.prefs.SettingsHubActivity
import com.callerid.number.lookup.home.screen.gadgetry.ToolboxActivity
import com.callerid.number.lookup.home.kit.followAdContainer

class HomeMainFragment : HolderFragment<BoardHomeBinding>() {

    private val viewModel: OverviewViewModel by viewModels()
    private val recentAdapter = CallLineAdapter(
        mutableListOf(),
        onCall = { placeCall(it) },
        onIdentify = { number -> homeShell?.showLookup(number) }
    )
    private val prefs by lazy { StorageRegistry(requireContext()) }

    /** Dialing code selected in the search country chip (no leading '+'). */
    private var homeDial: String = ""

    /** The one-shot search coach-mark while it is up, so the host can take it down. */
    private var searchHint: CoachBubble? = null

    /** Press-scale (0.96) with a spring release — the design's quick-action motion.
     *  Returns false so the view's own click listener still fires. */
    @SuppressLint("ClickableViewAccessibility")
    private val pressScale = View.OnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(120L).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f)
                    .setInterpolator(OvershootInterpolator()).setDuration(260L).start()
        }
        false
    }

    /** Quick-action permission queue + its result launcher (see [withCorePermissions]). */
    private val corePermQueue = ArrayDeque<String>()
    private var corePermAction: (() -> Unit)? = null
    private var lastCorePermission: String? = null
    private val corePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        lastCorePermission?.let { context?.logPermissionResult(it, granted) }
        advanceCorePermissions()
    }

    private val countryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val data = res.data ?: return@registerForActivityResult
            val iso = data.getStringExtra(CountryPickActivity.EXTRA_ISO) ?: return@registerForActivityResult
            val dial = data.getStringExtra(CountryPickActivity.EXTRA_DIAL).orEmpty()
            prefs.homeCountryIso = iso // remember the user's explicit choice
            applyHomeCountry(iso, dial)
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        BoardHomeBinding.inflate(inflater, container, false)

    override fun initView() {
        // Hero bleeds under the status bar; pad its content down by the inset.
        val baseTop = binding.heroHeaderVw.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.heroHeaderVw) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = baseTop + top)
            insets
        }

        // Per-tile tint = Claude Design's actions-3 cells (g-700/teal/clay/amber).
        // Blocklist/Tools deliberately do NOT reuse the danger/success verdict
        // colors here -- those are fixed verdict roles, not decorative tints.
        bindQuick(binding.qaDialerVw, R.drawable.slot_grid_nine, R.string.quick_dialer, R.color.primary, R.color.primary_container)
        bindQuick(binding.qaLookupVw, R.drawable.slot_magnifying_glass, R.string.quick_lookup, R.color.cid_teal, R.color.cid_teal_100)
        bindQuick(binding.qaBlocklistVw, R.drawable.slot_prohibit, R.string.quick_blocklist, R.color.cid_clay, R.color.cid_clay_100)
        bindQuick(binding.qaToolsVw, R.drawable.slot_squares_four, R.string.quick_tools, R.color.cid_amber, R.color.cid_amber_100)

        binding.rollRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rollRecent.adapter = recentAdapter

        // Native banner above the recent calls.
        InlinePromoStrip().showNativeBannerNative(requireActivity(), binding.adRecentBannerVw, binding.adRecentShimmerVw)
        binding.adNativeDividerVw.followAdContainer(binding.adRecentBannerVw)
        binding.adNativeDivider1Vw.followAdContainer(binding.adRecentBannerVw)
        binding.padSettings.setOnClickListener {
            requireActivity().openActivity<SettingsHubActivity>()
        }

        binding.qaDialerVw.root.setOnClickListener {
            withCorePermissions { requireActivity().openActivity<DialPadActivity>() }
        }
        binding.qaLookupVw.root.setOnClickListener {
            withCorePermissions { homeShell?.showLookup() }
        }
        setupHomeCountry()
        binding.rowHomeCountry.setOnClickListener {
            countryLauncher.launch(Intent(requireContext(), CountryPickActivity::class.java))
        }
        binding.padHomeSearch.setOnClickListener { submitSearch() }
        binding.inpHomeSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch(); true
            } else false
        }
        binding.qaBlocklistVw.root.setOnClickListener {
            withCorePermissions { requireActivity().openActivity<BlockCenterActivity>() }
        }
        binding.qaToolsVw.root.setOnClickListener {
            withCorePermissions { requireActivity().openActivity<ToolboxActivity>() }
        }

        // Protection card → Blocklist; Recent "See all" → Recents tab.
        binding.panelProtection.setOnClickListener {
            requireActivity().openActivity<BlockCenterActivity>()
        }
        binding.lblSeeAll.setOnClickListener {
            homeShell?.showRecents()
        }

        // Quick-action tiles: press-scale 0.96 with a spring release (design motion).
        listOf(binding.qaDialerVw, binding.qaLookupVw, binding.qaBlocklistVw, binding.qaToolsVw)
            .forEach { it.root.setOnTouchListener(pressScale) }
        binding.padAllowCallLog.setOnClickListener {
            requestPermissionChain(
                listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
            ) {
                loadRecentIfAllowed()
                homeShellController?.startOverlayPermissionFlow()
            }
        }

        binding.padPermManage.setOnClickListener {
            homeShellController?.showPermissionSheet()
        }

        HomeAnim.attachFocusScale(binding.searchBarVw, binding.inpHomeSearch)

        loadRecentIfAllowed()
        // Only when the shell is already on screen — in the launcher this view is built while
        // the panel is still parked off-screen. [onShellShown] covers the other order.
        if (homeShell?.isShellVisible != false) maybeShowSearchHint()
        refreshPermissionHint()
        playEntrance()
    }

    /**
     * Screen entrance: the Protection banner rises first, then the search pill,
     * the quick-actions card and the "Recent activity" header, each a beat
     * later -- the design's `cid-rise-in` rhythm. The recent list's own stagger
     * continues from [CallLineAdapter]. Runs once per Fragment instance (Home's
     * view is created once; tab switches show/hide it rather than recreating it).
     */
    private fun playEntrance() {
        HomeAnim.riseIn(binding.panelProtection, delay = 0L)
        HomeAnim.riseIn(binding.searchBarVw, delay = 90L)
        HomeAnim.riseIn(binding.quickActionsRowVw, delay = 150L)
        HomeAnim.riseIn(binding.recentHeaderRowVw, delay = 200L)
    }

    /**
     * Surfaces the "Manage permissions" hint once the AppHomeActivity permission
     * sheet has been dismissed with permissions still pending, and hides it again
     * as soon as everything is granted. Safe to call any time the fragment is
     * attached — AppHomeActivity owns the actual condition.
     */
    fun refreshPermissionHint() {
        if (view == null) return
        val show = homeShellController?.shouldShowPermissionHint() == true
        binding.rowPermHint.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * Core permissions nudged from the four Quick Action buttons: post-notifications
     * (Android 13+) so call alerts can show, and read-phone-state for call detection.
     */
    private fun corePermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.READ_PHONE_STATE)
    }

    /**
     * Requests the [corePermissions] (skipping any already granted) and then runs
     * [action] — which navigates to the tapped quick action's screen.
     *
     * Each tap re-attempts a still-denied permission. Once it has been denied a
     * second time (permanently — the system stops showing its dialog), we do NOT
     * bounce the user to App Settings; we simply skip it and continue to the next
     * activity so the quick action still works.
     */
    private fun withCorePermissions(action: () -> Unit) {
        corePermQueue.clear()
        corePermQueue.addAll(corePermissions())
        corePermAction = action
        advanceCorePermissions()
    }

    /** Walks the queue, prompting where the dialog still shows, then runs the action. */
    private fun advanceCorePermissions() {
        val ctx = context ?: return
        val prefs = StorageRegistry(ctx)
        while (corePermQueue.isNotEmpty()) {
            val permission = corePermQueue.removeFirst()
            if (ContextCompat.checkSelfPermission(ctx, permission)
                == PackageManager.PERMISSION_GRANTED
            ) continue

            when {
                // First-ever request → show the system dialog (callback resumes the walk).
                !prefs.hasRequestedPermission(permission) -> {
                    prefs.markPermissionRequested(permission)
                    lastCorePermission = permission
                    corePermLauncher.launch(permission)
                    return
                }
                // Denied before but the dialog still appears → re-attempt.
                shouldShowRequestPermissionRationale(permission) -> {
                    lastCorePermission = permission
                    corePermLauncher.launch(permission)
                    return
                }
                // Permanently denied → skip (no Settings redirect) and keep going.
                else -> continue
            }
        }
        // Queue drained — proceed to the next activity.
        val action = corePermAction
        corePermAction = null
        action?.invoke()
    }

    /** Called by [HomeShellFragment] when the shell reaches the screen, and when it leaves. */
    fun onShellShown() {
        maybeShowSearchHint()
    }

    fun onShellHidden() {
        searchHint?.dismiss()
        searchHint = null
    }

    /**
     * First-run coach-mark: dims the shell, spotlights the Home search bar through
     * the scrim, and shows a hint bubble beneath it. Shown only once (persisted via
     * [StorageRegistry.isSearchHintShown]); a tap anywhere dismisses it.
     *
     * Hosted on the shell's own root, not on the window: in the launcher this tab
     * lives in a side panel, and a mark on the decor view would scrim the launcher's
     * home screen — spotlighting a search bar that is parked off-screen — and stay
     * there after the panel slid away. Deferred to [onShellShown] for the same
     * reason: the view is built long before the panel opens.
     */
    private fun maybeShowSearchHint() {
        if (prefs.isSearchHintShown) return
        if (searchHint != null) return
        // isHidden: the shell keeps every visited tab alive and merely hides it, so this can be
        // asked while the user is looking at Recents or Lookup — the bar to spotlight is then
        // not on screen at all.
        if (view == null || isHidden) return
        val anchor = binding.searchBarVw
        anchor.post {
            if (!isAdded || view == null || isHidden) return@post
            if (prefs.isSearchHintShown) return@post
            // The shell root when there is one (launcher panel + AppHomeActivity both host the
            // tab inside it); the window only for a host that has no shell at all.
            val host = homeShell?.view as? ViewGroup
            prefs.isSearchHintShown = true
            searchHint = if (host != null) {
                CoachBubble.show(host, anchor, R.layout.part_search_hint) { searchHint = null }
            } else {
                val act = activity ?: return@post
                CoachBubble.show(act, anchor, R.layout.part_search_hint) { searchHint = null }
            }
        }
    }

    /**
     * Picks the search country chip. Uses the saved choice if any; otherwise shows the
     * device region immediately and refines it to the IP-detected country in the background.
     */
    private fun setupHomeCountry() {
        // 1) Honour an explicit choice from the country picker.
        val saved = prefs.homeCountryIso
        if (saved.length == 2) {
            applyHomeCountry(saved, dialFor(saved))
            return
        }
        // 2) The SIM/network country is the most accurate source for a phone
        //    (an Indian SIM → "IN" even when the device language is US English).
        val sim = simCountryIso()
        if (sim != null) {
            applyHomeCountry(sim, dialFor(sim))
            return
        }
        // 3) No SIM → device region immediately (never the globe), refined via IP.
        val region = java.util.Locale.getDefault().country
        val fallbackIso = if (region.length == 2) region else "US"
        applyHomeCountry(fallbackIso, dialFor(fallbackIso))
        detectCountryByIp()
    }

    /** SIM (then network) registered country as an uppercase ISO-2, or null. No permission needed. */
    private fun simCountryIso(): String? {
        val tm = context?.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        val iso = tm.simCountryIso?.takeIf { it.length == 2 }
            ?: tm.networkCountryIso?.takeIf { it.length == 2 }
        return iso?.uppercase()
    }

    private fun dialFor(iso: String): String =
        (DialCountries.byIso(iso)?.dial ?: DialCountries.dialOf(iso)).orEmpty()

    /** Resolves the country from the user's IP and updates the chip (best-effort). */
    private fun detectCountryByIp() {
        viewLifecycleOwner.lifecycleScope.launch {
            val geo = RegionResolver.detectCountry(requireContext()) ?: return@launch
            // Bail if the view is gone or the user picked a country meanwhile.
            if (view == null || prefs.homeCountryIso.isNotBlank()) return@launch
            // Prefer the dial code straight from the API; fall back to our local map.
            val dial = geo.dial.ifBlank { dialFor(geo.iso) }
            // Don't persist an auto-detected country — only the picker records a choice.
            applyHomeCountry(geo.iso, dial)
        }
    }

    private fun applyHomeCountry(iso: String, dial: String) {
        homeDial = dial
        binding.lblHomeFlag.text = DialCountries.flag(iso)
        binding.lblHomeDial.text = if (dial.isBlank()) iso else "+$dial"
    }

    /** Navigates to the Lookup tab and runs the lookup for the entered number. */
    private fun submitSearch() {
        val typed = binding.inpHomeSearch.text?.toString()?.trim().orEmpty()
        // Prefix the selected country code unless the user already typed a '+'.
        val number = when {
            typed.isBlank() -> ""
            typed.startsWith("+") -> typed
            homeDial.isNotBlank() -> "+$homeDial" + typed.filter { it.isDigit() }
            else -> typed
        }
        homeShell?.showLookup(number.ifBlank { null })
        binding.inpHomeSearch.setText("")
    }

    private fun bindQuick(
        item: CellQuickActionBinding,
        @DrawableRes icon: Int,
        @StringRes label: Int,
        @ColorRes fgColor: Int,
        @ColorRes softColor: Int
    ) {
        item.qaIconVw.setImageResource(icon)
        item.qaLabelVw.setText(label)
        val ctx = requireContext()
        item.qaIconVw.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(ctx, fgColor))
        item.qaIconCircleVw.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(ctx, softColor))
    }

    override fun initObservers() {
        viewModel.recent.observe(viewLifecycleOwner) { recentAdapter.submit(it) }
        // Protection banner subtitle -- Claude Design's hero C reads the count inline.
        viewModel.blockedCount.observe(viewLifecycleOwner) {
            binding.lblProtectionSub.text = getString(R.string.home_protection_subtitle, it)
        }
    }

    override fun onResume() {
        super.onResume()
        loadRecentIfAllowed()
        refreshPermissionHint()
    }

    override fun onDestroyView() {
        // The mark is parented to the shell, which outlives this view — take it with us.
        onShellHidden()
        super.onDestroyView()
    }

    /** Reloads when this tab becomes visible again (show/hide keeps the fragment resumed). */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            loadRecentIfAllowed()
            refreshPermissionHint()
            // Coming back to Home is the other moment the one-shot search hint can land: it is
            // skipped while another tab is up, and it is only ever spent once it truly shows.
            if (homeShell?.isShellVisible != false) maybeShowSearchHint()
        }
    }

    private fun loadRecentIfAllowed() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        // Swap the recent list for a permission prompt when access is missing.
        binding.rowRecentPermission.visibility = if (granted) View.GONE else View.VISIBLE
        binding.rollRecent.visibility = if (granted) View.VISIBLE else View.GONE

        if (granted) viewModel.loadRecent()
    }
}
