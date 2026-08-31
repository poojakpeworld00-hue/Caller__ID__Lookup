package com.callerid.number.lookup.home.screen

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.number.lookup.home.databinding.ScreenMainBinding
import com.callerid.number.lookup.home.shell.screens.HomeBoardActivity as LauncherHomeActivity
import com.callerid.number.lookup.home.screen.main.HomeShellDriver
import com.callerid.number.lookup.home.screen.main.HomeShellFragment
import com.callerid.number.lookup.home.screen.main.HomeShellOwner

class AppHomeActivity : FrameActivity<ScreenMainBinding>(), HomeShellOwner {

    override val layoutId: Int = R.layout.screen_main

    override val hostActivity: AppCompatActivity get() = this

    override val homeShellController = HomeShellDriver(this)

    private val shell: HomeShellFragment?
        get() = supportFragmentManager.findFragmentById(R.id.shellContainerVw) as? HomeShellFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        homeShellController.onHostCreated()
    }

    override fun initView() {

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }

        if (shell == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.shellContainerVw,
                    HomeShellFragment.newInstance(consumeLookupNumber(intent))
                )
                .commitNow()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (shell?.onBackPressed() != true) onShellBackExhausted()
        }

        shell?.setPanelVisible(true)
        homeShellController.startFirstRunPriming()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLookupNumber(intent)?.let { shell?.requestLookup(it) }
    }

    private fun consumeLookupNumber(intent: Intent?): String? {
        val number = intent?.getStringExtra(EXTRA_LOOKUP_NUMBER)?.takeIf { it.isNotBlank() }
        intent?.removeExtra(EXTRA_LOOKUP_NUMBER)
        return number
    }

    override fun onResume() {
        super.onResume()
        homeShellController.onHostResume()
    }

    override fun onDestroy() {
        homeShellController.onHostDestroy()
        super.onDestroy()
    }

    override fun onShellBackExhausted() {
        runCatching {
            startActivity(
                Intent(this, LauncherHomeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            )
        }
        finishAffinity()
    }

    /** The shell is the whole screen here — there is nothing else it could cover. */
    override val isShellOnScreen: Boolean get() = true

    /** The shell is always the visible surface here, so its own Snackbar is the right place. */
    override fun showUpdateReadyPrompt() {
        homeShellController.shell?.showUpdateReadyPrompt()
    }

    override fun bringHostToFront() {
        runCatching {
            startActivity(
                Intent(this, AppHomeActivity::class.java)
                    .addFlags(HomeShellDriver.REORDER_FLAGS)
            )
        }
    }

    companion object {

        const val EXTRA_LOOKUP_NUMBER = "extra_lookup_number"
    }
}
