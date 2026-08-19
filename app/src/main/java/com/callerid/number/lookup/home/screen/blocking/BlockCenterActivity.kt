package com.callerid.number.lookup.home.screen.blocking

import android.Manifest
import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.number.lookup.home.store.BlockedEntry
import com.callerid.number.lookup.home.store.CallEntry
import com.callerid.number.lookup.home.store.CallHistorySource
import com.callerid.number.lookup.home.store.CallFlavor
import com.callerid.number.lookup.home.store.ContactSource
import com.callerid.number.lookup.home.databinding.ScreenBlocklistBinding
import com.callerid.number.lookup.home.databinding.DlgBlockAddBinding
import com.callerid.number.lookup.home.databinding.DlgBlockDetailsBinding
import com.callerid.number.lookup.home.databinding.DlgBlockMethodsBinding
import com.callerid.number.lookup.home.databinding.DlgBlockRecentsBinding
import com.callerid.number.lookup.home.databinding.DlgEnableCallerIdBinding
import com.callerid.number.lookup.home.databinding.PartBlockMethodsBinding
import com.callerid.number.lookup.home.kit.InstallIdRegistry
import com.callerid.admesh.surface.OpenPromoRegistry
import java.text.DateFormat
import java.util.Date

class BlockCenterActivity : FrameActivity<ScreenBlocklistBinding>() {

    override val layoutId: Int = R.layout.screen_blocklist

    private val viewModel: BlockListViewModel by viewModels()

    // Row actions are block-management too, so they pass through the Caller-ID gate.
    private val adapter = BlockListAdapter(
        onUnblock = { entry -> requireCallerId { unblock(entry) } },
        onRowClick = { entry -> requireCallerId { showDetails(entry) } }
    )

    /** The single live "Enable Caller ID" dialog, if any (prevents duplicates). */
    private var enableCallerIdDialog: Dialog? = null

    /**
     * The block action the user tried to run while Caller ID was still off (e.g.
     * "Add number"). Held so that the instant the role is granted we resume *that*
     * action — opening its dialog — instead of stranding the user on the list (or
     * letting the role round-trip drop them back on Home). Cleared when it runs,
     * when the user declines, or on destroy.
     */
    private var pendingCallerIdAction: (() -> Unit)? = null

    /** Looping/entrance animators for the enable dialog; cancelled on dismiss. */
    private val enableDialogAnimators = mutableListOf<Animator>()

    /**
     * Re-checks Caller ID after the system role dialog returns and closes the
     * enable-prompt once the role is held. The role request returns its result
     * straight to this launcher, so no separate grant watcher is needed.
     */
    private val callerIdRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshCallerIdGate() }

    /** Asks for contacts access, then opens the in-app contacts picker once granted. */
    private val contactsPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showContactsPicker()
        else Toast.makeText(this, R.string.blocklist_perm_contacts, Toast.LENGTH_SHORT).show()
    }

    /** Asks for call-log access, then opens the recent-calls picker once granted. */
    private val callLogPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showRecentsPicker()
        else Toast.makeText(this, R.string.blocklist_perm_calllog, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.blocklistRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.padBack.setOnClickListener { goBack() }
        binding.rollBlocklist.layoutManager = LinearLayoutManager(this)
        binding.rollBlocklist.adapter = adapter

        // The empty-state card and the FAB chooser share the same three actions.
        bindMethods(binding.emptyMethodsVw)
        binding.fabAddVw.setOnClickListener { requireCallerId { showMethodsDialog() } }
    }

    /**
     * Wires the three "block by…" rows; [onChosen] lets a host dialog dismiss
     * itself. Each action is gated on Caller ID being enabled.
     */
    private fun bindMethods(methods: PartBlockMethodsBinding, onChosen: () -> Unit = {}) {
        methods.rowAddNumberVw.setOnClickListener { requireCallerId { onChosen(); showAddDialog() } }
        methods.rowFromContactsVw.setOnClickListener { requireCallerId { onChosen(); pickFromContacts() } }
        methods.rowFromRecentsVw.setOnClickListener { requireCallerId { onChosen(); pickFromRecents() } }
    }

    private var emptyCascaded = false

    /** Cascades the three add-method rows in (40ms stagger) the first time the empty state shows. */
    private fun cascadeEmptyMethods() {
        if (emptyCascaded) return
        emptyCascaded = true
        val rows = listOf(
            binding.emptyMethodsVw.rowAddNumberVw,
            binding.emptyMethodsVw.rowFromContactsVw,
            binding.emptyMethodsVw.rowFromRecentsVw
        )
        val dy = resources.displayMetrics.density * 10f
        rows.forEachIndexed { i, row ->
            row.alpha = 0f
            row.translationY = dy
            row.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(i * 40L)
                .setDuration(320L)
                .start()
        }
    }

    /** FAB entry point: the same three methods in a dialog when the list isn't empty. */
    private fun showMethodsDialog() {
        val view = DlgBlockMethodsBinding.inflate(layoutInflater)
        val dialog = customDialog(view.root)
        bindMethods(view.dialogMethodsVw) { dialog.dismiss() }
        dialog.show()
    }

    override fun initObservers() {
        viewModel.rows.observe(this) { rows ->
            adapter.submit(rows)
            val empty = rows.isEmpty()
            binding.emptyScrollVw.visibility = if (empty) View.VISIBLE else View.GONE
            binding.populatedGroupVw.visibility = if (empty) View.GONE else View.VISIBLE
            if (empty) cascadeEmptyMethods()
        }
        viewModel.count.observe(this) { count ->
            binding.lblSummaryCount.text =
                resources.getQuantityString(R.plurals.blocklist_blocked_count, count, count)
        }
    }

    /** Custom (non-system) details dialog with an Unblock action. */
    private fun showDetails(entry: BlockedEntry) {
        val view = DlgBlockDetailsBinding.inflate(layoutInflater)
        val dialog = customDialog(view.root)

        view.lblDetailNumber.text = entry.number
        if (entry.addedAt > 0L) {
            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(entry.addedAt))
            view.lblDetailAdded.text = getString(R.string.blocklist_details_added, date)
        } else {
            view.lblDetailAdded.visibility = View.GONE
        }

        view.padClose.setOnClickListener { dialog.dismiss() }
        view.padUnblock.setOnClickListener {
            dialog.dismiss()
            unblock(entry)
        }
        dialog.show()
    }

    private fun unblock(entry: BlockedEntry) {
        viewModel.remove(entry.number)
        Toast.makeText(this, R.string.blocklist_removed, Toast.LENGTH_SHORT).show()
    }


    /** Custom (non-system) add-to-blocklist dialog — manual entry. */
    private fun showAddDialog() {
        val view = DlgBlockAddBinding.inflate(layoutInflater)
        val dialog = customDialog(view.root)

        view.padCancel.setOnClickListener { dialog.dismiss() }
        view.padAdd.setOnClickListener {
            blockNumber(view.inpNumber.text?.toString().orEmpty())
            dialog.dismiss()
        }
        dialog.show()
    }

    // --- From contacts (in-app dialog — never leaves the app) ---

    private fun pickFromContacts() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) showContactsPicker()
        else requestPermissionManaged(Manifest.permission.READ_CONTACTS, contactsPermLauncher)
    }

    private fun showContactsPicker() {
        val contacts = ContactSource(this).getContacts()
            .filter { it.detail.isNotBlank() }
            .map { CallEntry(it.name, it.detail, CallFlavor.OUTGOING, 0L, 0L) }
            .distinctBy { it.number }
            .take(200)

        showPickDialog(R.string.blocklist_pick_contacts_title, R.string.blocklist_no_contacts, contacts)
    }

    // --- Block from recent calls ---

    private fun pickFromRecents() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) showRecentsPicker()
        else requestPermissionManaged(Manifest.permission.READ_CALL_LOG, callLogPermLauncher)
    }

    private fun showRecentsPicker() {
        val recents = CallHistorySource(this).getCalls(limit = 200)
            .filter { it.number.isNotBlank() && !it.number.equals("Unknown", ignoreCase = true) }
            .distinctBy { it.number }
            .take(50)

        showPickDialog(R.string.blocklist_pick_recents_title, R.string.blocklist_no_recents, recents)
    }

    /**
     * Shared in-app picker dialog for both contacts and recents — same list UI,
     * just a different title, empty message and data set. Tapping a row blocks
     * its number. Nothing here hands off to a system app.
     */
    private fun showPickDialog(
        @StringRes titleRes: Int,
        @StringRes emptyRes: Int,
        items: List<CallEntry>
    ) {
        val view = DlgBlockRecentsBinding.inflate(layoutInflater)
        val dialog = customDialog(view.root)

        view.lblTitle.setText(titleRes)
        view.lblNoRecents.setText(emptyRes)

        val pickAdapter = PrefixPickAdapter { entry ->
            dialog.dismiss()
            blockNumber(entry.number)
        }
        view.rollRecents.layoutManager = LinearLayoutManager(this)
        view.rollRecents.adapter = pickAdapter
        pickAdapter.submit(items)

        view.rollRecents.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        view.lblNoRecents.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        view.padClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /** Single funnel for all three methods: de-dupes and reports the outcome. */
    private fun blockNumber(raw: String) {
        val number = raw.trim()
        if (number.isEmpty()) return
        if (viewModel.isBlocked(number)) {
            Toast.makeText(this, R.string.blocklist_already_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.add(number)
        Toast.makeText(this, R.string.blocklist_added, Toast.LENGTH_SHORT).show()
    }

    // --- Caller-ID gate (all block management requires the CallScreening role) ---

    /**
     * Runs [action] only when Caller ID is enabled; otherwise prompts the user to
     * enable it. Single funnel for every block-management entry point so the check
     * is never duplicated.
     */
    private fun requireCallerId(action: () -> Unit) {
        if (InstallIdRegistry.isCallerIdEnabled(this)) {
            action()
        } else {
            // Remember what the user was trying to do; refreshCallerIdGate() resumes
            // it the moment Caller ID turns on.
            pendingCallerIdAction = action
            showEnableCallerIdDialog()
        }
    }

    /**
     * Modern animated bottom-sheet prompt shown when a block action is attempted
     * while Caller ID is off (design: "Enable Caller ID Dialog"). Guarded so rapid
     * taps can't stack copies. "Enable Caller ID" opens the system role flow; the
     * app returns here automatically and refreshes.
     */
    private fun showEnableCallerIdDialog() {
        if (enableCallerIdDialog?.isShowing == true) return

        val view = DlgEnableCallerIdBinding.inflate(layoutInflater)
        val dialog = Dialog(this).apply {
            setContentView(view.root)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.BOTTOM)
                setWindowAnimations(R.style.SheetSlideAnimation)
                setDimAmount(0.55f)
            }
        }

        view.padNotNow.setOnClickListener {
            // User declined — drop the queued action so it can't resurface later.
            pendingCallerIdAction = null
            dialog.dismiss()
        }
        view.padEnable.setOnClickListener {
            dialog.dismiss()
            requestEnableCallerId()
        }
        dialog.setOnDismissListener {
            cancelEnableDialogAnimators()
            if (enableCallerIdDialog === dialog) enableCallerIdDialog = null
        }
        enableCallerIdDialog = dialog
        dialog.show()
        animateEnableCallerIdDialog(view)
    }

    /**
     * Drives the dialog's motion once it's shown: the shield pops in with an
     * overshoot, a ring pulses out, the checkmark draws itself (AVD), the hint
     * strip reveals, the CTA breathes, and the demo toggle loops off→on to preview
     * the system step. All looping animators are tracked for cancellation.
     */
    private fun animateEnableCallerIdDialog(v: DlgEnableCallerIdBinding) {
        // Shield tile: scale-pop with overshoot.
        v.shieldTileVw.alpha = 0f
        v.shieldTileVw.scaleX = 0.4f
        v.shieldTileVw.scaleY = 0.4f
        v.shieldTileVw.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(80L).setDuration(440L)
            .setInterpolator(OvershootInterpolator(2.4f))
            .start()

        // Checkmark draws itself in via the animated vector.
        AnimatedVectorDrawableCompat.create(this, R.drawable.avd_guard_shield)?.let { avd ->
            v.shieldIconVw.setImageDrawable(avd)
            avd.start()
        }

        // Ring: pulse outward, forever.
        v.shieldRingVw.alpha = 0f
        loopAnimator(v.shieldRingVw, View.SCALE_X, 0.7f, 1.5f, 1500L, 220L, DecelerateInterpolator())
        loopAnimator(v.shieldRingVw, View.SCALE_Y, 0.7f, 1.5f, 1500L, 220L, DecelerateInterpolator())
        loopAnimator(v.shieldRingVw, View.ALPHA, 0.7f, 0f, 1500L, 220L, DecelerateInterpolator())

        // Hint strip: fade + rise in after the shield.
        v.tipStrip.alpha = 0f
        v.tipStrip.translationY = 10f * resources.displayMetrics.density
        v.tipStrip.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(620L).setDuration(340L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // CTA: gentle breathing to pull the tap.
        loopAnimator(
            v.padEnable, View.SCALE_X, 1f, 1.03f, 1300L, 900L,
            AccelerateDecelerateInterpolator(), ValueAnimator.REVERSE
        )
        loopAnimator(
            v.padEnable, View.SCALE_Y, 1f, 1.03f, 1300L, 900L,
            AccelerateDecelerateInterpolator(), ValueAnimator.REVERSE
        )

        // Demo toggle: loop off→on once the views are measured (needs the thumb travel).
        v.flipTrack.post { startToggleDemo(v) }
    }

    /** Starts one infinite [ObjectAnimator], registering it for later cancellation. */
    private fun loopAnimator(
        target: View,
        property: android.util.Property<View, Float>,
        from: Float,
        to: Float,
        duration: Long,
        startDelay: Long,
        interpolator: android.view.animation.Interpolator,
        repeatMode: Int = ValueAnimator.RESTART
    ) {
        ObjectAnimator.ofFloat(target, property, from, to).apply {
            this.duration = duration
            this.startDelay = startDelay
            this.interpolator = interpolator
            this.repeatCount = ValueAnimator.INFINITE
            this.repeatMode = repeatMode
            enableDialogAnimators.add(this)
            start()
        }
    }

    /**
     * Loops the illustrative toggle in the hint strip: track fades grey→green, the
     * thumb slides across with a tap ripple, holds, then resets — teaching the exact
     * system step the user is about to see.
     */
    private fun startToggleDemo(v: DlgEnableCallerIdBinding) {
        val marginStart = (v.flipThumb.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart ?: 0
        val travel = (v.flipTrack.width - v.flipThumb.width - 2 * marginStart).toFloat()
        if (travel <= 0f) return

        val offColor = ContextCompat.getColor(this, R.color.cid_toggle_off)
        val onColor = ContextCompat.getColor(this, R.color.online_green)
        val argb = ArgbEvaluator()

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = null // linear; phase timing is handled below
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float

                // Fraction "on" (track color + thumb position share this timeline).
                val on = when {
                    t < 0.30f -> 0f
                    t < 0.40f -> ease((t - 0.30f) / 0.10f)
                    t < 0.86f -> 1f
                    t < 0.96f -> 1f - ease((t - 0.86f) / 0.10f)
                    else -> 0f
                }
                v.flipTrack.backgroundTintList =
                    ColorStateList.valueOf(argb.evaluate(on, offColor, onColor) as Int)
                v.flipThumb.translationX = on * travel

                // Ripple bursts at the flip.
                val r = when {
                    t < 0.30f -> -1f
                    t < 0.52f -> (t - 0.30f) / 0.22f
                    else -> -1f
                }
                if (r in 0f..1f) {
                    v.flipRipple.alpha = (1f - r) * 0.8f
                    val s = 0.4f + r * 1.7f
                    v.flipRipple.scaleX = s
                    v.flipRipple.scaleY = s
                } else {
                    v.flipRipple.alpha = 0f
                }
            }
            enableDialogAnimators.add(this)
            start()
        }
    }

    /** Decelerating ease for the toggle demo (1-(1-x)^2). */
    private fun ease(x: Float): Float = 1f - (1f - x) * (1f - x)

    /** Cancels & clears every tracked enable-dialog animator (idempotent). */
    private fun cancelEnableDialogAnimators() {
        enableDialogAnimators.forEach { it.cancel() }
        enableDialogAnimators.clear()
    }

    /** Launches the system CallScreening role request (no-op if already held/unavailable). */
    private fun requestEnableCallerId() {
        val intent = InstallIdRegistry.buildEnableIntent(this) ?: run {
            refreshCallerIdGate(); return
        }
        // We're leaving to a system page — don't let the return trip trigger an App Open ad.
        OpenPromoRegistry.skipNextAppOpenAd = true
        runCatching { callerIdRoleLauncher.launch(intent) }
    }

    /**
     * Re-evaluates the gate. Once Caller ID is on, the enable-prompt is dismissed
     * and block management is immediately usable again (checked lazily per tap).
     * Called after the role round-trip and on every resume, so recreation or a
     * change made in system settings can never leave the prompt stranded.
     */
    private fun refreshCallerIdGate() {
        if (!InstallIdRegistry.isCallerIdEnabled(this)) return
        enableCallerIdDialog?.dismiss()
        enableCallerIdDialog = null

        // Caller ID just turned on — pick up exactly where the user left off (e.g.
        // reopen the add-number dialog) instead of leaving them on a bare list or
        // bouncing to Home. Posted so it runs after the role round-trip settles and
        // the window is ready to host a dialog. Runs at most once.
        val resume = pendingCallerIdAction ?: return
        pendingCallerIdAction = null
        binding.root.post { if (!isFinishing && !isDestroyed) resume() }
    }

    override fun onResume() {
        super.onResume()
        refreshCallerIdGate()
    }

    override fun onDestroy() {
        // Avoid leaking the dialog window / animators across recreation.
        cancelEnableDialogAnimators()
        enableCallerIdDialog?.dismiss()
        enableCallerIdDialog = null
        pendingCallerIdAction = null
        super.onDestroy()
    }

    /** Wraps a custom view in a transparent, centered, ~88%-width dialog. */
    private fun customDialog(content: View): Dialog = Dialog(this).apply {
        setContentView(content)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
