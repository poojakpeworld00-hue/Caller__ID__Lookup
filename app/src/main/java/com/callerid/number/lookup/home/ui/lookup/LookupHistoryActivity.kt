package com.callerid.number.lookup.home.ui.lookup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.callerid.admesh.presentation.BonusPromo
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.base.FrameActivity
import com.callerid.number.lookup.home.databinding.ScreenLookupHistoryBinding

/**
 * Standalone list of recent number lookups. Tapping a row returns its number to the
 * caller (Lookup tab) to re-run the search; the phone icon dials directly.
 */
class LookupHistoryActivity : FrameActivity<ScreenLookupHistoryBinding>() {

    override val layoutId: Int = R.layout.screen_lookup_history

    private val viewModel: TraceViewModel by viewModels()
    private val adapter = TraceAdapter(
        onClick = ::returnNumber,
        onCall = { entry -> placeCall(entry.rawNumber) },
        onRevealName = ::revealName
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.lookupHistoryRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.btnBack.setOnClickListener { goBack() }
        binding.btnClearAll.setOnClickListener { viewModel.clear() }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
        // Warm up the rewarded ad that gates revealing caller names.
        BonusPromo.preload(this)
    }

    override fun initObservers() {
        viewModel.history.observe(this) { items ->
            adapter.submit(items)
            val empty = items.isEmpty()
            binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
            binding.btnClearAll.visibility = if (empty) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload in case the list changed while away (e.g. a new lookup was run).
        viewModel.load()
    }

    private fun returnNumber(entry: TraceRow) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_NUMBER, entry.rawNumber))
        finish()
    }

    /** Gate the name reveal behind a rewarded ad, then un-mask that row. */
    private fun revealName(entry: TraceRow) {
        val name = entry.name ?: return
        BonusReveal.reveal(this, name, entry.number) { adapter.revealName(entry.rawNumber) }
    }

    companion object {
        const val EXTRA_NUMBER = "extra_number"

        fun newIntent(context: Context): Intent =
            Intent(context, LookupHistoryActivity::class.java)
    }
}
