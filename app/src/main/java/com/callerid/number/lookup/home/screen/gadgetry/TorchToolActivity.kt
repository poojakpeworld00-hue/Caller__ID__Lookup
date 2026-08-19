package com.callerid.number.lookup.home.screen.gadgetry

import android.content.res.ColorStateList
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.admesh.surface.InlinePromo
import com.callerid.number.lookup.home.databinding.ScreenFlashlightBinding
import kotlin.math.max
import kotlin.math.roundToInt

/** Torch with Steady / Strobe / SOS modes and (where supported) brightness control. */
class TorchToolActivity : FrameActivity<ScreenFlashlightBinding>() {

    override val layoutId: Int = R.layout.screen_flashlight

    private enum class Mode { STEADY, STROBE, SOS }

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var maxStrength = 1

    private var active = false
    private var mode = Mode.STEADY
    private var brightnessPct = 100

    private val handler = Handler(Looper.getMainLooper())
    private var strobeOn = false
    // SOS = ...---... : on/off durations in ms, looping.
    private val sosPattern = longArrayOf(
        200, 200, 200, 200, 200, 400,
        500, 200, 500, 200, 500, 400,
        200, 200, 200, 200, 200, 1200
    )
    private var sosIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.flashlightRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.padBack.setOnClickListener { goBack() }

        // Mid native, scrolls with the tool content.
        InlinePromo().renderMidNative(this, binding.adNativeFrameVw, binding.adShimmerVw)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = findFlashCamera()
        if (cameraId == null) {
            binding.lblNoFlash.visibility = View.VISIBLE
            binding.contentVw.visibility = View.GONE
            return
        }

        binding.padToggle.setOnClickListener { toggleActive() }
        binding.modeSteadyVw.setOnClickListener { selectMode(Mode.STEADY) }
        binding.modeStrobeVw.setOnClickListener { selectMode(Mode.STROBE) }
        binding.modeSosVw.setOnClickListener { selectMode(Mode.SOS) }

        binding.seekBrightnessVw.progress = brightnessPct
        binding.seekBrightnessVw.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                brightnessPct = value
                if (active && mode == Mode.STEADY) applyTorch(true)
                updateReadout()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        selectMode(Mode.STEADY)
        updatePowerUi()
        updateReadout()
    }

    private fun findFlashCamera(): String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            val c = cameraManager.getCameraCharacteristics(id)
            val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            if (hasFlash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                maxStrength = c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            }
            hasFlash
        }
    }.getOrNull()

    private fun toggleActive() {
        active = !active
        if (active) startMode() else stopAll()
        updatePowerUi()
        updateReadout()
    }

    private fun selectMode(target: Mode) {
        mode = target
        highlightModes()
        if (active) startMode() // restart with the new mode
        updateReadout()
    }

    private fun startMode() {
        handler.removeCallbacksAndMessages(null)
        when (mode) {
            Mode.STEADY -> applyTorch(true)
            Mode.STROBE -> handler.post(strobeTick)
            Mode.SOS -> { sosIndex = 0; handler.post(sosTick) }
        }
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        applyTorch(false)
    }

    /** Turns the torch on/off, using the brightness level where the device supports it. */
    private fun applyTorch(on: Boolean) {
        val id = cameraId ?: return
        runCatching {
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxStrength > 1) {
                val level = max(1, (brightnessPct / 100f * maxStrength).roundToInt())
                cameraManager.turnOnTorchWithStrengthLevel(id, level)
            } else {
                cameraManager.setTorchMode(id, on)
            }
        }
    }

    private val strobeTick = object : Runnable {
        override fun run() {
            strobeOn = !strobeOn
            applyTorch(strobeOn)
            handler.postDelayed(this, 90)
        }
    }

    private val sosTick = object : Runnable {
        override fun run() {
            applyTorch(sosIndex % 2 == 0)
            val duration = sosPattern[sosIndex]
            sosIndex = (sosIndex + 1) % sosPattern.size
            handler.postDelayed(this, duration)
        }
    }

    private fun updatePowerUi() {
        binding.padToggle.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (active) R.color.primary else R.color.on_surface_variant)
        )
        binding.padToggle.backgroundTintList = if (active) {
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_container))
        } else null
    }

    private fun updateReadout() {
        binding.lblBrightnessPct.text = "$brightnessPct%"
        // Faux lumen output: scales with brightness while the torch is on.
        val lumen = if (active) (brightnessPct / 100f * NOMINAL_LUMENS).roundToInt() else 0
        binding.lblLumen.text = getString(R.string.flashlight_lm, lumen)
    }

    private fun highlightModes() {
        setMode(binding.modeSteadyVw, binding.picSteady, binding.lblSteady, mode == Mode.STEADY)
        setMode(binding.modeStrobeVw, binding.picStrobe, binding.lblStrobe, mode == Mode.STROBE)
        setMode(binding.modeSosVw, binding.picSos, binding.lblSos, mode == Mode.SOS)
    }

    private fun setMode(container: View, icon: ImageView, label: TextView, selected: Boolean) {
        container.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (selected) R.color.primary else R.color.surface)
        )
        val fg = ContextCompat.getColor(this, if (selected) R.color.white else R.color.on_surface_variant)
        icon.imageTintList = ColorStateList.valueOf(fg)
        label.setTextColor(fg)
    }

    override fun onPause() {
        super.onPause()
        active = false
        stopAll()
        updatePowerUi()
        updateReadout()
    }

    private companion object {
        const val NOMINAL_LUMENS = 50
    }
}
