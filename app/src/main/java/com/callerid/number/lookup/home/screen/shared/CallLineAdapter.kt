package com.callerid.number.lookup.home.screen.shared

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.store.CallCardModel
import com.callerid.number.lookup.home.store.CallFlavor
import com.callerid.number.lookup.home.databinding.CellCallBinding

class CallLineAdapter(
    initial: List<CallCardModel> = emptyList(),
    private val onCall: (String) -> Unit = {},
    private val onIdentify: (String) -> Unit = {}
) : RecyclerView.Adapter<CallLineAdapter.VH>() {

    private var items: List<CallCardModel> = initial

    /** Rows animate in once; scrolling back or re-submitting must not replay the stagger. */
    private var lastAnimated = -1

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<CallCardModel>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: CellCallBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = CellCallBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        val isSpam = item.type == CallFlavor.SPAM

        fun color(res: Int) = ContextCompat.getColor(ctx, res)
        fun tint(res: Int) = ColorStateList.valueOf(color(res))

        with(holder.binding) {
            lblAvatar.text = item.initials
            lblName.text = item.name
            lblSub.text = item.info

            // Verdict container: spam rows read red before the text does; everything
            // else sits on a neutral surface card with a primary-container avatar.
            rowCallVw.setBackgroundResource(
                if (isSpam) R.drawable.form_home_tile_spam else R.drawable.form_home_tile
            )
            lblAvatar.backgroundTintList =
                tint(if (isSpam) R.color.spam_avatar_bg else R.color.primary_container)
            lblAvatar.setTextColor(color(if (isSpam) R.color.spam_on else R.color.on_primary_container))
            lblName.setTextColor(color(if (isSpam) R.color.spam_on else R.color.on_surface))

            val (iconRes, subColorRes) = when (item.type) {
                CallFlavor.INCOMING -> R.drawable.slot_arrow_down_left to R.color.on_surface_variant
                CallFlavor.OUTGOING -> R.drawable.slot_arrow_up_right to R.color.on_surface_variant
                CallFlavor.MISSED -> R.drawable.sym_call_missed to R.color.danger
                CallFlavor.SPAM -> R.drawable.slot_shield_warning to R.color.spam_on
            }
            picType.setImageResource(iconRes)
            picType.imageTintList = tint(subColorRes)
            lblSub.setTextColor(color(subColorRes))

            // Spam → no action (auto-blocked); unknown number → Identify (opens Lookup);
            // otherwise the Call button.
            val unknown = !item.identified && item.number.isNotBlank()
            when {
                isSpam -> {
                    padCall.visibility = android.view.View.GONE
                    padIdentify.visibility = android.view.View.GONE
                }
                unknown -> {
                    padCall.visibility = android.view.View.GONE
                    padIdentify.visibility = android.view.View.VISIBLE
                    padIdentify.setOnClickListener { onIdentify(item.number) }
                }
                else -> {
                    padCall.visibility = android.view.View.VISIBLE
                    padIdentify.visibility = android.view.View.GONE
                    padCall.setOnClickListener { if (item.number.isNotBlank()) onCall(item.number) }
                }
            }
        }

        // Claude Design's cid-rise-in stagger, once per row.
        if (position > lastAnimated) {
            lastAnimated = position
            HomeAnim.riseIn(holder.itemView, delay = position * HomeAnim.STAGGER_STEP)
        }
    }

    override fun onViewDetachedFromWindow(holder: VH) {
        super.onViewDetachedFromWindow(holder)
        holder.itemView.animate().cancel()
    }

    override fun getItemCount(): Int = items.size
}
