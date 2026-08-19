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

    private var homeDial: String = ""

    private var searchHint: CoachBubble? = null

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
            prefs.homeCountryIso = iso
            applyHomeCountry(iso, dial)
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        BoardHomeBinding.inflate(inflater, container, false)

    override fun initView() {

        val baseTop = binding.heroHeaderVw.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.heroHeaderVw) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = baseTop + top)
            insets
        }

        bindQuick(binding.qaDialerVw, R.drawable.slot_grid_nine, R.string.quick_dialer, R.color.primary, R.color.primary_container)
        bindQuick(binding.qaLookupVw, R.drawable.slot_magnifying_glass, R.string.quick_lookup, R.color.cid_teal, R.color.cid_teal_100)
        bindQuick(binding.qaBlocklistVw, R.drawable.slot_prohibit, R.string.quick_blocklist, R.color.cid_clay, R.color.cid_clay_100)
        bindQuick(binding.qaToolsVw, R.drawable.slot_squares_four, R.string.quick_tools, R.color.cid_amber, R.color.cid_amber_100)

        binding.rollRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rollRecent.adapter = recentAdapter

        InlinePromoStrip().renderNativeBanner(requireActivity(), binding.adRecentBannerVw, binding.adRecentShimmerVw)
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

        binding.panelProtection.setOnClickListener {
            requireActivity().openActivity<BlockCenterActivity>()
        }
        binding.lblSeeAll.setOnClickListener {
            homeShell?.showRecents()
        }

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

        if (homeShell?.isShellVisible != false) maybeShowSearchHint()
        refreshPermissionHint()
        playEntrance()
    }

    private fun playEntrance() {
        HomeAnim.riseIn(binding.panelProtection, delay = 0L)
        HomeAnim.riseIn(binding.searchBarVw, delay = 90L)
        HomeAnim.riseIn(binding.quickActionsRowVw, delay = 150L)
        HomeAnim.riseIn(binding.recentHeaderRowVw, delay = 200L)
    }

    fun refreshPermissionHint() {
        if (view == null) return
        val show = homeShellController?.shouldShowPermissionHint() == true
        binding.rowPermHint.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun corePermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.READ_PHONE_STATE)
    }

    private fun withCorePermissions(action: () -> Unit) {
        corePermQueue.clear()
        corePermQueue.addAll(corePermissions())
        corePermAction = action
        advanceCorePermissions()
    }

    private fun advanceCorePermissions() {
        val ctx = context ?: return
        val prefs = StorageRegistry(ctx)
        while (corePermQueue.isNotEmpty()) {
            val permission = corePermQueue.removeFirst()
            if (ContextCompat.checkSelfPermission(ctx, permission)
                == PackageManager.PERMISSION_GRANTED
            ) continue

            when {

                !prefs.hasRequestedPermission(permission) -> {
                    prefs.markPermissionRequested(permission)
                    lastCorePermission = permission
                    corePermLauncher.launch(permission)
                    return
                }

                shouldShowRequestPermissionRationale(permission) -> {
                    lastCorePermission = permission
                    corePermLauncher.launch(permission)
                    return
                }

                else -> continue
            }
        }

        val action = corePermAction
        corePermAction = null
        action?.invoke()
    }

    fun onShellShown() {
        maybeShowSearchHint()
    }

    fun onShellHidden() {
        searchHint?.dismiss()
        searchHint = null
    }

    private fun maybeShowSearchHint() {
        if (prefs.isSearchHintShown) return
        if (searchHint != null) return

        if (view == null || isHidden) return
        val anchor = binding.searchBarVw
        anchor.post {
            if (!isAdded || view == null || isHidden) return@post
            if (prefs.isSearchHintShown) return@post

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

    private fun setupHomeCountry() {

        val saved = prefs.homeCountryIso
        if (saved.length == 2) {
            applyHomeCountry(saved, dialFor(saved))
            return
        }

        val sim = simCountryIso()
        if (sim != null) {
            applyHomeCountry(sim, dialFor(sim))
            return
        }

        val region = java.util.Locale.getDefault().country
        val fallbackIso = if (region.length == 2) region else "US"
        applyHomeCountry(fallbackIso, dialFor(fallbackIso))
        detectCountryByIp()
    }

    private fun simCountryIso(): String? {
        val tm = context?.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        val iso = tm.simCountryIso?.takeIf { it.length == 2 }
            ?: tm.networkCountryIso?.takeIf { it.length == 2 }
        return iso?.uppercase()
    }

    private fun dialFor(iso: String): String =
        (DialCountries.byIso(iso)?.dial ?: DialCountries.dialOf(iso)).orEmpty()

    private fun detectCountryByIp() {
        viewLifecycleOwner.lifecycleScope.launch {
            val geo = RegionResolver.detectCountry(requireContext()) ?: return@launch

            if (view == null || prefs.homeCountryIso.isNotBlank()) return@launch

            val dial = geo.dial.ifBlank { dialFor(geo.iso) }

            applyHomeCountry(geo.iso, dial)
        }
    }

    private fun applyHomeCountry(iso: String, dial: String) {
        homeDial = dial
        binding.lblHomeFlag.text = DialCountries.flag(iso)
        binding.lblHomeDial.text = if (dial.isBlank()) iso else "+$dial"
    }

    private fun submitSearch() {
        val typed = binding.inpHomeSearch.text?.toString()?.trim().orEmpty()

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

        onShellHidden()
        super.onDestroyView()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            loadRecentIfAllowed()
            refreshPermissionHint()

            if (homeShell?.isShellVisible != false) maybeShowSearchHint()
        }
    }

    private fun loadRecentIfAllowed() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        binding.rowRecentPermission.visibility = if (granted) View.GONE else View.VISIBLE
        binding.rollRecent.visibility = if (granted) View.VISIBLE else View.GONE

        if (granted) viewModel.loadRecent()
    }
}
