package com.callerid.number.lookup.home.screen.main

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.OpenPromoRegistry
import com.callerid.admesh.surface.StoreUpdateListener
import com.callerid.admesh.surface.StoreUpdateRegistry
import com.callerid.number.lookup.home.permit.PermitSheetDialog
import com.callerid.number.lookup.home.permit.fullscreen.FsiPermit
import com.callerid.number.lookup.home.permit.fullscreen.FsiSettings
import com.callerid.number.lookup.home.permit.fullscreen.FsiPrimerDialog
import com.callerid.number.lookup.home.permit.fullscreen.FsiReturnGuard
import com.callerid.number.lookup.home.runtime.ContactSync
import com.callerid.number.lookup.home.screen.consent.OverlayKit

class HomeShellDriver(private val host: HomeShellOwner) {

    private val activity get() = host.hostActivity

    var shell: HomeShellFragment? = null

    private val fsiReturnWatcher by lazy { FsiReturnGuard(activity) }

    private var awaitFsiReturnForSheet = false

    private var permissionSheetDismissed = false

    private var primingStarted = false

    /**
     * The sheet came due while the shell was off screen (the launcher's caller panel was
     * shut — a HOME press during the Settings round trip, say). Held here rather than shown
     * over the home grid, and flushed the next time the shell is on screen.
     */
    private var permissionSheetPending = false

    /**
     * The shell is on its way off screen (the launcher's caller panel is closing).
     *
     * [HomeShellOwner.isShellOnScreen] cannot answer this: the launcher reads the panel's x,
     * and the panel has not started sliding yet at the moment it is asked to close — so
     * anything that reacts to the tear-down would still be told the shell is visible.
     */
    private var shellOffScreen = false

    /**
     * We took the sheet down ourselves, so its completion callback is not the user dismissing
     * it — it must not arm Home's "Manage" hint, which exists to mean "you closed this once
     * and something is still missing".
     */
    private var sheetTakenDownByUs = false

    /**
     * Whether [onHostCreated] ran. The launcher skips it while its first run is unfinished,
     * and its `onResume` is not gated the same way — without this the resume-side update
     * re-check would start a Play flow over the onboarding screens, on a host that never
     * registered a result launcher for it.
     */
    private var hostCreated = false

    private val handler = Handler(Looper.getMainLooper())

    private val overlayLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        stopOverlayGrantPoll()
        shell?.updateOverlayBanner()
    }

    private val fsiSettingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {

        stopFsiGrantPoll()
        if (FsiPermit.isGranted(activity)) FsiPrimerDialog.dismissIfShowing()

        if (awaitFsiReturnForSheet) {
            awaitFsiReturnForSheet = false
            maybeAutoShowPermissionSheet()
        }
    }

    fun onHostCreated() {
        hostCreated = true
        StoreUpdateRegistry.registerLauncher(activity)
        maybeCheckForUpdate()

        ContactSync.uploadOnceIfNeeded(activity)

        fsiReturnWatcher.register()
    }

    fun startFirstRunPriming() {
        
        shellOffScreen = false
        if (primingStarted) {
            
            if (permissionSheetPending) maybeAutoShowPermissionSheet()
            return
        }
        primingStarted = true

        val fsiCfg = FsiSettings.load(activity)
        if (FsiPermit.shouldShowDialog(activity, fsiCfg)) {
            scheduleFsiDialog(fsiCfg)
        } else {
            maybeAutoShowPermissionSheet()
        }
    }

    fun onHostResume() {
        shell?.updateOverlayBanner()

        FsiPermit.stopWatch(activity)
        if (FsiPermit.isGranted(activity)) FsiPrimerDialog.dismissIfShowing()

        if (awaitFsiReturnForSheet && FsiPermit.isGranted(activity)) {
            awaitFsiReturnForSheet = false
            maybeAutoShowPermissionSheet()
        }

        StoreUpdateRegistry.resumeUpdate()
        
        maybeCheckForUpdate()
    }

    fun onHostDestroy() {
        hostCreated = false
        stopOverlayGrantPoll()
        stopFsiGrantPoll()
        handler.removeCallbacksAndMessages(null)
        fsiReturnWatcher.unregister()
        FsiPermit.stopWatch(activity)
        shell?.dismissForceUpdateDialog()

        StoreUpdateRegistry.destroy(activity)
    }

    private fun maybeCheckForUpdate() {
        if (!hostCreated) return
        val pref = PromoVault.getInstance(activity)
        if (!pref.getBoolean("In_App_Update_Show")) return

        val isForceUpdate = pref.getBoolean("In_App_Update_Force_Show")

        
        if (!isForceUpdate) {
            val age = System.currentTimeMillis() - pref.getLong(LAST_UPDATE_CHECK_KEY, 0L)
            if (age in 0 until UPDATE_CHECK_WINDOW_MS) return
            pref.putLong(LAST_UPDATE_CHECK_KEY, System.currentTimeMillis())
        }

        StoreUpdateRegistry.init(
            activity = activity,
            isForceUpdate = isForceUpdate,
            callback = object : StoreUpdateListener {
                override fun onUpdateSuccess() {}
                override fun onUpdateCanceled() {}
                override fun onUpdateFailed() {
                    shell?.showForceUpdateRequiredDialog()
                }

                override fun onUpdateDownloaded() {
                    
                    host.showUpdateReadyPrompt()
                }
            }
        )
    }

    fun showPermissionSheet() {
        PermitSheetDialog.show(activity) {
            shell?.updateOverlayBanner()
            
            if (sheetTakenDownByUs) sheetTakenDownByUs = false else permissionSheetDismissed = true
            shell?.refreshHomePermissionHint()
        }
    }

    /**
     * The shell is going off screen — on the launcher, the caller panel is closing.
     *
     * Anything this controller has put on screen is anchored to the Activity rather than to
     * the panel, so it survives the slide and is left sitting over the launcher's home grid:
     * a sheet about the caller-ID app's permissions in front of the app drawer and the clock.
     * That is what a HOME press, a back press, or the shell running out of tab history all
     * looked like. Both surfaces come down with the panel, and the sheet is re-armed so the
     * next open still asks.
     */
    fun onShellHidden() {
        shellOffScreen = true
        
        FsiPrimerDialog.dismissIfShowing()
        if (!PermitSheetDialog.isShowing(activity)) return
        sheetTakenDownByUs = true
        PermitSheetDialog.dismissIfShowing(activity)
        permissionSheetPending = true
    }

    private fun maybeAutoShowPermissionSheet() {
        
        if (shellOffScreen || !host.isShellOnScreen) {
            permissionSheetPending = true
            return
        }
        permissionSheetPending = false
        if (PermitSheetDialog.shouldAutoShow(activity)) showPermissionSheet()
    }

    fun shouldShowPermissionHint(): Boolean =
        permissionSheetDismissed && PermitSheetDialog.hasPending(activity)

    private fun scheduleFsiDialog(cfg: FsiSettings) {
        handler.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            if (!FsiPermit.shouldShowDialog(activity, cfg)) {
                maybeAutoShowPermissionSheet()
                return@postDelayed
            }
            FsiPermit.markDialogShown(activity)
            FsiPrimerDialog.show(activity, cfg) { enabled ->
                if (enabled) {

                    awaitFsiReturnForSheet = true
                } else {
                    maybeAutoShowPermissionSheet()
                }
            }
        }, cfg.dialog.delayMs)
    }

    fun openFsiSettings() {
        FsiPermit.openSettings(activity, fsiSettingsLauncher)

        startFsiGrantPoll()
    }

    private val fsiGrantPollHandler = Handler(Looper.getMainLooper())
    private var fsiGrantPolling = false
    private val fsiGrantPoll = object : Runnable {
        override fun run() {
            if (activity.isDestroyed) { fsiGrantPolling = false; return }
            if (FsiPermit.isGranted(activity)) {
                fsiGrantPolling = false
                onFsiGranted()
            } else {
                fsiGrantPollHandler.postDelayed(this, GRANT_POLL_MS)
            }
        }
    }

    private fun startFsiGrantPoll() {
        if (fsiGrantPolling) return
        fsiGrantPolling = true
        fsiGrantPollHandler.removeCallbacks(fsiGrantPoll)
        fsiGrantPollHandler.postDelayed(fsiGrantPoll, GRANT_POLL_MS)
    }

    private fun stopFsiGrantPoll() {
        fsiGrantPolling = false
        fsiGrantPollHandler.removeCallbacks(fsiGrantPoll)
    }

    private fun onFsiGranted() {
        FsiPrimerDialog.dismissIfShowing()
        host.bringHostToFront()
    }

    fun startOverlayPermissionFlow() {
        
        if (!OverlayKit.isOfferable(activity) || OverlayKit.isGranted(activity)) {
            shell?.updateOverlayBanner()
            return
        }

        OpenPromoRegistry.skipNextAppOpenAd = true

        val launched = runCatching {
            overlayLauncher.launch(OverlayKit.buildOverlayIntent(activity.packageName))
        }.isSuccess
        if (!launched) return

        OverlayKit.showGuide(activity)

        startOverlayGrantPoll()
    }

    private val overlayGrantPollHandler = Handler(Looper.getMainLooper())
    private var overlayGrantPolling = false
    private val overlayGrantPoll = object : Runnable {
        override fun run() {
            if (activity.isDestroyed) { overlayGrantPolling = false; return }
            if (OverlayKit.isGranted(activity)) {
                overlayGrantPolling = false
                onOverlayGranted()
            } else {
                overlayGrantPollHandler.postDelayed(this, GRANT_POLL_MS)
            }
        }
    }

    private fun startOverlayGrantPoll() {
        if (overlayGrantPolling) return
        overlayGrantPolling = true
        overlayGrantPollHandler.removeCallbacks(overlayGrantPoll)
        overlayGrantPollHandler.postDelayed(overlayGrantPoll, GRANT_POLL_MS)
    }

    private fun stopOverlayGrantPoll() {
        overlayGrantPolling = false
        overlayGrantPollHandler.removeCallbacks(overlayGrantPoll)
    }

    private fun onOverlayGranted() {
        shell?.updateOverlayBanner()
        host.bringHostToFront()
    }

    companion object {

        private const val GRANT_POLL_MS = 350L

        /** Pref holding when the Play update check last ran. */
        private const val LAST_UPDATE_CHECK_KEY = "last_inapp_update_check_at"

        /** How long a Play update check stays fresh before a resume may run another. */
        private const val UPDATE_CHECK_WINDOW_MS = 6 * 60 * 60 * 1000L

        const val REORDER_FLAGS =
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP

        /**
         * Marks a [HomeShellOwner.bringHostToFront] intent as the app pulling itself back,
         * so a host whose `onNewIntent` otherwise means "HOME was pressed" can tell the two
         * apart. Only the launcher's singleTask home screen has that ambiguity.
         */
        const val EXTRA_SELF_REORDER = "extra_self_reorder"
    }
}
