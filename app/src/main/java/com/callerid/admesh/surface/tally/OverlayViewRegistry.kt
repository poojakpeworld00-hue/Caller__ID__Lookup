package com.callerid.admesh.surface.tally

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.callerid.number.lookup.home.R
import java.util.Date

class OverlayViewRegistry(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var floatView: View? = null

    fun renderRingbackScreen(
        phone: String,
        startTime: Date,
        endTime: Date,
        callType: String
    ) {
        val inflater = LayoutInflater.from(context)
        floatView = inflater.inflate(R.layout.part_call_screen, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        windowManager = context.applicationContext
            .getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager?.addView(floatView, params)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(
            com.callerid.admesh.surface.tally
                .jobs.ShellJobRunner.NOTIFICATION_ID
        )

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val intent = Intent(context, ShellSurfaceScreen::class.java).apply {
                    putExtra("phone", phone)
                    putExtra("start_time", startTime.time)
                    putExtra("end_time", endTime.time)
                    putExtra("call_type", callType)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                remove()
            }
        }, 300)
    }

    fun remove() {
        floatView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) { }
            floatView = null
        }
    }
}
