package com.callerid.number.lookup.home.screen.identify

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.callerid.number.lookup.home.databinding.CellSearchHistoryBinding
import com.callerid.number.lookup.home.screen.shared.CallFormatter
import com.callerid.number.lookup.home.screen.shared.HomeAnim

class TraceAdapter(
    private val onClick: (TraceRow) -> Unit,
    private val onCall: (TraceRow) -> Unit,

    private val onRevealName: (TraceRow) -> Unit = {}
) : RecyclerView.Adapter<TraceAdapter.VH>() {

    private val items = mutableListOf<TraceRow>()

    private val revealed = mutableSetOf<String>()

    private var lastAnimated = -1

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<TraceRow>) {
        items.clear()
        items.addAll(list)
        lastAnimated = -1
        notifyDataSetChanged()
    }

    fun revealName(rawNumber: String) {
        if (revealed.add(rawNumber)) {
            val i = items.indexOfFirst { it.rawNumber == rawNumber }
            if (i != RecyclerView.NO_POSITION) notifyItemChanged(i)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun resetReveals() {
        if (revealed.isEmpty()) return
        revealed.clear()
        notifyDataSetChanged()
    }

    private fun isLocked(item: TraceRow): Boolean =
        item.name != null && item.rawNumber !in revealed

    inner class VH(val binding: CellSearchHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onClick(items[p])
            }
            binding.picHistCall.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onCall(items[p])
            }

            binding.picHistReveal.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onRevealName(items[p])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = CellSearchHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        with(holder.binding) {
            lblHistAvatar.text = CallFormatter.initials(item.name, item.rawNumber)
            val locked = isLocked(item)

            lblHistName.text = if (locked) blurName(item.name!!) else (item.name ?: item.number)
            picHistReveal.visibility = if (locked) View.VISIBLE else View.GONE
            lblHistSub.text = item.subtitle ?: item.number
        }

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

    private fun blurName(name: String): String =
        if (name.isNotEmpty()) name[0] + "•".repeat(name.length - 1) else name
}
