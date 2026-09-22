package com.callerid.number.lookup.home.shell.panels

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.StatFs
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.format.Formatter
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import com.callerid.admesh.engine.ShellPromoConfig
import com.callerid.admesh.surface.BonusPromo
import com.callerid.admesh.surface.InlinePromo
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.databinding.BoardLeftPanelBinding
import com.callerid.number.lookup.home.shell.entities.AppTile
import com.callerid.number.lookup.home.shell.screens.HomeBoardActivity
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * The swipe-left side panel: this app's **Free up space** page (ported from the reference
 * launcher's left_panel_fragment).
 *
 * It used to be app search plus Suggested/Recent app grids. Those went when this became the
 * cleaner — the app drawer already has a search field and the full app list, so the panel was a
 * second route to the same place, and clearing the cache is something the drawer cannot do. The
 * home "Search apps" pill now opens the app drawer (see [HomeBoardActivity.openAppSearch]).
 *
 * Scope is this app's own `cacheDir`/`externalCacheDir` — the whole of what an app may clear
 * without special permissions. Unlike [com.callerid.number.lookup.home.screen.cleaner.CleanerActivity],
 * which sweeps on open, this panel opens on every swipe, so it opens on "here is what can be freed"
 * with the action still to take and sweeps only when the button is pressed — clearing the caches on
 * every swipe would throw away caches the app has just built. The sweep is gated behind a rewarded
 * ad and the button locks while it runs.
 *
 * [gotLaunchers], [hasQuery] and [resetSearch] survive as no-ops / trivial answers: the host still
 * calls them from its app-list refresh and its back handling, and a panel that no longer searches
 * still has to answer them.
 */
class LeftPanel(
    context: Context,
    attributeSet: AttributeSet,
) : BasePanel<BoardLeftPanelBinding>(context, attributeSet) {

    private data class SweepResult(val bytes: Long, val files: Int)

    /** What the last scan found — the figures this page is built around. */
    private var sweepable = SweepResult(0L, 0)

    /** True from the button tap until the rewarded ad and sweep finish (or fail). Locks the button. */
    private var cleaning = false

    private val nativePromo = InlinePromo()

    private var adSlot = ShellPromoConfig.Slot(
        enabled = false,
        adType = ShellPromoConfig.SlotAd.NONE,
        nativeType = "mid2",
        bannerType = "adaptive",
        adUnitId = "",
    )

    // The panel covers the whole screen while open, so HomeBoardActivity never sees these events.
    private val gestureDetector = GestureDetectorCompat(context, object : SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            // Swipe right still closes the panel — navigation unchanged.
            if (velocityX > 0 && abs(velocityX) > abs(velocityY)) {
                activity?.hideLeftPanel()
                return true
            }
            return false
        }
    })

    override fun setupFragment(activity: HomeBoardActivity) {
        this.activity = activity
        this.binding = BoardLeftPanelBinding.bind(this)

        adSlot = ShellPromoConfig.sidePanelSlot(activity)
        if (adSlot.needsNativePreload) nativePromo.fetchNativeAds(activity)

        binding.panelCleanerActionVw.setOnClickListener { onActionClicked() }
        renderStorage()
    }

    /**
     * Opens on "here is what can be freed": re-scan the cache, refresh the storage figure and the
     * bottom ad, and preload the rewarded ad that gates the sweep. Re-scanning on each open is the
     * point — the user may have cleared, left and come back, and a stale figure would mislead.
     */
    fun onPanelShown() {
        val activity = activity ?: return

        BonusPromo.preload(activity)

        val fresh = ShellPromoConfig.sidePanelSlot(activity)
        if (fresh != adSlot) {
            adSlot = fresh
            if (adSlot.needsNativePreload) nativePromo.fetchNativeAds(activity)
        }
        ShellPromoConfig.refreshSlot(activity, adSlot, binding.adNativeFrameVw, binding.adShimmerVw)

        scan()
        renderStorage()
    }

    /** Measures the cache without touching it, off the main thread so the slide-in never stutters. */
    private fun scan() {
        if (cleaning) return
        val ctx = context.applicationContext
        thread {
            val found = measureSweepable(ctx)
            post {
                if (cleaning) return@post
                sweepable = found
                renderFound(found)
            }
        }
    }

    private fun onActionClicked() {
        // One press at a time: the guard blocks duplicate taps (and a second rewarded-ad trigger)
        // while the ad is on screen and the sweep runs.
        if (cleaning) return

        // Nothing to free: the button is the "Done" that closes the panel back to the home screen.
        if (sweepable.files == 0) {
            activity?.hideLeftPanel()
            return
        }

        val host = activity ?: return
        cleaning = true
        lockAction()

        // Gate the sweep behind the rewarded ad. BonusPromo.show fires this callback on dismiss, on
        // a show/load failure (after its fallback) and when ads are off — i.e. once the flow is done
        // either way — so this is the single place the sweep runs and the button unlocks.
        BonusPromo().show(host) {
            performClean()
        }
    }

    /** Clears this app's caches off the main thread, then switches to the freed verdict. */
    private fun performClean() {
        val ctx = context.applicationContext
        thread {
            val freed = measureSweepable(ctx)
            runCatching {
                ctx.cacheDir?.deleteRecursively()
                ctx.externalCacheDir?.deleteRecursively()
            }
            post {
                cleaning = false
                sweepable = SweepResult(0L, 0)
                renderCleaned(freed)
                // The volume figures moved, by exactly what was just freed.
                renderStorage()
            }
        }
    }

    /** Locks the CTA while the ad/sweep is in flight so it cannot be tapped again. */
    private fun lockAction() = with(binding.panelCleanerActionVw) {
        isEnabled = false
        text = context.getString(R.string.cleaner_working)
    }

    /** The "before" state: what a sweep would free, with the action still to take. */
    private fun renderFound(result: SweepResult) = with(binding) {
        val nothing = result.files == 0
        panelCleanerLabelVw.setText(if (nothing) R.string.cleaner_all_clear else R.string.cleaner_ready)
        panelCleanerAmountVw.text = formatBytes(result.bytes)
        panelCleanerAmountBodyVw.setText(R.string.cleaner_found_body)
        panelCleanerRecoveredLabelVw.setText(R.string.cleaner_space_found)
        panelCleanerRecoveredVw.text = formatBytes(result.bytes)
        panelCleanerItemsVw.text = context.getString(R.string.cleaner_items, result.files)
        // Nothing to free means the job is already done, so the button becomes the "Done" that
        // closes the panel rather than a disabled dead end. Otherwise it is the rewarded-gated
        // "Clean now", carrying a lock to mark that the sweep plays an ad first.
        panelCleanerActionVw.isEnabled = true
        if (nothing) {
            panelCleanerActionVw.setText(R.string.cleaner_done)
        } else {
            panelCleanerActionVw.text = lockedActionLabel()
        }
    }

    /**
     * "🔒 Clean now" as a single centred run: the lock inlined immediately before the label so the
     * two centre together. A compound drawable would pin the lock to the button's far-left edge
     * (this is a match_parent, gravity=center button) while the text stays centred — visibly out of
     * line — so the icon is carried by a leading span instead.
     */
    private fun lockedActionLabel(): CharSequence {
        val label = context.getString(R.string.cleaner_action)
        val icon = ContextCompat.getDrawable(context, R.drawable.sym_lock_fill)?.mutate() ?: return label
        val size = (16 * resources.displayMetrics.density).toInt()
        icon.setBounds(0, 0, size, size)
        // First space carries the icon span; the second is the icon–text gap.
        val text = SpannableStringBuilder("  ").append(label)
        text.setSpan(CenteredImageSpan(icon), 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        return text
    }

    /** The "after" state — the same verdict and wording CleanerActivity shows once it has swept. */
    private fun renderCleaned(freed: SweepResult) = with(binding) {
        panelCleanerLabelVw.setText(R.string.cleaner_all_clear)
        panelCleanerAmountVw.text = formatBytes(freed.bytes)
        panelCleanerAmountBodyVw.setText(R.string.cleaner_cleaned_body)
        panelCleanerRecoveredLabelVw.setText(R.string.cleaner_space_recovered)
        panelCleanerRecoveredVw.text = formatBytes(freed.bytes)
        panelCleanerItemsVw.text = context.getString(R.string.cleaner_items, 0)
        // The sweep is finished: the button now closes the panel back to the home screen.
        panelCleanerActionVw.isEnabled = true
        panelCleanerActionVw.setText(R.string.cleaner_done)
    }

    /** Whole-volume figures for the data partition (`StatFs` on `filesDir`), off the main thread. */
    private fun renderStorage() {
        thread {
            val stat = StatFs(context.filesDir.absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            val used = total - free
            val percent = if (total == 0L) 0 else ((used * 100) / total).toInt().coerceIn(0, 100)
            post {
                binding.panelCleanerStorageBarVw.progress = percent
                binding.panelCleanerPercentVw.text =
                    context.getString(R.string.cleaner_percent, percent)
                binding.panelCleanerStorageTextVw.text = context.getString(
                    R.string.cleaner_storage_used, formatBytes(used), formatBytes(total)
                )
            }
        }
    }

    private fun measureSweepable(ctx: Context): SweepResult {
        var bytes = 0L
        var files = 0
        listOf(ctx.cacheDir, ctx.externalCacheDir).forEach { root ->
            walk(root) { f ->
                bytes += f.length()
                files++
            }
        }
        return SweepResult(bytes, files)
    }

    private inline fun walk(dir: File?, onFile: (File) -> Unit) {
        if (dir == null || !dir.exists()) return
        dir.walkBottomUp().forEach { if (it.isFile) onFile(it) }
    }

    private fun formatBytes(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    /**
     * The host refreshes the app list on package changes and hands it here. Nothing to do with it
     * any more — the panel shows no apps — but the call site is about the app list, not this panel.
     */
    @Suppress("UNUSED_PARAMETER")
    fun gotLaunchers(appLaunchers: List<AppTile>) = Unit

    /** No search field here any more, so there is never a query to go back out of. */
    fun hasQuery() = false

    /** Back to the top, which is all "reset" can mean on a panel with one screen of content. */
    fun resetSearch() {
        binding.panelScrollVw.scrollTo(0, 0)
    }
}

/**
 * An [ImageSpan] that centres its drawable on the text's vertical midline. The default `ImageSpan`
 * sits the image on the baseline, so a lock inlined into a button label hangs low against the text.
 * `ALIGN_CENTER` would do this but is API 29+, so the centring is done from the paint's metrics.
 */
private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {
    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val d = drawable
        val fm = paint.fontMetricsInt
        val lineCenter = y + (fm.descent + fm.ascent) / 2
        val transY = lineCenter - d.bounds.height() / 2
        canvas.save()
        canvas.translate(x, transY.toFloat())
        d.draw(canvas)
        canvas.restore()
    }
}
