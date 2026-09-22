package com.callerid.number.lookup.home.shell.lists

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.realScreenSize
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.shell.screens.ShellBaseActivity
import com.callerid.number.lookup.home.databinding.CellLauncherLabelBinding
import com.callerid.number.lookup.home.shell.ext.animateScale
import com.callerid.number.lookup.home.shell.ext.config
import com.callerid.number.lookup.home.shell.contracts.DrawerListener
import com.callerid.number.lookup.home.shell.entities.AppTile

class AppTileAdapter(
    val activity: ShellBaseActivity,
    val allAppsListener: DrawerListener,
    val itemClick: (Any) -> Unit
) : ListAdapter<AppTile, RecyclerView.ViewHolder>(AppTileDiff()),
    RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textColor = Color.WHITE
    private var iconPadding = 0

    private var adGapEnabled = false

    private var adRow = 1

    private var adGapHeightPx = 0

    private val headerCount: Int get() = if (adGapEnabled) 1 else 0

    private val adPosition: Int
        get() {
            if (!adGapEnabled) return -1
            val columns = activity.config.drawerColumnCount.coerceAtLeast(1)
            return (adRow * columns).coerceAtMost(currentList.size)
        }

    init {
        setHasStableIds(true)
        calculateIconWidth()
    }

    /**
     * Opens (or closes) an empty full-width gap in the list at [row], [heightPx] tall — the slot
     * the sticky native ad overlay aligns itself to. The ad itself is drawn in the overlay, not
     * here; this row only reserves the space. See AppDrawerPanel.syncStickyAd.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun setAdGap(enabled: Boolean, row: Int, heightPx: Int) {
        val newRow = row.coerceAtLeast(0)
        val newHeight = heightPx.coerceAtLeast(0)
        if (adGapEnabled == enabled && adRow == newRow && adGapHeightPx == newHeight) {
            return
        }

        adGapEnabled = enabled
        adRow = newRow
        adGapHeightPx = newHeight
        notifyDataSetChanged()
    }

    /** The item index of the ad gap, or -1 when there is none. Used to place the sticky overlay. */
    fun adGapPosition(): Int = adPosition

    fun isAdRow(position: Int): Boolean = adGapEnabled && position == adPosition

    private fun launcherIndex(position: Int): Int {
        val ad = adPosition
        return if (ad in 0 until position) position - 1 else position
    }

    override fun getItemCount(): Int = super.getItemCount() + headerCount

    override fun getItemViewType(position: Int): Int =
        if (isAdRow(position)) VIEW_TYPE_AD else VIEW_TYPE_LAUNCHER

    override fun getItemId(position: Int): Long {
        if (isAdRow(position)) {
            return AD_HEADER_ID
        }

        val item = getItem(launcherIndex(position))
        val base = item.getLauncherIdentifier().hashCode().toLong()
        // Keep promo ids in their own high band so they never collide with an app id (Int-range
        // hashCode) or the ad row (Long.MIN_VALUE).
        return if (item.isPromo) PROMO_ID_BASE + base else base
    }

    fun launchFirstApp(): Boolean {
        val launcher = currentList.firstOrNull() ?: return false
        itemClick(launcher)
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == VIEW_TYPE_AD) {
            val host = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    adGapHeightPx.coerceAtLeast(1)
                )
            }
            return AdGapViewHolder(host)
        }

        val binding = CellLauncherLabelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AdGapViewHolder -> holder.sizeTo(adGapHeightPx)
            is ViewHolder -> holder.bindView(getItem(launcherIndex(position)))
        }
    }

    override fun submitList(list: MutableList<AppTile>?) {
        calculateIconWidth()
        super.submitList(list)
    }

    private fun calculateIconWidth() {
        val currentColumnCount = activity.config.drawerColumnCount
        val iconWidth = activity.realScreenSize.x / currentColumnCount
        iconPadding = (iconWidth * 0.1f).toInt()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateTextColor(newTextColor: Int) {
        if (newTextColor != textColor) {
            textColor = newTextColor
            notifyDataSetChanged()
        }
    }

    /**
     * The empty gap the sticky native ad aligns to. It holds no ad — it only reserves [heightPx]
     * of full-width space in the list so the icons part where the overlaid ad sits. The ad itself
     * is drawn in AppDrawerPanel's overlay, which tracks this row and docks to the bottom when it
     * scrolls off.
     */
    class AdGapViewHolder(private val host: FrameLayout) : RecyclerView.ViewHolder(host) {
        fun sizeTo(heightPx: Int) {
            val lp = host.layoutParams ?: RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, heightPx
            )
            if (lp.height != heightPx) {
                lp.height = heightPx.coerceAtLeast(1)
                host.layoutParams = lp
            }
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        @SuppressLint("ClickableViewAccessibility")
        fun bindView(launcher: AppTile): View {
            val binding = CellLauncherLabelBinding.bind(itemView)
            itemView.apply {
                binding.launcherLabelVw.text = launcher.title
                binding.launcherLabelVw.setTextColor(textColor)
                binding.launcherLabelVw.beVisibleIf(activity.config.showDrawerAppLabels)
                binding.launcherIconVw.setPadding(iconPadding, iconPadding, iconPadding, 0)
                binding.promoBadgeVw.beVisibleIf(launcher.isPromo)

                if (launcher.isPromo) {
                    Glide.with(activity)
                        .load(launcher.iconUrl)
                        .placeholder(
                            activity.resources.getColoredDrawableWithColor(
                                drawableId = R.drawable.stub_drawable,
                                color = launcher.thumbnailColor
                            )
                        )
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.launcherIconVw)
                } else if (launcher.drawable != null && binding.launcherIconVw.tag == true) {
                    binding.launcherIconVw.setImageDrawable(launcher.drawable)
                } else {
                    val placeholderDrawable = activity.resources.getColoredDrawableWithColor(
                        drawableId = R.drawable.stub_drawable,
                        color = launcher.thumbnailColor
                    )
                    Glide.with(activity)
                        .load(launcher.drawable)
                        .placeholder(placeholderDrawable)
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .into(object : DrawableImageViewTarget(binding.launcherIconVw) {
                            override fun onResourceReady(
                                resource: Drawable,
                                transition: Transition<in Drawable>?
                            ) {
                                super.onResourceReady(resource, transition)
                                view.tag = true
                            }
                        })
                }

                if (launcher.isPromo) {
                    // A promo tile opens its link and offers no home-screen menu.
                    setOnClickListener { openPromo(launcher.link) }
                    setOnLongClickListener { true }
                } else {
                    setOnClickListener { itemClick(launcher) }
                    setOnLongClickListener {
                        val location = IntArray(2)
                        getLocationOnScreen(location)
                        allAppsListener.onAppLauncherLongPressed(
                            x = (location[0] + width / 2).toFloat(),
                            y = location[1].toFloat(),
                            appLauncher = launcher
                        )
                        true
                    }
                }

                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            binding.launcherIconVw.drawable?.alpha = LAUNCHER_ALPHA_PRESSED
                            animateScale(
                                from = LAUNCHER_SCALE_NORMAL,
                                to = LAUNCHER_SCALE_PRESSED,
                                duration = LAUNCHER_SCALE_UP_DURATION
                            )
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            binding.launcherIconVw.drawable?.alpha = LAUNCHER_ALPHA_NORMAL
                            animateScale(
                                from = LAUNCHER_SCALE_PRESSED,
                                to = LAUNCHER_SCALE_NORMAL,
                                duration = LAUNCHER_SCALE_DOWN_DURATION
                            )
                        }
                    }
                    false
                }
            }

            return itemView
        }
    }

    /** Opens a promo tile's configured link in the browser; a null-scheme/blank link is ignored. */
    private fun openPromo(link: String) {
        val uri = runCatching { Uri.parse(link).takeIf { !it.scheme.isNullOrBlank() } }.getOrNull()
            ?: return
        runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onChange(position: Int) =
        currentList.getOrNull(launcherIndex(position))?.getBubbleText() ?: ""

    companion object {
        const val VIEW_TYPE_LAUNCHER = 0
        const val VIEW_TYPE_AD = 1

        private const val AD_HEADER_ID = Long.MIN_VALUE

        /** High band for promo-tile stable ids so they never collide with app ids. */
        private const val PROMO_ID_BASE = 1L shl 40

        private const val LAUNCHER_SCALE_NORMAL = 1f
        private const val LAUNCHER_SCALE_PRESSED = 1.15f
        private const val LAUNCHER_SCALE_UP_DURATION = 100L
        private const val LAUNCHER_SCALE_DOWN_DURATION = 50L
        private const val LAUNCHER_ALPHA_NORMAL = 255
        private const val LAUNCHER_ALPHA_PRESSED = 220
    }
}

class AppTileDiff : DiffUtil.ItemCallback<AppTile>() {
    override fun areItemsTheSame(oldItem: AppTile, newItem: AppTile): Boolean {
        return oldItem.getLauncherIdentifier().hashCode().toLong() ==
                newItem.getLauncherIdentifier().hashCode().toLong()
    }

    override fun areContentsTheSame(oldItem: AppTile, newItem: AppTile): Boolean {
        return oldItem.title == newItem.title &&
                oldItem.order == newItem.order &&
                oldItem.thumbnailColor == newItem.thumbnailColor &&
                oldItem.drawable != null &&
                newItem.drawable != null
    }
}
