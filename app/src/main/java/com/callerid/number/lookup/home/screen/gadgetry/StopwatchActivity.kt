package com.callerid.number.lookup.home.screen.gadgetry

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.admesh.surface.InlinePromo
import com.callerid.number.lookup.home.databinding.ScreenStopwatchBinding
import com.callerid.number.lookup.home.databinding.CellLapBinding
import java.util.Locale

/** Stopwatch with lap recording. */
class StopwatchActivity : FrameActivity<ScreenStopwatchBinding>() {

    override val layoutId: Int = R.layout.screen_stopwatch

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var accumulatedMs = 0L
    private var startRealtime = 0L
    private var lastLapTotal = 0L
    private var lapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.stopwatchRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.padBack.setOnClickListener { goBack() }

        // Mid native, scrolls with the tool content.
        InlinePromo().renderMidNative(this, binding.adNativeFrameVw, binding.adShimmerVw)
        binding.padStartPause.setOnClickListener { if (running) pause() else start() }
        binding.padReset.setOnClickListener { reset() }
        binding.padLap.setOnClickListener { lap() }

        reset()
    }

    override fun onPause() {
        super.onPause()
        if (running) pause()
    }

    private fun start() {
        running = true
        startRealtime = SystemClock.elapsedRealtime()
        binding.padStartPause.setText(R.string.action_pause)
        binding.padStartPause.setIconResource(R.drawable.sym_pause)
        binding.padLap.isEnabled = true
        handler.post(tick)
    }

    private fun pause() {
        running = false
        handler.removeCallbacks(tick)
        accumulatedMs += SystemClock.elapsedRealtime() - startRealtime
        binding.padStartPause.setText(R.string.action_start)
        binding.padStartPause.setIconResource(R.drawable.sym_play)
        binding.padLap.isEnabled = false
        renderTime()
    }

    private fun reset() {
        running = false
        handler.removeCallbacks(tick)
        accumulatedMs = 0L
        lastLapTotal = 0L
        lapCount = 0
        binding.padStartPause.setText(R.string.action_start)
        binding.padStartPause.setIconResource(R.drawable.sym_play)
        binding.padLap.isEnabled = false
        binding.rowLaps.removeAllViews()
        binding.rowLaps.visibility = View.GONE
        binding.emptyStateVw.visibility = View.VISIBLE
        binding.lblLapCount.text = getString(R.string.stopwatch_laps, 0)
        renderTime()
    }

    private fun lap() {
        if (!running) return
        val total = elapsed()
        val split = total - lastLapTotal
        lastLapTotal = total
        lapCount++

        val row = CellLapBinding.inflate(LayoutInflater.from(this), binding.rowLaps, false)
        row.lblLapName.text = getString(R.string.stopwatch_lap_n, lapCount)
        row.lblLapSplit.text = format(split)
        row.lblLapTotal.text = format(total)
        binding.rowLaps.addView(row.root, 0) // newest on top

        binding.rowLaps.visibility = View.VISIBLE
        binding.emptyStateVw.visibility = View.GONE
        binding.lblLapCount.text = getString(R.string.stopwatch_laps, lapCount)
    }

    private fun elapsed(): Long =
        accumulatedMs + if (running) SystemClock.elapsedRealtime() - startRealtime else 0L

    private val tick = object : Runnable {
        override fun run() {
            renderTime()
            handler.postDelayed(this, 30)
        }
    }

    private fun renderTime() {
        val text = format(elapsed())
        binding.lblTime.text = text
        binding.lblTotal.text = text
    }

    /** mm:ss.cc (centiseconds). */
    private fun format(ms: Long): String {
        val totalSec = ms / 1000
        val cs = (ms % 1000) / 10
        return String.format(Locale.getDefault(), "%02d:%02d.%02d", totalSec / 60, totalSec % 60, cs)
    }
}
