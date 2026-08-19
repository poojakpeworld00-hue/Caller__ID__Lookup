package com.callerid.number.lookup.home.screen.history

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.callerid.number.lookup.home.R
import com.callerid.admesh.surface.InlinePromoStrip
import com.callerid.number.lookup.home.frame.HolderFragment
import com.callerid.number.lookup.home.kit.openActivity
import com.callerid.number.lookup.home.databinding.BoardRecentsBinding
import com.callerid.number.lookup.home.screen.report.CallDetailActivity
import com.callerid.number.lookup.home.screen.dialpad.DialPadActivity
import com.callerid.number.lookup.home.kit.followAdContainer
import com.callerid.number.lookup.home.screen.main.homeShellController
import com.callerid.number.lookup.home.screen.main.homeShell

class RecentsFragment : HolderFragment<BoardRecentsBinding>() {

    private val viewModel: LogViewModel by viewModels()
    private val adapter = LogAdapter(
        ::dialNumber,
        ::openDetail,
        onIdentify = { number -> homeShell?.showLookup(number) }
    )

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        BoardRecentsBinding.inflate(inflater, container, false)

    override fun initView() {
        // Hero bleeds under the status bar; pad its content down by the inset.
        val baseTop = binding.heroHeaderVw.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.heroHeaderVw) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = baseTop + top)
            insets
        }
        binding.padRecentsDial.setOnClickListener {
            requireActivity().openActivity<DialPadActivity>()
        }
        binding.padRecentsFilter.setOnClickListener { showSortMenu(it) }

        binding.rollRecents.layoutManager = LinearLayoutManager(requireContext())
        binding.rollRecents.adapter = adapter

        // Native banner at the bottom of the recents screen.
        InlinePromoStrip().showNativeBannerNative(requireActivity(), binding.adNativeFrameVw, binding.adShimmerVw)
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)
        binding.adNativeDivider1Vw.followAdContainer(binding.adNativeFrameVw)

        binding.segAll.setOnClickListener { viewModel.setFilter(LogScope.ALL) }
        binding.segIncoming.setOnClickListener { viewModel.setFilter(LogScope.INCOMING) }
        binding.segOutgoing.setOnClickListener { viewModel.setFilter(LogScope.OUTGOING) }
        binding.segMissed.setOnClickListener { viewModel.setFilter(LogScope.MISSED) }
        binding.padGrant.setOnClickListener {
            requestPermissionChain(
                listOf(Manifest.permission.READ_CALL_LOG)
            ) {
                if (hasCallLogPermission()) onPermissionGranted() else showPermissionState()
                homeShellController?.startOverlayPermissionFlow()
            }
        }

        binding.inpSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s?.toString().orEmpty()
                viewModel.setQuery(text)
                binding.padClearSearch.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            }
        })
        binding.padClearSearch.setOnClickListener { binding.inpSearch.setText("") }

        if (hasCallLogPermission()) onPermissionGranted() else showPermissionState()
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate after returning from Settings (or a system dialog) so a freshly
        // granted permission shows the list without needing to leave the screen.
        if (view != null) {
            if (hasCallLogPermission()) onPermissionGranted() else showPermissionState()
        }
    }

    override fun initObservers() {
        viewModel.rows.observe(viewLifecycleOwner) { rows ->
            adapter.submit(rows)
            binding.lblEmpty.visibility =
                if (rows.isEmpty() && hasCallLogPermission()) View.VISIBLE else View.GONE
        }
        viewModel.filter.observe(viewLifecycleOwner) { active ->
            highlightTab(binding.segAll, active == LogScope.ALL)
            highlightTab(binding.segIncoming, active == LogScope.INCOMING)
            highlightTab(binding.segOutgoing, active == LogScope.OUTGOING)
            highlightTab(binding.segMissed, active == LogScope.MISSED)
        }
    }

    private fun highlightTab(tab: TextView, active: Boolean) {
        tab.isActivated = active
        tab.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (active) R.color.white else R.color.on_surface_variant
            )
        )
        // Selected: tint the leading icon white. Unselected: clear the tint so the
        // icon keeps its own colour.
        TextViewCompat.setCompoundDrawableTintList(
            tab,
            if (active) {
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            } else {
                null
            }
        )
    }

    private fun hasCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Custom sort popup: a styled card anchored under the filter button that changes the
     * list order (date / name). Type filtering stays on the tabs.
     */
    private fun showSortMenu(anchor: View) {
        val options = listOf(
            R.string.sort_newest to LogOrder.NEWEST,
            R.string.sort_oldest to LogOrder.OLDEST,
            R.string.sort_name_asc to LogOrder.NAME_ASC,
            R.string.sort_name_desc to LogOrder.NAME_DESC
        )
        val current = viewModel.sort.value ?: LogOrder.NEWEST

        val inflater = LayoutInflater.from(requireContext())
        val content = inflater.inflate(R.layout.popup_sort, null) as LinearLayout
        val container = content.findViewById<LinearLayout>(R.id.sortContainerVw)

        val popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 8f * resources.displayMetrics.density
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        options.forEach { (titleRes, sort) ->
            val row = inflater.inflate(R.layout.cell_sort_option, container, false)
            row.findViewById<TextView>(R.id.lblSortLabel).setText(titleRes)
            row.findViewById<ImageView>(R.id.picSortCheck).visibility =
                if (sort == current) View.VISIBLE else View.INVISIBLE
            row.setOnClickListener {
                viewModel.setSort(sort)
                popup.dismiss()
            }
            container.addView(row)
        }

        val yOffset = (4f * resources.displayMetrics.density).toInt()
        popup.showAsDropDown(anchor, 0, yOffset, Gravity.END)
    }

    private fun onPermissionGranted() {
        binding.permStateVw.visibility = View.GONE
        binding.rollRecents.visibility = View.VISIBLE
        viewModel.load()
    }

    private fun showPermissionState() {
        binding.permStateVw.visibility = View.VISIBLE
        binding.rollRecents.visibility = View.GONE
        binding.lblEmpty.visibility = View.GONE
    }

    private fun dialNumber(number: String) = placeCall(number)

    private fun openDetail(entry: com.callerid.number.lookup.home.store.CallEntry) {
        requireActivity().openActivity(CallDetailActivity.newIntent(requireContext(), entry.number, entry.name))
    }
}
