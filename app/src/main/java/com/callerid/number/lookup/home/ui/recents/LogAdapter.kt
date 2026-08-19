package com.callerid.number.lookup.home.ui.recents

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.data.CallEntry
import com.callerid.number.lookup.home.data.CallFlavor
import com.callerid.number.lookup.home.databinding.CellCallBinding
import com.callerid.number.lookup.home.databinding.CellSectionHeaderBinding
import com.callerid.number.lookup.home.ui.common.CallFormatter
import com.callerid.number.lookup.home.ui.common.HomeAnim

class LogAdapter(
    private val onCall: (String) -> Unit,
    private val onOpen: (CallEntry) -> Unit,
    private val onIdentify: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<LogRow> = emptyList()

    /** Rows animate in once; scrolling back or re-submitting must not replay the stagger. */
    private var lastAnimated = -1

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<LogRow>) {
        rows = list
        lastAnimated = -1
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is LogRow.Header) TYPE_HEADER else TYPE_CALL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(CellSectionHeaderBinding.inflate(inflater, parent, false))
        } else {
            CallVH(CellCallBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is LogRow.Header -> (holder as HeaderVH).binding.tvHeader.setText(row.titleRes)
            is LogRow.Call -> (holder as CallVH).bind(row)
        }

        // Claude Design's cid-rise-in stagger, once per row per submit().
        if (position > lastAnimated) {
            lastAnimated = position
            HomeAnim.riseIn(holder.itemView, delay = position * HomeAnim.STAGGER_STEP)
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.itemView.animate().cancel()
    }

    override fun getItemCount(): Int = rows.size

    class HeaderVH(val binding: CellSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    inner class CallVH(val binding: CellCallBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: LogRow.Call) {
            val e = row.entry
            val ctx = binding.root.context
            val isSpam = e.type == CallFlavor.SPAM

            fun color(res: Int) = ContextCompat.getColor(ctx, res)
            fun tint(res: Int) = ColorStateList.valueOf(color(res))

            binding.tvAvatar.text = CallFormatter.initials(e.name, e.number)
            binding.tvName.text = CallFormatter.displayName(e.name, e.number)

            val type = ctx.getString(CallFormatter.typeLabelRes(e.type))
            val time = CallFormatter.timeLabel(e.date)
            val duration = CallFormatter.durationLabel(e.durationSec)
            binding.tvSub.text = buildString {
                append(type).append(" · ").append(time)
                if (duration.isNotEmpty()) append(" · ").append(duration)
            }

            // Verdict-tinted row + avatar (matches the Home list): spam reads red,
            // everything else sits on a neutral card with a primary-container avatar.
            binding.rowCall.setBackgroundResource(
                if (isSpam) R.drawable.shape_home_tile_spam else R.drawable.shape_home_tile
            )
            binding.tvAvatar.backgroundTintList =
                tint(if (isSpam) R.color.spam_avatar_bg else R.color.primary_container)
            binding.tvAvatar.setTextColor(
                color(if (isSpam) R.color.spam_on else R.color.on_primary_container)
            )
            binding.tvName.setTextColor(color(if (isSpam) R.color.spam_on else R.color.on_surface))

            // Icon + subtitle colour by verdict/type.
            val subColorRes = when (e.type) {
                CallFlavor.MISSED -> R.color.danger
                CallFlavor.SPAM -> R.color.spam_on
                else -> R.color.on_surface_variant
            }
            binding.ivType.setImageResource(CallFormatter.typeIconRes(e.type))
            binding.ivType.imageTintList = tint(subColorRes)
            binding.tvSub.setTextColor(color(subColorRes))

            // Spam → no action; unknown/unsaved → Identify (opens Lookup); else Call.
            val unknown = e.name.isNullOrBlank() && e.number.isNotBlank()
            when {
                isSpam -> {
                    binding.btnCall.visibility = View.GONE
                    binding.btnIdentify.visibility = View.GONE
                }
                unknown -> {
                    binding.btnCall.visibility = View.GONE
                    binding.btnIdentify.visibility = View.VISIBLE
                    binding.btnIdentify.setOnClickListener { onIdentify(e.number) }
                }
                else -> {
                    binding.btnCall.visibility = View.VISIBLE
                    binding.btnIdentify.visibility = View.GONE
                    binding.btnCall.setOnClickListener { onCall(e.number) }
                }
            }
            binding.root.setOnClickListener { onOpen(e) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_CALL = 1
    }
}
