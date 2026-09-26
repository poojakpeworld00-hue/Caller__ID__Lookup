package com.callerid.number.lookup.home.screen.main

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.callerid.number.lookup.home.launcher.LauncherShellHost

interface HomeShellOwner {

    val hostActivity: AppCompatActivity

    val homeShellController: HomeShellDriver

    fun onShellBackExhausted()

    /** True while the shell's content is actually visible to the user. */
    val isShellOnScreen: Boolean

    /**
     * Offer the "a new version is downloaded — restart to install" affordance.
     *
     * Owned by the host rather than by [HomeShellFragment] because the launcher commits that
     * fragment at `onCreate` and parks it off screen: a Snackbar anchored inside it while the
     * caller panel is shut is drawn on a view the user cannot see, so the update sits pending
     * with nothing on screen to act on. Each host puts it where its user is actually looking.
     */
    fun showUpdateReadyPrompt()

    fun bringHostToFront()
}

/** The standalone [com.callerid.number.lookup.home.screen.AppHomeActivity], or the launcher's stand-in. */
val Activity.homeShellOwner: HomeShellOwner?
    get() = this as? HomeShellOwner ?: LauncherShellHost.of(this)

val Fragment.homeShellHost: HomeShellOwner? get() = activity?.homeShellOwner

val Fragment.homeShellController: HomeShellDriver? get() = homeShellHost?.homeShellController

val Fragment.homeShell: HomeShellFragment? get() = parentFragment as? HomeShellFragment
