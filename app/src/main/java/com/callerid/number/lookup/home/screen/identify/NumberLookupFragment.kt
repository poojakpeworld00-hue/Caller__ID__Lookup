package com.callerid.number.lookup.home.screen.identify

import android.app.Activity
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.store.RegionResolver
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.BonusPromo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.callerid.number.lookup.home.frame.HolderFragment
import com.callerid.number.lookup.home.store.StorageRegistry
import com.callerid.number.lookup.home.kit.openActivity
import com.callerid.number.lookup.home.databinding.DlgWatchAdBinding
import com.callerid.number.lookup.home.databinding.BoardLookupBinding
import java.util.Locale

class NumberLookupFragment : HolderFragment<BoardLookupBinding>() {

    private val viewModel: LookupViewModel by viewModels()
    private lateinit var historyAdapter: TraceAdapter
    private var currentState: LookupState = LookupState.Idle

    private var pendingNumber: String? = null

    private var consumedClip: String? = null

    private val countryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val data = res.data ?: return@registerForActivityResult
            val iso = data.getStringExtra(CountryPickActivity.EXTRA_ISO) ?: return@registerForActivityResult
            val dial = data.getStringExtra(CountryPickActivity.EXTRA_DIAL).orEmpty()
            StorageRegistry(requireContext()).homeCountryIso = iso
            useCountry(iso, dial)
        }
    }

    private val historyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val number = res.data?.getStringExtra(LookupHistoryActivity.EXTRA_NUMBER)
                ?.takeIf { it.isNotBlank() } ?: return@registerForActivityResult
            binding.inpNumberInput.setText(number)
            binding.inpNumberInput.setSelection(number.length)
            viewModel.search(number)
            hideKeyboard()
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        BoardLookupBinding.inflate(inflater, container, false)

    override fun initView() {

        val baseTop = binding.heroHeaderVw.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.heroHeaderVw) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = baseTop + top)
            insets
        }
        setupCountryChip()

        BonusPromo.preload(requireContext())
        binding.rowCountryPickerSearch.setOnClickListener {
            countryLauncher.launch(Intent(requireContext(), CountryPickActivity::class.java))
        }

        binding.padLookupHistory.setOnClickListener {
            historyLauncher.launch(LookupHistoryActivity.newIntent(requireContext()))
        }

        historyAdapter = TraceAdapter(
            onClick = { entry -> binding.inpNumberInput.setText(entry.rawNumber); binding.inpNumberInput.setSelection(entry.rawNumber.length) },
            onCall = { entry -> dial(entry.rawNumber) },
            onRevealName = { entry -> revealHistoryName(entry) }
        )
        binding.rollHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rollHistory.adapter = historyAdapter

        binding.inpNumberInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                binding.picClear.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE

                if (text.isEmpty()) maybeShowPasteChip() else binding.flagPaste.visibility = View.GONE
            }
        })

        binding.inpNumberInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(binding.inpNumberInput.text?.toString().orEmpty())
                hideKeyboard()
                true
            } else false
        }

        binding.padSearch.setOnClickListener {
            viewModel.search(binding.inpNumberInput.text?.toString().orEmpty())
            hideKeyboard()
        }

        binding.picClear.setOnClickListener {
            binding.inpNumberInput.setText("")
            viewModel.clear()
        }

        binding.padEmptySearch.setOnClickListener {
            binding.inpNumberInput.requestFocus()
            showKeyboard()
        }

        binding.flagPaste.setOnClickListener {
            val n = (binding.flagPaste.tag as? String)?.takeIf { it.isNotBlank() }
                ?: binding.lblPasteNumber.text?.toString().orEmpty()
            if (n.isNotBlank()) {
                consumedClip = n
                binding.inpNumberInput.setText(n)
                binding.inpNumberInput.setSelection(n.length)
                viewModel.search(n)
                hideKeyboard()
                binding.flagPaste.visibility = View.GONE
            }
        }

        binding.inpNumberInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) maybeShowPasteChip()
        }

        binding.lblHistoryClearAll.setOnClickListener { viewModel.clearHistory() }

        consumePendingSearch()
    }

    override fun onResume() {
        super.onResume()

        if (view != null) {
            viewModel.refreshHistory()
            maybeShowPasteChip(autoFocusIfUsed = true)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && view != null) {
            maybeShowPasteChip(autoFocusIfUsed = true)

            historyAdapter.resetReveals()
        }
    }

    fun requestSearch(number: String) {
        pendingNumber = number
        if (_isViewReady()) consumePendingSearch()
    }

    private fun _isViewReady(): Boolean = view != null

    private fun consumePendingSearch() {
        val number = pendingNumber?.takeIf { it.isNotBlank() } ?: return
        pendingNumber = null
        binding.inpNumberInput.setText(number)
        binding.inpNumberInput.setSelection(number.length)
        viewModel.search(number)
        hideKeyboard()
    }

    override fun initObservers() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            currentState = state
            when (state) {
                is LookupState.Idle -> render(loading = false, result = null)
                is LookupState.Loading -> render(loading = true, result = null)
                is LookupState.Result -> render(loading = false, result = state.result)
            }
            maybeShowPasteChip()
        }
        viewModel.history.observe(viewLifecycleOwner) { items ->
            historyAdapter.submit(items)
            val hasHistory = items.isNotEmpty()
            binding.rowHistoryHeader.visibility = if (hasHistory) View.VISIBLE else View.GONE
            binding.rollHistory.visibility = if (hasHistory) View.VISIBLE else View.GONE
            updateEmptyState(hasHistory)
        }
    }

    private fun render(loading: Boolean, result: LookupResult?) {
        binding.shmResult.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) binding.shmResult.startShimmer() else binding.shmResult.stopShimmer()

        binding.scrlContent.visibility = if (loading) View.GONE else View.VISIBLE

        if (result != null) {
            binding.cvResultVw.visibility = View.VISIBLE
            bindResult(result)
        } else {
            binding.cvResultVw.visibility = View.GONE
        }
        updateEmptyState(binding.rollHistory.visibility == View.VISIBLE)
    }

    private fun updateEmptyState(hasHistory: Boolean) {
        val idle = currentState is LookupState.Idle
        binding.rowEmptyState.visibility =
            if (idle && !hasHistory) View.VISIBLE else View.GONE
    }

    private fun bindResult(result: LookupResult) {

        val hasName = !result.name.isNullOrBlank()
        val fullName = result.name?.takeIf { it.isNotBlank() } ?: getString(R.string.lookup_unknown_caller)
        binding.lblResName.text = if (hasName) blurName(fullName) else fullName
        binding.picRevealName.visibility = if (hasName) View.VISIBLE else View.GONE
        binding.lblResNumber.text = result.number

        when {
            result.isSpam -> bindStatusPill(
                R.string.lookup_spam_risk, R.color.danger, R.color.danger_soft, R.drawable.sym_warning
            )
            result.valid == true -> bindStatusPill(
                R.string.lookup_valid_number, R.color.success, R.color.success_soft, R.drawable.sym_verified
            )
            result.valid == false -> bindStatusPill(
                R.string.lookup_invalid_number, R.color.danger, R.color.danger_soft, R.drawable.sym_warning
            )
            else -> bindStatusPill(
                if (result.inContacts) R.string.lookup_in_contacts else R.string.lookup_not_in_contacts,
                R.color.on_surface_variant, R.color.neutral_soft, R.drawable.sym_info
            )
        }

        binding.lblResCountry.text = result.country
            ?: viewModel.regionName(result.rawNumber)
            ?: getString(
                if (result.regionCode.isEmpty()) R.string.lookup_local_number
                else R.string.lookup_international
            )
        binding.lblResCarrier.text = result.carrier?.takeIf { it.isNotBlank() }
            ?: getString(R.string.lookup_unknown_value)
        binding.lblResType.text = result.lineType?.takeIf { it.isNotBlank() }
            ?: getString(R.string.lookup_unknown_value)

        binding.padResCall.setOnClickListener { dial(result.rawNumber) }
        binding.padResShare.setOnClickListener { share(result) }

        val reveal = { revealFullDetail(result, fullName) }
        binding.picRevealName.setOnClickListener { reveal() }
        binding.padShowFullDetail.setOnClickListener { reveal() }
    }

    private fun revealFullDetail(result: LookupResult, fullName: String) {
        val act = activity ?: return
        val open = {
            if (view != null) {
                binding.lblResName.text = fullName
                binding.picRevealName.visibility = View.GONE
                requireActivity().openActivity(LookupResultActivity.newIntent(requireContext(), result), false)
            }
        }

        if (!PromoVault.getInstance(act).getBoolean("IsAdsON")) {
            open()
            return
        }

        val db = DlgWatchAdBinding.inflate(layoutInflater)
        val dialog = Dialog(act).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(db.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        db.lblPreviewName.text = blurName(fullName)
        db.lblPreviewNumber.text = result.number
        db.padWatchAd.setOnClickListener {
            dialog.dismiss()
            BonusPromo().show(act) { open() }
        }
        db.padCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun blurName(name: String): String =
        if (name.isNotEmpty()) name[0] + "•".repeat(name.length - 1) else name

    private fun revealHistoryName(entry: TraceRow) {
        val act = activity ?: return
        val name = entry.name ?: return
        BonusReveal.reveal(act, name, entry.number) {
            if (view != null) historyAdapter.revealName(entry.rawNumber)
        }
    }

    private fun setupCountryChip() {

        val saved = StorageRegistry(requireContext()).homeCountryIso
        if (saved.length == 2) {
            useCountry(saved, dialFor(saved))
            return
        }

        val sim = simCountryIso()
        if (sim != null) {
            useCountry(sim, dialFor(sim))
            return
        }

        val region = Locale.getDefault().country
        val fallbackIso = if (region.length == 2) region else "US"
        useCountry(fallbackIso, dialFor(fallbackIso))
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
            val iso = RegionResolver.detectCountry(requireContext())?.iso ?: return@launch

            if (view == null || StorageRegistry(requireContext()).homeCountryIso.isNotBlank()) return@launch
            val dial = dialFor(iso)
            if (dial.isBlank()) return@launch

            useCountry(iso, dial)
        }
    }

    private fun useCountry(iso: String, dial: String) {
        binding.lblFlagSearch.text = DialCountries.flag(iso)
        binding.lblCountrySearch.text = if (dial.isBlank()) iso else "+$dial"
        viewModel.setRegion(iso, dial)
    }

    private fun bindStatusPill(textRes: Int, fgColor: Int, bgColor: Int, iconRes: Int) {
        val fg = color(fgColor)
        binding.lblResValid.apply {
            setText(textRes)
            setTextColor(fg)
            backgroundTintList = ColorStateList.valueOf(color(bgColor))
            setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(fg))
        }
    }

    private fun color(resId: Int) = ContextCompat.getColor(requireContext(), resId)

    private fun dial(number: String) = placeCall(number)

    private fun share(result: LookupResult) {
        val details = buildString {
            append(result.name ?: getString(R.string.lookup_unknown_caller))
            append("\n").append(result.number)
            result.country?.let { append("\n").append(getString(R.string.lookup_country)).append(": ").append(it) }
            result.carrier?.let { append("\n").append(getString(R.string.lookup_carrier)).append(": ").append(it) }
            result.lineType?.let { append("\n").append(getString(R.string.lookup_line_type)).append(": ").append(it) }
        }
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, details)
            }
            startActivity(Intent.createChooser(intent, null))
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.inpNumberInput.windowToken, 0)
    }

    private fun showKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.inpNumberInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun maybeShowPasteChip(autoFocusIfUsed: Boolean = false) {
        if (view == null) return
        val idleEmpty = currentState is LookupState.Idle && binding.inpNumberInput.text.isNullOrBlank()
        if (!idleEmpty) {
            binding.flagPaste.visibility = View.GONE
            return
        }
        val clip = clipboardPhone()
        when {

            clip != null && clip != consumedClip -> {
                binding.flagPaste.tag = clip
                binding.lblPasteNumber.text =
                    runCatching { NumberInfo.format(clip) }.getOrNull()?.takeIf { it.isNotBlank() } ?: clip
                binding.flagPaste.visibility = View.VISIBLE
            }

            clip != null && clip == consumedClip && autoFocusIfUsed -> {
                binding.flagPaste.visibility = View.GONE
                binding.inpNumberInput.requestFocus()
                showKeyboard()
            }
            else -> binding.flagPaste.visibility = View.GONE
        }
    }

    private fun clipboardPhone(): String? {
        val cm = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(requireContext())?.toString()?.trim().orEmpty()
        return clip.takeIf { looksLikePhone(it) }
    }

    private fun looksLikePhone(s: String): Boolean {
        if (s.length < 6 || s.length > 20) return false
        if (s.count { it.isDigit() } < 6) return false
        return s.all { it.isDigit() || it in "+-().,  " }
    }
}
