package com.callerid.number.lookup.home.launcher

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.callerid.admesh.surface.StoreUpdateRegistry
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.screen.main.HomeShellDriver
import com.callerid.number.lookup.home.screen.main.HomeShellOwner
import com.google.android.material.snackbar.Snackbar
import io.launcher.home.activities.LauncherPanel
import java.util.WeakHashMap

/**
 * [HomeShellOwner] for the launcher module's home screen.
 *
 * [LauncherPanel] is the module's Activity, so it cannot implement our interface the way the old
 * in-app launcher did. This object stands in for it, one per LauncherPanel instance, and the shell
 * fragments find it through [of].
 *
 * It must be created from [CallerLauncherBridge.onLauncherStart]: that runs inside the launcher's
 * `onCreate`, and [HomeShellDriver] registers its result launchers in field initializers, which
 * only works before the Activity is STARTED.
 */
class LauncherShellHost private constructor(private val panel: LauncherPanel) : HomeShellOwner {

    override val hostActivity: AppCompatActivity get() = panel

    override val homeShellController = HomeShellDriver(this)

    /** Set by [LauncherShellFragment] as the right-hand panel slides in and out. */
    var shellVisible = false

    private var updateReadySnackbar: Snackbar? = null

    override fun onShellBackExhausted() = panel.closeHostPanel()

    override val isShellOnScreen: Boolean get() = shellVisible

    /**
     * With the panel open the shell's own Snackbar is right; with it shut the shell is parked off
     * screen, so the home grid carries the prompt or a downloaded update has nowhere to install from.
     */
    override fun showUpdateReadyPrompt() {
        if (shellVisible) {
            homeShellController.shell?.showUpdateReadyPrompt()
            return
        }
        if (updateReadySnackbar?.isShown == true) return
        updateReadySnackbar = Snackbar
            .make(panel.findViewById(android.R.id.content), R.string.update_ready_msg, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.update_restart) { StoreUpdateRegistry.completeUpdate() }
            .also { it.show() }
    }

    /** Back from a Settings round trip (overlay / FSI grant): land in the panel, not on the grid. */
    override fun bringHostToFront() {
        runCatching {
            panel.startActivity(
                Intent(panel, LauncherPanel::class.java)
                    .addFlags(HomeShellDriver.REORDER_FLAGS)
                    .putExtra(LauncherPanel.EXTRA_OPEN_HOST_PANEL, true)
            )
        }
    }

    companion object {

        private val hosts = WeakHashMap<Activity, LauncherShellHost>()

        fun attach(panel: LauncherPanel) {
            if (hosts.containsKey(panel)) return
            val host = LauncherShellHost(panel)
            hosts[panel] = host
            host.homeShellController.onHostCreated()
            panel.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    hosts.remove(panel)?.homeShellController?.onHostDestroy()
                }
            })
        }

        fun of(activity: Activity?): LauncherShellHost? = activity?.let { hosts[it] }
    }
}
