package com.callerid.number.lookup.home.screen.dialpad

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.callerid.number.lookup.home.store.FrequentDigit
import com.callerid.number.lookup.home.databinding.CellFrequentBinding
import com.callerid.number.lookup.home.screen.shared.CallFormatter

class FrequentAdapter(
    private val onClick: (String) -> Unit,
    private val onCall: (String) -> Unit
) : RecyclerView.Adapter<FrequentAdapter.VH>() {

    private var items: List<FrequentDigit> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<FrequentDigit>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: CellFrequentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(CellFrequentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val hasName = !item.name.isNullOrBlank()
        holder.binding.lblName.text = CallFormatter.displayName(item.name, item.number)
        holder.binding.lblAvatar.text = CallFormatter.initials(item.name, item.number)

        holder.binding.lblCount.text = item.number
        holder.binding.lblCount.visibility = if (hasName) View.VISIBLE else View.GONE
        holder.binding.root.setOnClickListener { onClick(item.number) }
        holder.binding.padCall.setOnClickListener { onCall(item.number) }
    }

    override fun getItemCount(): Int = items.size
}
