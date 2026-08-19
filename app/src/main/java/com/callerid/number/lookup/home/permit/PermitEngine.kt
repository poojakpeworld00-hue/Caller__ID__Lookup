package com.callerid.number.lookup.home.permit

import java.util.Locale
import android.app.Activity
import android.content.Context
import com.callerid.admesh.engine.trackEvent
import com.callerid.admesh.engine.logPermissionResult
import com.callerid.number.lookup.home.kit.LogRail
import java.lang.ref.WeakReference

object PermitEngine {

    private const val TAG = "PermitEngine"

    private val scheduler = PermitScheduler()

    private var activeRef: WeakReference<Activity>? = null
    private var running = false

    private var pendingOnComplete: (() -> Unit)? = null

    fun init(context: Context) {
        try {
            PermitSource.refreshFromRemote()
        } catch (e: Exception) {
            LogRail.error(TAG, "init failed", e)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun check(activity: Activity, onComplete: (() -> Unit)? = null) {
        try {
            if (activity.isFinishing || activity.isDestroyed) {
                fireComplete(onComplete)
                return
            }
            val name = activity::class.java.simpleName

            val current = activeRef?.get()
            if (running && current === activity) return

            if (running && current !== activity) abort()

            pendingOnComplete = onComplete

            val allRules = PermitSource.rules()
            val matched = if (allRules.isEmpty()) emptyList()
            else ScreenGlob.rulesFor(name, allRules)
            if (matched.isEmpty()) {
                LogRail.log(TAG, "No permission rules for $name")
                complete()
                return
            }

            val prefs = PermitVault(activity)
            val pending = matched.filter { rule -> isStillNeeded(activity, rule, prefs) }
            if (pending.isEmpty()) {
                LogRail.log(TAG, "All configured permissions already satisfied on $name")
                complete()
                return
            }

            LogRail.log(TAG, "Queueing ${pending.size} permission(s) for $name")
            running = true
            activeRef = WeakReference(activity)
            processNext(PermitQueue(pending))
        } catch (e: Exception) {
            LogRail.error(TAG, "check() failed", e)
            abort()
        }
    }

    @JvmStatic
    @JvmOverloads
    fun request(activity: Activity, key: String, onComplete: (() -> Unit)? = null) {
        try {
            if (activity.isFinishing || activity.isDestroyed) {
                fireComplete(onComplete); return
            }
            val spec = PermitKit.spec(key)
            if (spec == null) {
                LogRail.log(TAG, "request(): unknown permission key '$key' — skipped")
                fireComplete(onComplete); return
            }
            if (!PermitKit.isApplicableOnThisSdk(spec) ||
                !PermitKit.isPrefGateOpen(activity, spec) ||
                PermitKit.isGranted(activity, spec)
            ) {
                fireComplete(onComplete); return
            }

            val rule = PermitSource.rules().firstOrNull { it.key == key }
            if (rule != null && !rule.enabled) {
                LogRail.log(TAG, "request(): '$key' disabled in config — skipped")
                fireComplete(onComplete); return
            }
            val prefs = PermitVault(activity)
            if (rule?.showOnce == true && prefs.wasShown(key)) {
                fireComplete(onComplete); return
            }

            prefs.markAsked(key)
            if (rule?.showOnce == true) prefs.markShown(key)

            val shortName = spec.androidPermission.substringAfterLast('.').lowercase(Locale.ROOT)
            activity.trackEvent("perm_${shortName}_show")
            LogRail.log(TAG, "request(): asking '$key' on ${activity::class.java.simpleName}")

            PermitLauncher.launch(activity, spec.androidPermission) { granted ->
                activity.logPermissionResult(spec.androidPermission, granted)
                LogRail.log(TAG, "request(): '$key' granted=$granted")
                fireComplete(onComplete)
            }
        } catch (e: Exception) {
            LogRail.error(TAG, "request() failed", e)
            fireComplete(onComplete)
        }
    }

    private fun isStillNeeded(
        activity: Activity,
        rule: PermitRule,
        prefs: PermitVault,
    ): Boolean {
        val spec = PermitKit.spec(rule.key)
        if (spec == null) {
            LogRail.log(TAG, "Unknown permission key '${rule.key}' — skipped")
            return false
        }
        if (!PermitKit.isApplicableOnThisSdk(spec)) return false
        if (!PermitKit.isPrefGateOpen(activity, spec)) {
            LogRail.log(TAG, "Pref gate '${spec.enabledPrefGate}' closed — '${rule.key}' skipped")
            return false
        }
        if (PermitKit.isGranted(activity, spec)) return false
        if (rule.showOnce && prefs.wasShown(rule.key)) return false
        return true
    }

    private fun processNext(queue: PermitQueue) {
        val rule = queue.poll() ?: run { complete(); return }
        val spec = PermitKit.spec(rule.key) ?: run { processNext(queue); return }

        val activity = activeRef?.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            abort(); return
        }

        if (PermitKit.isGranted(activity, spec)) {
            processNext(queue); return
        }

        scheduler.schedule(rule.delayMs) {
            val act = activeRef?.get()
            if (act == null || act.isFinishing || act.isDestroyed) {
                abort(); return@schedule
            }
            if (PermitKit.isGranted(act, spec)) {
                processNext(queue); return@schedule
            }

            val prefs = PermitVault(act)
            prefs.markAsked(rule.key)
            if (rule.showOnce) prefs.markShown(rule.key)

            val shortName = spec.androidPermission.substringAfterLast('.').lowercase(Locale.ROOT)
            act.trackEvent("perm_${shortName}_show")

            LogRail.log(TAG, "Requesting '${rule.key}' on ${act::class.java.simpleName}")
            PermitLauncher.launch(act, spec.androidPermission) { granted ->

                (activeRef?.get() ?: act).logPermissionResult(spec.androidPermission, granted)
                LogRail.log(TAG, "Result '${rule.key}' granted=$granted")

                processNext(queue)
            }
        }
    }

    private fun complete() {
        scheduler.clear()
        running = false
        activeRef = null
        val cb = pendingOnComplete
        pendingOnComplete = null
        fireComplete(cb)
    }

    private fun abort() {
        scheduler.clear()
        running = false
        activeRef = null
        pendingOnComplete = null
    }

    private fun fireComplete(cb: (() -> Unit)?) {
        if (cb == null) return
        try {
            cb()
        } catch (e: Exception) {
            LogRail.error(TAG, "onComplete callback threw", e)
        }
    }
}
