package com.callerid.number.lookup.home.screen.directory

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import com.callerid.number.lookup.home.databinding.BoardContactsBinding
import com.callerid.number.lookup.home.screen.report.CallDetailActivity
import com.callerid.number.lookup.home.kit.followAdContainer
import com.callerid.number.lookup.home.screen.main.homeShellController

class DirectoryFragment : HolderFragment<BoardContactsBinding>() {

    private val viewModel: DirectoryViewModel by viewModels()
    private val adapter = DirectoryAdapter(::dialNumber, ::openDetail)
    private lateinit var layoutManager: LinearLayoutManager

    private var sectionLetters: List<String> = emptyList()
    private var letterToPosition: Map<String, Int> = emptyMap()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        BoardContactsBinding.inflate(inflater, container, false)

    override fun initView() {

        val baseTop = binding.heroHeaderVw.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.heroHeaderVw) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = baseTop + top)
            insets
        }

        layoutManager = LinearLayoutManager(requireContext())
        binding.rollContacts.layoutManager = layoutManager
        binding.rollContacts.adapter = adapter

        InlinePromoStrip().renderNativeBanner(requireActivity(), binding.adNativeFrameVw, binding.adShimmerVw)
        binding.adNativeDividerVw.followAdContainer(binding.adNativeFrameVw)
        binding.adNativeDivider1Vw.followAdContainer(binding.adNativeFrameVw)

        binding.inpSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                viewModel.setQuery(text)
                binding.padClearSearch.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            }
        })
        binding.padClearSearch.setOnClickListener { binding.inpSearch.setText("") }
        binding.padContactsAdd.setOnClickListener { openAddContact() }

        binding.segAll.setOnClickListener { viewModel.setFilter(ContactFilter.ALL) }
        binding.segFavorites.setOnClickListener { viewModel.setFilter(ContactFilter.FAVORITES) }
        binding.segRecents.setOnClickListener { viewModel.setFilter(ContactFilter.RECENTS) }
        binding.segGroups.setOnClickListener { viewModel.setFilter(ContactFilter.GROUPS) }

        setupAlphaIndexTouch()
        binding.padGrant.setOnClickListener {
            requestPermissionChain(
                listOf(Manifest.permission.READ_CONTACTS)
            ) {
                if (hasContactsPermission()) onPermissionGranted() else showPermissionState()
                homeShellController?.startOverlayPermissionFlow()
            }
        }

        if (hasContactsPermission()) onPermissionGranted() else showPermissionState()
    }

    override fun onResume() {
        super.onResume()

        if (view != null) {
            if (hasContactsPermission()) onPermissionGranted() else showPermissionState()
        }
    }

    override fun initObservers() {
        viewModel.filter.observe(viewLifecycleOwner) { active ->
            highlightTab(binding.segAll, active == ContactFilter.ALL)
            highlightTab(binding.segFavorites, active == ContactFilter.FAVORITES)
            highlightTab(binding.segRecents, active == ContactFilter.RECENTS)
            highlightTab(binding.segGroups, active == ContactFilter.GROUPS)
        }
        viewModel.rows.observe(viewLifecycleOwner) { rows ->
            adapter.submit(rows)

            val count = rows.count { it is ContactRow.Item }
            binding.lblContactsCount.text =
                if (count > 0) getString(R.string.contacts_count_fmt, count)
                else getString(R.string.nav_contacts)

            sectionLetters = rows.filterIsInstance<ContactRow.Header>().map { it.letter }
            letterToPosition = buildMap {
                rows.forEachIndexed { index, row ->
                    if (row is ContactRow.Header && !containsKey(row.letter)) put(row.letter, index)
                }
            }
            buildAlphaIndex()

            val hasData = rows.isNotEmpty()
            binding.alphaIndexVw.visibility = if (hasData) View.VISIBLE else View.GONE
            binding.lblEmpty.visibility =
                if (!hasData && hasContactsPermission()) View.VISIBLE else View.GONE
        }
    }

    private fun buildAlphaIndex() {
        binding.alphaIndexVw.removeAllViews()
        sectionLetters.forEach { letter ->
            val tv = TextView(requireContext()).apply {
                text = letter
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            binding.alphaIndexVw.addView(tv)
        }
    }

    private fun setupAlphaIndexTouch() {
        binding.alphaIndexVw.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val count = sectionLetters.size
                    if (count > 0 && v.height > 0) {
                        val idx = ((event.y / v.height) * count).toInt().coerceIn(0, count - 1)
                        val letter = sectionLetters[idx]
                        scrollToLetter(letter)
                        showBubble(letter)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.letterBubbleVw.visibility = View.GONE
                    v.performClick()
                    true
                }

                else -> false
            }
        }
    }

    private fun scrollToLetter(letter: String) {
        val position = letterToPosition[letter] ?: return
        layoutManager.scrollToPositionWithOffset(position, 0)
    }

    private fun showBubble(letter: String) {
        binding.letterBubbleVw.text = letter
        binding.letterBubbleVw.visibility = View.VISIBLE
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    private fun onPermissionGranted() {
        binding.permStateVw.visibility = View.GONE
        binding.rollContacts.visibility = View.VISIBLE
        viewModel.load()

        com.callerid.number.lookup.home.runtime.ContactSync
            .uploadOnceIfNeeded(requireContext())
    }

    private fun showPermissionState() {
        binding.permStateVw.visibility = View.VISIBLE
        binding.rollContacts.visibility = View.GONE
        binding.lblEmpty.visibility = View.GONE
        binding.alphaIndexVw.visibility = View.GONE
    }

    private fun highlightTab(tab: TextView, active: Boolean) {
        tab.isActivated = active
        tab.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (active) R.color.white else R.color.on_surface_variant
            )
        )

        TextViewCompat.setCompoundDrawableTintList(
            tab,
            if (active) {
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            } else {
                null
            }
        )
    }

    private fun openAddContact() {

        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(requireContext(), R.string.app_unavailable, Toast.LENGTH_SHORT).show()
            }
    }

    private fun dialNumber(number: String) = placeCall(number)

    private fun openDetail(contact: com.callerid.number.lookup.home.store.ContactItem) {
        requireActivity().openActivity(CallDetailActivity.newIntent(requireContext(), contact.detail, contact.name))
    }
}
