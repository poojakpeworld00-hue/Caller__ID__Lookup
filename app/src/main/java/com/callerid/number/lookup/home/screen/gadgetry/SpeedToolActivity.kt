package com.callerid.number.lookup.home.screen.gadgetry

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.admesh.surface.InlinePromo
import com.callerid.number.lookup.home.databinding.ScreenSpeedometerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Internet speed test: download / upload throughput, latency and jitter over HTTP. */
class SpeedToolActivity : FrameActivity<ScreenSpeedometerBinding>() {

    override val layoutId: Int = R.layout.screen_speedometer

    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.speedRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.padBack.setOnClickListener { goBack() }

        // Mid native, scrolls with the tool content.
        InlinePromo().renderMidNative2(this, binding.adNativeFrameVw, binding.adShimmerVw)
        binding.pbGaugeVw.isIndeterminate = false
        binding.pbGaugeVw.max = 100
        binding.barUploadVw.max = 100
        binding.barLatencyVw.max = 100
        binding.barJitterVw.max = 100

        binding.padRetest.setOnClickListener { runTest() }
    }

    override fun onResume() {
        super.onResume()
        if (job?.isActive != true) runTest()
    }

    override fun onPause() {
        super.onPause()
        job?.cancel()
    }

    private fun runTest() {
        job?.cancel()
        resetUi()
        binding.lblStatus.setText(R.string.speedometer_waiting)

        job = lifecycleScope.launch {
            // 1) Latency + jitter
            val (latency, jitter) = withContext(Dispatchers.IO) { measureLatency() }
            if (latency < 0) {
                binding.lblStatus.setText(R.string.speedtest_error)
                return@launch
            }
            binding.lblLatency.text = getString(R.string.speedtest_ms, latency)
            binding.lblJitter.text = getString(R.string.speedtest_ms, jitter)
            binding.barLatencyVw.setProgressCompat(pct(latency.toFloat(), 150f), true)
            binding.barJitterVw.setProgressCompat(pct(jitter.toFloat(), 30f), true)

            // 2) Download (with live gauge)
            val download = measureDownload { live -> showSpeed(live) }
            showSpeed(download)

            // 3) Upload (best-effort)
            val upload = withContext(Dispatchers.IO) { measureUpload() }
            binding.lblUpload.text = getString(R.string.speedtest_mbps, oneDp(upload))
            binding.barUploadVw.setProgressCompat(pct(upload, MAX_MBPS), true)

            binding.lblStatus.setText(R.string.speedtest_stable)
        }
    }

    private fun showSpeed(mbps: Float) {
        binding.lblSpeed.text = mbps.roundToInt().toString()
        binding.pbGaugeVw.setProgressCompat(pct(mbps, MAX_MBPS), true)
    }

    private fun resetUi() {
        binding.lblSpeed.text = "0"
        binding.pbGaugeVw.setProgressCompat(0, false)
        binding.lblUpload.text = getString(R.string.speedtest_mbps, "0")
        binding.lblLatency.text = getString(R.string.speedtest_ms, 0)
        binding.lblJitter.text = getString(R.string.speedtest_ms, 0)
        binding.barUploadVw.setProgressCompat(0, false)
        binding.barLatencyVw.setProgressCompat(0, false)
        binding.barJitterVw.setProgressCompat(0, false)
    }

    /** Returns avg latency (ms) and jitter (ms), or (-1, 0) if unreachable. */
    private fun measureLatency(): Pair<Int, Int> {
        val samples = mutableListOf<Long>()
        repeat(5) {
            runCatching {
                val start = SystemClock.elapsedRealtime()
                (URL(LATENCY_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                    connect()
                    responseCode
                    disconnect()
                }
                samples.add(SystemClock.elapsedRealtime() - start)
            }
        }
        if (samples.isEmpty()) return -1 to 0
        val avg = samples.average()
        val jitter = if (samples.size > 1) {
            (1 until samples.size).map { abs(samples[it] - samples[it - 1]).toDouble() }.average()
        } else 0.0
        return avg.roundToInt() to jitter.roundToInt()
    }

    /** Streams a fixed payload and reports live + final Mbps. */
    private suspend fun measureDownload(onLive: (Float) -> Unit): Float {
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 8000
                }
                conn.inputStream.use { input ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    val start = SystemClock.elapsedRealtime()
                    var lastPost = start
                    while (isActive) {
                        val read = input.read(buf)
                        if (read < 0) break
                        total += read
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastPost >= 150) {
                            lastPost = now
                            val live = mbps(total, now - start)
                            withContext(Dispatchers.Main) { onLive(live) }
                        }
                        if (now - start > 12_000) break
                    }
                    conn.disconnect()
                    mbps(total, SystemClock.elapsedRealtime() - start)
                }
            }.getOrDefault(0f)
        }
    }

    private fun measureUpload(): Float {
        return runCatching {
            val size = 5 * 1024 * 1024
            val conn = (URL(UPLOAD_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                doOutput = true
                requestMethod = "POST"
                setFixedLengthStreamingMode(size)
            }
            val start = SystemClock.elapsedRealtime()
            conn.outputStream.use { out ->
                val buf = ByteArray(16 * 1024)
                var sent = 0
                while (sent < size) {
                    val chunk = minOf(buf.size, size - sent)
                    out.write(buf, 0, chunk)
                    sent += chunk
                }
                out.flush()
            }
            conn.responseCode
            val elapsed = SystemClock.elapsedRealtime() - start
            conn.disconnect()
            mbps(size.toLong(), elapsed)
        }.getOrDefault(0f)
    }

    private fun mbps(bytes: Long, ms: Long): Float =
        if (ms <= 0) 0f else (bytes * 8f / (ms / 1000f) / 1_000_000f)

    private fun pct(value: Float, max: Float): Int =
        (value / max * 100f).roundToInt().coerceIn(0, 100)

    private fun oneDp(value: Float) = String.format(Locale.getDefault(), "%.1f", value)

    private companion object {
        const val MAX_MBPS = 100f
        const val LATENCY_URL = "https://www.google.com/generate_204"
        const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=20000000"
        const val UPLOAD_URL = "https://speed.cloudflare.com/__up"
    }
}
