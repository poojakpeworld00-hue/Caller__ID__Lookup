package com.callerid.number.lookup.home.screen.gadgetry

import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.admesh.surface.InlinePromo
import com.callerid.number.lookup.home.databinding.ScreenLevelBinding
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** A bubble (spirit) level driven by the accelerometer. */
class LevelToolActivity : FrameActivity<ScreenLevelBinding>(), SensorEventListener {

    override val layoutId: Int = R.layout.screen_level

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val gravity = FloatArray(3)
    private var maxOffsetPx = 0f

    // Calibration offsets captured by the Calibrate button.
    private var calRoll = 0f
    private var calPitch = 0f
    private var rawRoll = 0f
    private var rawPitch = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.levelRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.padBack.setOnClickListener { goBack() }

        // Mid native, scrolls with the tool content.
        InlinePromo().showMidNative2(this, binding.adNativeFrameVw, binding.adShimmerVw)
        binding.padCalibrate.setOnClickListener {
            // Treat the current orientation as perfectly level.
            calRoll = rawRoll
            calPitch = rawPitch
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // The bubble travels from the centre toward the rim of the dial.
        binding.dialAreaVw.post {
            maxOffsetPx = (binding.dialAreaVw.width - binding.bubbleVw.width) / 2f * 0.82f
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        // Low-pass filter to steady the reading.
        val a = 0.2f
        for (i in 0..2) gravity[i] = gravity[i] + a * (event.values[i] - gravity[i])
        val (x, y, z) = gravity

        rawRoll = Math.toDegrees(atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
        rawPitch = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat()

        val roll = rawRoll - calRoll
        val pitch = rawPitch - calPitch

        // Full deflection (~30°) pushes the bubble to the rim.
        val k = maxOffsetPx / 30f
        binding.bubbleVw.translationX = (roll * k).coerceIn(-maxOffsetPx, maxOffsetPx)
        binding.bubbleVw.translationY = (-pitch * k).coerceIn(-maxOffsetPx, maxOffsetPx)

        val tilt = sqrt(roll * roll + pitch * pitch)
        binding.lblTilt.text = String.format(Locale.getDefault(), "%.1f°", tilt)
        binding.lblX.text = String.format(Locale.getDefault(), "%.1f°", roll)
        binding.lblY.text = String.format(Locale.getDefault(), "%.1f°", pitch)
        bindStatus(tilt)
    }

    /** "Level" (green) when nearly flat, otherwise "Adjusting" (blue). */
    private fun bindStatus(tilt: Float) {
        val level = abs(tilt) < 1f
        binding.lblStatus.setText(if (level) R.string.level_level else R.string.level_adjusting)
        val fg = if (level) R.color.success else R.color.primary
        val bg = if (level) R.color.success_soft else R.color.primary_container
        binding.lblStatus.setTextColor(ContextCompat.getColor(this, fg))
        binding.lblStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bg))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
