package com.callerid.number.lookup.home.screen.ringing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.runtime.CallEndGuard
import com.callerid.number.lookup.home.runtime.IdentOverlayCard
import com.callerid.admesh.surface.OsDialogKit
import com.callerid.number.lookup.home.runtime.incoming.PhoneStateReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RingScreenActivity : AppCompatActivity() {

    private val number by lazy { intent.getStringExtra(EXTRA_NUMBER).orEmpty() }

    private val callEndWatcher by lazy { CallEndGuard(this) { finish() } }

    private val systemDialogHelper by lazy {
        OsDialogKit(this) {
            if (!isFinishing && !isDestroyed) finish()
        }
    }

    private val endReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PhoneStateReceiver.ACTION_CALL_ENDED) finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockscreen()
        setContentView(R.layout.screen_incoming_call)

        if (number.isBlank()) {
            finish(); return
        }

        val card = findViewById<View>(R.id.incallCardVw)
        card.findViewById<View>(R.id.padIncallClose).setOnClickListener { finish() }

        lifecycleScope.launch {
            
            IdentOverlayCard.bindResolving(this@RingScreenActivity, card, number)
        }

        val filter = IntentFilter(PhoneStateReceiver.ACTION_CALL_ENDED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(endReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(endReceiver, filter)
        }
        callEndWatcher.start()
        lifecycle.addObserver(systemDialogHelper)
    }

    @Suppress("DEPRECATION")
    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        isActive = true
        NotificationManagerCompat.from(this).cancelAll()
    }

    override fun onPause() {
        super.onPause()
        isActive = false
    }

    override fun onDestroy() {
        isActive = false
        callEndWatcher.stop()
        runCatching { unregisterReceiver(endReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_NUMBER = "extra_number"

        @Volatile
        var isActive = false

        fun newIntent(context: Context, number: String): Intent =
            Intent(context, RingScreenActivity::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
