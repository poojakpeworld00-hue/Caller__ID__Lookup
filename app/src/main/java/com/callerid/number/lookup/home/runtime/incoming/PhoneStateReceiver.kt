package com.callerid.number.lookup.home.runtime.incoming

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.OpenPromoRegistry
import com.callerid.admesh.surface.tally.OverlayViewRegistry
import com.callerid.admesh.surface.tally.ShellSurfaceScreen
import com.callerid.admesh.surface.tally.jobs.ShellJobRunner.Companion.NOTIFICATION_ID
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.store.BlockListRegistry
import com.callerid.number.lookup.home.shell.ext.isDefaultLauncher
import com.callerid.number.lookup.home.screen.ringing.RingScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (!number.isNullOrEmpty()) lastNumber = number
        Log.d(TAG, "state=$state number=$number")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                wasRinging = true
                wasOffhook = false
                callStartTime = System.currentTimeMillis()

                if (!number.isNullOrBlank() && BlockListRegistry(context).isBlocked(number)) {
                    wasBlocked = true
                    Log.d(TAG, "blocked number rejected: $number")
                    endCall(context)
                    return
                }

                if (!number.isNullOrBlank() && Settings.canDrawOverlays(context)) {
                    IdentOverlayService.start(context, number)
                } else if (number.isNullOrBlank()) {
                    Log.w(TAG, "ringing without a number — skipping caller-ID card")
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                wasOffhook = true
                if (callStartTime == 0L) callStartTime = System.currentTimeMillis()
                dismissCard(context)
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                dismissCard(context)

                if (wasBlocked) {
                    resetState()
                    return
                }

                val now = System.currentTimeMillis()
                if (now - lastTime < 2000) {
                    Log.d(TAG, "duplicate IDLE skipped")
                    resetState()
                    return
                }
                lastTime = now

                val phoneNumber = lastNumber ?: "Private Number"
                val endTime = Date()
                val startTime = if (callStartTime > 0) Date(callStartTime) else endTime
                val callType = when {
                    wasRinging && !wasOffhook -> "MISSED"
                    wasRinging && wasOffhook -> "INCOMING"
                    !wasRinging && wasOffhook -> "OUTGOING"
                    else -> "UNKNOWN"
                }

                handlePostCall(context, phoneNumber, startTime, endTime, callType)
                resetState()
            }
        }
    }

    private fun dismissCard(context: Context) {
        IdentOverlayService.stop(context)
        context.sendBroadcast(Intent(ACTION_CALL_ENDED).setPackage(context.packageName))
    }

    private fun resetState() {
        callStartTime = 0L
        lastNumber = null
        wasRinging = false
        wasOffhook = false
        wasBlocked = false
    }

    @Suppress("DiscouragedPrivateApi", "PrivateApi")
    private fun endCall(context: Context) {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val method = tm.javaClass.getDeclaredMethod("getITelephony").apply { isAccessible = true }
            val telephony = method.invoke(tm) ?: return
            telephony.javaClass.getDeclaredMethod("endCall").invoke(telephony)
        } catch (e: Exception) {
            Log.e(TAG, "endCall failed", e)
        }
    }

    private fun handlePostCall(
        context: Context, phoneNumber: String, startTime: Date, endTime: Date, type: String
    ) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (!PromoVault.getInstance(context).getBoolean("HD_VBC_Show")) return@launch

                OpenPromoRegistry.callbackshow = true
                delay(500)

                val hasOverlay = canShowOverlay(context)
                val started = when {
                    hasOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                        runCatching {
                            OverlayViewRegistry(context)
                                .renderRingbackScreen(phoneNumber, startTime, endTime, type)
                        }.isSuccess

                    hasOverlay || holdsSystemDefaultRole(context) ->
                        runCatching {
                            launchCallbackScreen(context, phoneNumber, startTime, endTime, type)
                        }.isSuccess

                    else -> false
                }

                if (!started) {
                    showFullScreenNotification(context, phoneNumber, startTime, endTime, type)
                    return@launch
                }

                if (!hasOverlay) {
                    delay(CALLBACK_CONFIRM_MS)
                    if (!ShellSurfaceScreen.isActive) {
                        Log.w(TAG, "role-backed callback start did not surface — notifying")
                        showFullScreenNotification(context, phoneNumber, startTime, endTime, type)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun canShowOverlay(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun holdsSystemDefaultRole(context: Context): Boolean {
        if (runCatching { context.isDefaultLauncher() }.getOrDefault(false)) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return runCatching {
            listOf(RoleManager.ROLE_DIALER, RoleManager.ROLE_CALL_SCREENING).any {
                rm.isRoleAvailable(it) && rm.isRoleHeld(it)
            }
        }.getOrDefault(false)
    }

    private fun launchCallbackScreen(
        context: Context, phone: String, start: Date, end: Date, type: String
    ) {
        try {
            val intent = Intent(context, ShellSurfaceScreen::class.java).apply {
                putExtra("phone", phone)
                putExtra("start_time", start.time)
                putExtra("end_time", end.time)
                putExtra("call_type", type)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "callback activity launch failed", e)
        }
    }

    private fun showFullScreenNotification(
        context: Context, phone: String, start: Date, end: Date, type: String
    ) {
        Log.e(TAG, "showFullScreenNotification: ")
        if (ShellSurfaceScreen.isActive || RingScreenActivity.isActive) {
            Log.d(TAG, "post-call screen in foreground — suppressing notification")
            return
        }

        val channelId = "post_call_channel"
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Post Call Info", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows callback screen after a call"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, ShellSurfaceScreen::class.java).apply {
            putExtra("phone", phone)
            putExtra("start_time", start.time)
            putExtra("end_time", end.time)
            putExtra("call_type", type)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.post_call_notif_title, phone))
            .setContentText(context.getString(R.string.post_call_notif_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).cancelAll()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "PhoneStateReceiver"

        private const val CALLBACK_CONFIRM_MS = 1_500L
        const val ACTION_CALL_ENDED = "com.callerid.number.lookup.home.CALL_ENDED"

        private var lastTime = 0L
        private var callStartTime = 0L
        private var lastNumber: String? = null
        private var wasRinging = false
        private var wasOffhook = false
        private var wasBlocked = false
    }
}
