package com.callerid.number.lookup.home.screen.gadgetry

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.number.lookup.home.databinding.ScreenToolsBinding
import com.callerid.number.lookup.home.kit.openActivity

/**
 * Grid of mini-tools grouped into Measure · Device · Time, with instant search
 * and a friendly empty state. Each tile launches its own activity.
 */
class ToolboxActivity : FrameActivity<ScreenToolsBinding>() {

    override val layoutId: Int = R.layout.screen_tools

    private val adapter = ToolAdapter { tool -> openActivity(Intent(this, tool.target)) }

    /** Full tool set, in display order, with the controlled 6-hue palette. */
    private val tools: List<ToolUi> by lazy {
        val measure = getString(R.string.tools_cat_measure)
        val device = getString(R.string.tools_cat_device)
        val time = getString(R.string.tools_cat_time)
        listOf(
            // Tile colour per tool matches Claude Design's exact assignment
            // (g-700 / teal / clay / amber -- bg_tile_blue/teal/violet/amber
            // are now flat fills in those colours, not the old 6-hue gradients).
            ToolUi(getString(R.string.tools_compass), getString(R.string.tools_compass_sub),
                R.drawable.sym_tool_compass, R.drawable.form_tile_blue, measure, CompassToolActivity::class.java),
            ToolUi(getString(R.string.tools_level), getString(R.string.tools_level_sub),
                R.drawable.sym_tool_level, R.drawable.form_tile_teal, measure, LevelToolActivity::class.java),
            ToolUi(getString(R.string.tools_sound), getString(R.string.tools_sound_sub),
                R.drawable.sym_tool_sound, R.drawable.form_tile_violet, measure, NoiseToolActivity::class.java),
            ToolUi(getString(R.string.tools_light), getString(R.string.tools_light_sub),
                R.drawable.sym_tool_light, R.drawable.form_tile_amber, measure, LightMeterActivity::class.java),
            ToolUi(getString(R.string.tools_flashlight), getString(R.string.tools_flashlight_sub),
                R.drawable.sym_tool_flashlight, R.drawable.form_tile_amber, device, TorchToolActivity::class.java),
            ToolUi(getString(R.string.tools_battery), getString(R.string.tools_battery_sub),
                R.drawable.sym_tool_battery, R.drawable.form_tile_blue, device, BatteryToolActivity::class.java),
            ToolUi(getString(R.string.tools_sim), getString(R.string.tools_sim_sub),
                R.drawable.sym_tool_network, R.drawable.form_tile_teal, device, SimInfoActivity::class.java),
            ToolUi(getString(R.string.tools_speedometer), getString(R.string.tools_speedometer_sub),
                R.drawable.sym_tool_speedometer, R.drawable.form_tile_violet, device, SpeedToolActivity::class.java),
            ToolUi(getString(R.string.tools_stopwatch), getString(R.string.tools_stopwatch_sub),
                R.drawable.sym_tool_stopwatch, R.drawable.form_tile_blue, time, StopwatchActivity::class.java),
            ToolUi(getString(R.string.timer_tool), getString(R.string.timer_tool_sub),
                R.drawable.sym_tool_timer, R.drawable.form_tile_teal, time, CountdownActivity::class.java),
        )
    }

    /** Category display order for grouping. */
    private val categoryOrder: List<String> by lazy {
        listOf(
            getString(R.string.tools_cat_measure),
            getString(R.string.tools_cat_device),
            getString(R.string.tools_cat_time),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolsRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.padBack.setOnClickListener { goBack() }

        val gridManager = GridLayoutManager(this, 2)
        gridManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (adapter.isHeader(position)) 2 else 1
        }
        binding.rollTools.layoutManager = gridManager
        binding.rollTools.adapter = adapter

        setupSearch()
        applyQuery("")
    }

    private fun setupSearch() {
        binding.inpSearch.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            binding.padClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            updateSearchChrome(query)
            applyQuery(query)
        }
        binding.inpSearch.setOnFocusChangeListener { _, _ ->
            updateSearchChrome(binding.inpSearch.text?.toString().orEmpty())
        }
        binding.padClearSearch.setOnClickListener { binding.inpSearch.setText("") }
        binding.padResetSearch.setOnClickListener { binding.inpSearch.setText("") }
    }

    /** Accent ring on the search pill while focused or typing. */
    private fun updateSearchChrome(query: String) {
        val active = query.isNotEmpty() || binding.inpSearch.hasFocus()
        binding.searchBarVw.setBackgroundResource(
            if (active) R.drawable.form_search_bar_active else R.drawable.form_search_bar
        )
    }

    private fun applyQuery(query: String) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) tools
        else tools.filter { it.name.contains(q, true) || it.hint.contains(q, true) }

        if (filtered.isEmpty()) {
            binding.lblEmptyTitle.text = getString(R.string.tools_empty_title, q)
            binding.rollTools.visibility = View.GONE
            binding.emptyToolsVw.visibility = View.VISIBLE
        } else {
            binding.emptyToolsVw.visibility = View.GONE
            binding.rollTools.visibility = View.VISIBLE
            adapter.submit(buildRows(filtered))
        }
    }

    /** Groups filtered tools under their category headers, in display order. */
    private fun buildRows(items: List<ToolUi>): List<ToolRow> {
        val rows = mutableListOf<ToolRow>()
        for (category in categoryOrder) {
            val inCategory = items.filter { it.category == category }
            if (inCategory.isEmpty()) continue
            rows.add(ToolRow.Header(category))
            inCategory.forEach { rows.add(ToolRow.Tool(it)) }
        }
        return rows
    }
}
