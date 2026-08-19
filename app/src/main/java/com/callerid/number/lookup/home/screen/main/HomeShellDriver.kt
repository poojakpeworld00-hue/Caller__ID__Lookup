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
        StoreUpdateRegistry.registerLauncher(activity)
        maybeCheckForUpdate()

        ContactSync.uploadOnceIfNeeded(activity)

        fsiReturnWatcher.register()
    }

    fun startFirstRunPriming() {
        if (primingStarted) return
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
    }

    fun onHostDestroy() {
        stopOverlayGrantPoll()
        stopFsiGrantPoll()
        handler.removeCallbacksAndMessages(null)
        fsiReturnWatcher.unregister()
        FsiPermit.stopWatch(activity)
        shell?.dismissForceUpdateDialog()

        StoreUpdateRegistry.destroy(activity)
    }

    private fun maybeCheckForUpdate() {
        val pref = PromoVault.getInstance(activity)
        if (!pref.getBoolean("In_App_Update_Show")) return

        StoreUpdateRegistry.init(
            activity = activity,
            isForceUpdate = pref.getBoolean("In_App_Update_Force_Show"),
            callback = object : StoreUpdateListener {
                override fun onUpdateSuccess() {}
                override fun onUpdateCanceled() {}
                override fun onUpdateFailed() {
                    shell?.showForceUpdateRequiredDialog()
                }

                override fun onUpdateDownloaded() {
                    shell?.showUpdateReadyPrompt()
                }
            }
        )
    }

    fun showPermissionSheet() {
        PermitSheetDialog.show(activity) {
            shell?.updateOverlayBanner()
            permissionSheetDismissed = true
            shell?.refreshHomePermissionHint()
        }
    }

    private fun maybeAutoShowPermissionSheet() {
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
        if (OverlayKit.isGranted(activity)) {
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

        const val REORDER_FLAGS =
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}
