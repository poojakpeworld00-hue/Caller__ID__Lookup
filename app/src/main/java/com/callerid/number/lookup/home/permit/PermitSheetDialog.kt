package com.callerid.number.lookup.home.permit

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.callerid.number.lookup.home.store.StorageRegistry
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.callerid.admesh.engine.trackEvent
import com.callerid.admesh.engine.logPermissionResult
import com.callerid.admesh.surface.OpenPromoRegistry
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.screen.main.HomeShellOwner
import com.callerid.number.lookup.home.screen.reveal.RevealConfig
import com.callerid.number.lookup.home.screen.reveal.RevealPolicy
import com.callerid.number.lookup.home.screen.consent.OverlayKit
import com.callerid.number.lookup.home.kit.LogRail

class PermitSheetDialog : BottomSheetDialogFragment() {

    var onFinished: (() -> Unit)? = null

    private data class Row(
        val key: String,
        @StringRes val title: Int,
        @StringRes val desc: Int,
        @DrawableRes val icon: Int,
        val isOverlay: Boolean = false,
        val androidPermission: String? = null,

        val engineManaged: Boolean = false,
    )

    private lateinit var rows: List<Row>

    private var continueInProgress = false

    private var finishAfterOverlay = false

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result.forEach { (perm, granted) ->
            context?.logPermissionResult(perm, granted)
        }
        refreshRows()
        if (continueInProgress) {
            continueInProgress = false
            proceedToOverlayOrFinish()
        }
    }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = context?.let { ctx -> OverlayKit.isGranted(ctx) } ?: false
        context?.trackEvent(if (granted) "Permission_OVERLAY_Allow" else "Permission_OVERLAY_Deny")
        refreshRows()
        if (finishAfterOverlay) {
            finishAfterOverlay = false
            finishFlow()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val root = inflater.inflate(R.layout.dlg_permission_sheet, container, false)
        rows = buildRows()

        val rowsContainer = root.findViewById<LinearLayout>(R.id.holderRows)
        rows.forEach { row ->
            val rowView = inflater.inflate(R.layout.cell_permission_row, rowsContainer, false)
            rowView.tag = row.key
            rowView.findViewById<ImageView>(R.id.picIcon).setImageResource(row.icon)
            rowView.findViewById<TextView>(R.id.lblTitle).setText(row.title)
            rowView.findViewById<TextView>(R.id.lblDesc).setText(row.desc)
            rowView.findViewById<TextView>(R.id.padAllow).setOnClickListener { requestSingle(row) }

            rowView.visibility = if (shouldHideRow(row)) View.GONE else View.VISIBLE
            rowsContainer.addView(rowView)
        }

        root.findViewById<TextView>(R.id.padContinue).setOnClickListener { onContinueClicked() }
        root.findViewById<TextView>(R.id.padNotNow).setOnClickListener {
            context?.trackEvent("permission_sheet_not_now")
            finishFlow()
        }

        context?.trackEvent("permission_sheet_show")
        return root
    }

    override fun onStart() {
        super.onStart()

        (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)

        view?.findViewById<View>(R.id.rowsScrollVw)?.let { scroll ->
            scroll.post {

                if (!isAdded) return@post
                val maxH = (scroll.resources.displayMetrics.heightPixels * 0.5f).toInt()
                if (scroll.height > maxH) {
                    scroll.layoutParams = scroll.layoutParams.apply { height = maxH }
                    scroll.requestLayout()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRows()
    }

    private fun buildRows(): List<Row> {
        val list = mutableListOf<Row>()

        val ctx = requireContext()

        
        if (PermitKit.isOfferable(ctx, "notification")) {
            list += Row(
                "notification", R.string.perm_notification_title, R.string.perm_notification_desc,
                R.drawable.sym_notifications, androidPermission = Manifest.permission.POST_NOTIFICATIONS,
                engineManaged = true,
            )
        }

        
        if (PermitKit.isOfferable(ctx, "phone_state")) {
            list += Row(
                "phone_state", R.string.perm_phone_title, R.string.perm_phone_desc,
                R.drawable.sym_phone_solid,
                androidPermission = Manifest.permission.READ_PHONE_STATE,
                engineManaged = true,
            )
        }
        list += Row(
            "call_log", R.string.permsheet_calllog_title, R.string.perm_calllog_desc,
            R.drawable.sym_history, androidPermission = Manifest.permission.READ_CALL_LOG,
        )
        list += Row(
            "contacts", R.string.permsheet_contacts_title, R.string.perm_contacts_desc,
            R.drawable.sym_group, androidPermission = Manifest.permission.READ_CONTACTS,
        )
        
        if (OverlayKit.isOfferable(ctx)) {
            list += Row(
                "overlay", R.string.perm_overlay_title, R.string.perm_overlay_desc,
                R.drawable.sym_apps, isOverlay = true,
            )
        }
        return list
    }

    private fun isGranted(row: Row): Boolean {
        val ctx = context ?: return false
        return if (row.isOverlay) {
            OverlayKit.isGranted(ctx)
        } else {
            val perm = row.androidPermission ?: return true
            ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun refreshRows() {
        val root = view ?: return
        val rowsContainer = root.findViewById<LinearLayout>(R.id.holderRows)
        rows.forEach { row ->
            val rowView = rowsContainer.findViewWithTag<View>(row.key) ?: return@forEach

            rowView.visibility = if (shouldHideRow(row)) View.GONE else View.VISIBLE
        }

        if (!continueInProgress && !finishAfterOverlay && rows.all { shouldHideRow(it) }) {
            finishFlow()
        }
    }

    private fun shouldHideRow(row: Row): Boolean {
        if (isClosedByEngine(row)) return true
        if (isGranted(row)) return true
        if (!row.engineManaged) return false
        val act = activity ?: return false
        val perm = row.androidPermission ?: return false
        return isPermanentlyDenied(act, row.key, perm)
    }

    /**
     * The engine's own gates, re-read on every refresh. `HD_VBC_Show` in particular flips to false
     * only after the IP/country check in `funOnAdsLoad` lands, which is well after the sheet is
     * built — a row filtered at build time alone would stay on screen doing nothing when tapped,
     * because [PermitEngine] skips gated keys.
     */
    private fun isClosedByEngine(row: Row): Boolean {
        val ctx = context ?: return false
        
        if (row.isOverlay) return !OverlayKit.isOfferable(ctx)
        if (PermitKit.spec(row.key) == null) return false
        return !PermitKit.isOfferable(ctx, row.key)
    }

    private fun requestSingle(row: Row) {
        if (isGranted(row)) return
        when {

            
            row.engineManaged -> PermitEngine.request(requireActivity(), row.key) {
                if (isAdded) refreshRows()
            }
            row.isOverlay -> launchOverlay(finishAfter = false)
            else -> row.androidPermission?.let { requestPerms.launch(arrayOf(it)) }
        }
    }

    private fun onContinueClicked() {
        
        requestEngineRows(rows.filter { it.engineManaged && !isGranted(it) }) {
            if (isAdded) requestSheetOwnedThenOverlay()
        }
    }

    /** Walks [queue] through [PermitEngine.request] sequentially, then runs [onDone]. */
    private fun requestEngineRows(queue: List<Row>, onDone: () -> Unit) {
        val head = queue.firstOrNull() ?: run { onDone(); return }
        val act = activity ?: run { onDone(); return }
        PermitEngine.request(act, head.key) {
            if (!isAdded) return@request
            refreshRows()
            requestEngineRows(queue.drop(1), onDone)
        }
    }

    private fun requestSheetOwnedThenOverlay() {
        val missing = rows
            .filter { !it.isOverlay && !it.engineManaged && !isGranted(it) }
            .mapNotNull { it.androidPermission }
        if (missing.isNotEmpty()) {
            continueInProgress = true
            requestPerms.launch(missing.toTypedArray())
        } else {
            proceedToOverlayOrFinish()
        }
    }

    private fun proceedToOverlayOrFinish() {
        val overlayRow = rows.firstOrNull { it.isOverlay && !isClosedByEngine(it) }
        if (overlayRow != null && !isGranted(overlayRow)) {
            launchOverlay(finishAfter = true)
        } else {
            finishFlow()
        }
    }

    private fun launchOverlay(finishAfter: Boolean) {

        val controller = (activity as? HomeShellOwner)?.homeShellController
        if (controller != null) {
            controller.startOverlayPermissionFlow()

            if (finishAfter) finishFlow()
            return
        }

        finishAfterOverlay = finishAfter
        OpenPromoRegistry.skipNextAppOpenAd = true
        runCatching {
            overlayLauncher.launch(OverlayKit.buildOverlayIntent(requireContext().packageName))
        }.onSuccess {

            OverlayKit.showGuide(requireActivity())
        }.onFailure {
            LogRail.error("PermissionSheet", "Failed to open overlay settings", it)
            if (finishAfter) finishFlow()
        }
    }

    private fun finishFlow() {
        onFinished?.invoke()
        onFinished = null
        runCatching { dismissAllowingStateLoss() }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)

        onFinished?.invoke()
        onFinished = null
    }

    companion object {
        const val TAG = "permission_sheet"

        /** True while the sheet is on screen. */
        @JvmStatic
        fun isShowing(activity: FragmentActivity): Boolean =
            activity.supportFragmentManager.findFragmentByTag(TAG) != null

        /**
         * Takes the sheet down. A no-op when it is not showing.
         *
         * The sheet is committed to the **Activity's** fragment manager, not to whatever view
         * it was raised over, so on the launcher it outlives the caller panel that triggered
         * it: close the panel and the sheet stays, asking about the caller-ID app's
         * permissions in front of the launcher's app grid. Callers pair this with re-arming
         * the sheet so the user is still asked next time the panel opens.
         */
        @JvmStatic
        fun dismissIfShowing(activity: FragmentActivity) {
            val fm = activity.supportFragmentManager
            (fm.findFragmentByTag(TAG) as? PermitSheetDialog)
                ?.let { runCatching { it.dismissAllowingStateLoss() } }
        }

        @JvmStatic
        fun hasPending(activity: FragmentActivity): Boolean {
            fun granted(perm: String) =
                ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED

            
            if (PermitKit.isOfferable(activity, "notification") &&
                !granted(Manifest.permission.POST_NOTIFICATIONS) &&
                !isPermanentlyDenied(activity, "notification", Manifest.permission.POST_NOTIFICATIONS)
            ) return true
            if (PermitKit.isOfferable(activity, "phone_state") &&
                !granted(Manifest.permission.READ_PHONE_STATE) &&
                !isPermanentlyDenied(activity, "phone_state", Manifest.permission.READ_PHONE_STATE)
            ) return true
            if (!granted(Manifest.permission.READ_CALL_LOG)) return true
            if (!granted(Manifest.permission.READ_CONTACTS)) return true
            if (OverlayKit.isOfferable(activity) && !OverlayKit.isGranted(activity)) return true
            return false
        }

        @JvmStatic
        fun isPermanentlyDenied(activity: FragmentActivity, key: String, perm: String): Boolean {
            if (ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED) {
                return false
            }
            val askedAnywhere = PermitVault(activity).wasAsked(key) ||
                StorageRegistry(activity).hasRequestedPermission(perm)
            if (!askedAnywhere) return false
            return !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }

        @JvmStatic
        fun shouldAutoShow(activity: FragmentActivity): Boolean {
            if (!hasPending(activity)) return false
            return RevealPolicy.shouldShowPermissionSheet(activity)
        }

        @JvmStatic
        @JvmOverloads
        fun show(activity: FragmentActivity, onFinished: (() -> Unit)? = null) {
            val fm = activity.supportFragmentManager
            if (fm.isStateSaved || fm.findFragmentByTag(TAG) != null) return

            RevealPolicy.markShown(activity, RevealConfig.PERMISSION_SHEET)
            PermitSheetDialog().apply { this.onFinished = onFinished }
                .show(fm, TAG)
        }
    }
}
