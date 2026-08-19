package com.callerid.number.lookup.home.ui.home

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

/**
 * Implemented by whichever Activity is hosting [HomeShellFragment].
 *
 * There are two: [com.callerid.number.lookup.home.ui.AppHomeActivity], where the shell *is* the
 * screen, and the launcher's home screen, where the same shell rides in the swipe-right side
 * panel. Everything that differs between those two lives behind this interface, so the shell
 * itself never asks which one it is in.
 */
interface HomeShellOwner {

    /** The hosting Activity — result launchers, dialogs and system round-trips need it. */
    val hostActivity: AppCompatActivity

    /**
     * The Activity-bound half of the shell.
     *
     * Must be a **field initializer** in the implementing Activity, not `by lazy`:
     * constructing it registers `ActivityResultLauncher`s, which has to happen before the
     * Activity is STARTED. A panel-hosted fragment is committed later than that, which is
     * the whole reason this controller exists separately from the fragment.
     */
    val homeShellController: HomeShellDriver

    /**
     * Back was pressed on Home with the visited-tab history already empty.
     *
     * AppHomeActivity leaves for the launcher home screen; the launcher panel just closes.
     */
    fun onShellBackExhausted()

    /**
     * Pull the host Activity back to the front of its task.
     *
     * Called when a grant is detected while the user is sitting on a system Settings page,
     * so the (NO_HISTORY) Settings page drops away without them pressing Back.
     */
    fun bringHostToFront()
}

/** The shell host, for any fragment or dialog attached inside the home shell. */
val Fragment.homeShellHost: HomeShellOwner? get() = activity as? HomeShellOwner

/** The Activity-bound shell controller, or null when not hosted by a shell. */
val Fragment.homeShellController: HomeShellDriver? get() = homeShellHost?.homeShellController

/**
 * The shell fragment a tab is running inside.
 *
 * Tabs are committed to the shell's *child* fragment manager, so the shell is their
 * [Fragment.parentFragment] — this is how a tab switches to another tab.
 */
val Fragment.homeShell: HomeShellFragment? get() = parentFragment as? HomeShellFragment
