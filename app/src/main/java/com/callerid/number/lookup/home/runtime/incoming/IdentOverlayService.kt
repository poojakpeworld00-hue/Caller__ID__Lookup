package com.callerid.number.lookup.home.runtime.incoming

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.kit.InstallIdRegistry
import com.callerid.number.lookup.home.runtime.CallEndGuard
import com.callerid.number.lookup.home.runtime.IdentOverlayCard
import com.callerid.number.lookup.home.screen.ringing.RingScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IdentOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private val callEndWatcher by lazy { CallEndGuard(this) { stopSelf() } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        
        startAsForeground()

        val number = intent?.getStringExtra(EXTRA_NUMBER)?.takeIf { it.isNotBlank() } ?: run {
            stopSelf(); return START_NOT_STICKY
        }

        val canOverlay = Settings.canDrawOverlays(this)
        val keyguard = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        val locked = keyguard?.isKeyguardLocked == true

        
        if (locked || !canOverlay) {
            if (!canOverlay && !InstallIdRegistry.holdsSystemDefaultRole(this)) {
                
                Log.w(TAG, "no overlay permission and no default role — no caller-ID card")
                stopSelf(); return START_NOT_STICKY
            }
            runCatching { startActivity(RingScreenActivity.newIntent(this, number)) }
                .onFailure { Log.w(TAG, "caller-ID activity start refused", it) }
            stopSelf()
            return START_NOT_STICKY
        }

        showOverlay(number)
        callEndWatcher.start()
        return START_STICKY
    }

    private fun showOverlay(number: String) {

        removeOverlay()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(this).inflate(R.layout.part_caller_id, null)
        view.findViewById<View>(R.id.padIncallClose).setOnClickListener { stopSelf() }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            val density = resources.displayMetrics.density

            width = resources.displayMetrics.widthPixels - (24 * density).toInt()
        }

        runCatching {
            windowManager?.addView(view, params)
            overlayView = view
        }.onFailure {
            Log.w(TAG, "addView failed", it)
            stopSelf()
            return
        }

        scope.launch {
            
            overlayView?.let { IdentOverlayCard.bindResolving(this@IdentOverlayService, it, number) }
        }
    }

    private fun removeOverlay() {
        overlayView?.let { v -> runCatching { windowManager?.removeView(v) } }
        overlayView = null
    }

    private fun startAsForeground() {
        val channelId = "caller_id_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.incall_service_active), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.incall_service_active))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, notification)
            }
        }.onFailure { Log.w(TAG, "startForeground refused — running as plain service", it) }
    }

    override fun onDestroy() {
        callEndWatcher.stop()
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallerOverlay"

        const val EXTRA_NUMBER = "extra_number"

        /**
         * How long a start for one number suppresses a second start for the same number.
         *
         * The card has two triggers: [CallScreenService.onScreenCall], which fires before the
         * phone rings whenever we hold the CallScreening role, and [PhoneStateReceiver]'s
         * RINGING broadcast, which is the only trigger without the role. When we do hold it
         * both fire for the same call, milliseconds apart — this window swallows the second.
         *
         * It has to be a plain timestamp rather than an "is the service running" flag: on a
         * locked device the service hands off to [RingScreenActivity] and immediately stops
         * itself, so by the time the broadcast lands there is no service left to check and
         * the hand-off would happen twice.
         */
        private const val DEDUPE_WINDOW_MS = 5_000L

        private var lastStartedNumber: String? = null
        private var lastStartedAt = 0L

        fun start(context: Context, number: String) {
            if (isDuplicateStart(number)) {
                Log.d(TAG, "card already raised for $number — duplicate start ignored")
                return
            }
            lastStartedNumber = number
            lastStartedAt = System.currentTimeMillis()

            val intent = Intent(context, IdentOverlayService::class.java)
                .putExtra(EXTRA_NUMBER, number)
            runCatching {
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "overlay service start refused", it) }
        }

        fun stop(context: Context) {
            
            lastStartedNumber = null
            lastStartedAt = 0L
            runCatching { context.stopService(Intent(context, IdentOverlayService::class.java)) }
        }

        private fun isDuplicateStart(number: String): Boolean {
            val previous = lastStartedNumber ?: return false
            if (System.currentTimeMillis() - lastStartedAt > DEDUPE_WINDOW_MS) return false
            return sameNumber(previous, number)
        }

        /**
         * Compares on the last 10 digits: the screening service reports the raw SIP/tel
         * handle while the broadcast can carry a locally formatted one, and a strict
         * equals would let the duplicate through.
         */
        private fun sameNumber(a: String, b: String): Boolean {
            val x = a.filter(Char::isDigit).takeLast(10)
            return x.isNotEmpty() && x == b.filter(Char::isDigit).takeLast(10)
        }
    }
}
