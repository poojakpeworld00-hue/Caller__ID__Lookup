package com.callerid.number.lookup.home.screen.dialpad

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.number.lookup.home.store.ContactSource
import com.callerid.number.lookup.home.databinding.ScreenDialerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DialPadActivity : FrameActivity<ScreenDialerBinding>() {

    override val layoutId: Int = R.layout.screen_dialer

    private val viewModel: DialPadViewModel by viewModels()
    private val adapter = FrequentAdapter(onClick = ::setDial, onCall = ::fillAndDial)
    private val contactsRepo by lazy { ContactSource(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.dialerRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.padBack.setOnClickListener { goBack() }

        binding.rollFrequent.layoutManager = LinearLayoutManager(this)
        binding.rollFrequent.adapter = adapter

        binding.padBackspace.setOnClickListener { backspaceDial() }
        binding.padBackspace.setOnLongClickListener { setDial(""); true }
        binding.padDialCall.setOnClickListener { placeCall(dialedNumber()) }
        binding.rowAddContact.setOnClickListener { addToContacts(dialedNumber()) }

        binding.lblDialNumber.showSoftInputOnFocus = false
        binding.lblDialNumber.requestFocus()
        hideSystemKeyboard()

        setupKeypad()
        updateDialState()
        loadFrequentIfAllowed()
    }

    override fun initObservers() {
        viewModel.frequent.observe(this) { list ->
            adapter.submit(list)
            val hasMatches = list.isNotEmpty()
            binding.lblEmpty.visibility = if (hasMatches) View.GONE else View.VISIBLE
            binding.lblMatchesLabel.visibility = if (hasMatches) View.VISIBLE else View.GONE

            binding.lblMatchesLabel.setText(
                if (dialedNumber().isEmpty()) R.string.dialer_frequent else R.string.dialer_matches
            )

            hasNamedMatch = dialedNumber().isNotEmpty() && list.any { !it.name.isNullOrBlank() }
            applyAddContactVisibility()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemKeyboard()
    }

    private fun hideSystemKeyboard() {
        runCatching {
            WindowCompat.getInsetsController(window, binding.lblDialNumber)
                .hide(WindowInsetsCompat.Type.ime())
        }
    }

    private fun setupKeypad() {
        val grid = binding.gridKeypadVw
        for (i in 0 until grid.childCount) {
            val cell = grid.getChildAt(i)
            val key = cell.tag?.toString() ?: continue
            cell.setOnClickListener { appendDial(key) }
            if (key == "0") cell.setOnLongClickListener { appendDial("+"); true }
            cell.setOnTouchListener(keyPressScale)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private val keyPressScale = View.OnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90L).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f)
                    .setInterpolator(android.view.animation.OvershootInterpolator())
                    .setDuration(180L).start()
        }
        false
    }

    private fun dialedNumber(): String = binding.lblDialNumber.text?.toString().orEmpty()

    private fun appendDial(text: String) {
        binding.lblDialNumber.append(text)
        updateDialState()
    }

    private fun backspaceDial() {
        val text = binding.lblDialNumber.text
        if (text.isNotEmpty()) binding.lblDialNumber.setText(text.subSequence(0, text.length - 1))
        updateDialState()
    }

    private fun setDial(number: String) {
        binding.lblDialNumber.setText(number)
        updateDialState()
    }

    private var addContactJob: Job? = null

    private var savedExact = false

    private var hasNamedMatch = false

    private fun updateDialState() {
        val number = dialedNumber()

        binding.lblDialNumber.setSelection(number.length)
        val hasNumber = number.isNotEmpty()
        binding.padBackspace.visibility = if (hasNumber) View.VISIBLE else View.INVISIBLE
        if (hasNumber) {
            refreshAddContact(number)
        } else {
            addContactJob?.cancel()
            savedExact = false
            applyAddContactVisibility()
        }
        viewModel.filter(number)
    }

    private fun refreshAddContact(number: String) {
        addContactJob?.cancel()
        addContactJob = lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                contactsRepo.lookupNameByNumber(number) != null
            }

            if (dialedNumber() == number) {
                savedExact = saved
                applyAddContactVisibility()
            }
        }
    }

    private fun applyAddContactVisibility() {
        val show = dialedNumber().isNotEmpty() && !savedExact && !hasNamedMatch
        binding.rowAddContact.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    private fun fillAndDial(number: String) {
        setDial(number)
        placeCall(number)
    }

    private fun addToContacts(number: String) {
        if (number.isBlank()) return
        runCatching {
            val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, number)
            }
            startActivity(intent)
        }
    }

    private fun loadFrequentIfAllowed() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.load() else binding.lblEmpty.visibility = View.VISIBLE
    }
}
