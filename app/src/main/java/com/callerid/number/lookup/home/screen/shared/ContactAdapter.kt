package com.callerid.number.lookup.home.screen.shared

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.callerid.number.lookup.home.store.ContactItem
import com.callerid.number.lookup.home.databinding.CellContactBinding

class ContactAdapter(
    private val items: List<ContactItem>
) : RecyclerView.Adapter<ContactAdapter.VH>() {

    inner class VH(val binding: CellContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = CellContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        with(holder.binding) {
            lblAvatar.text = item.initials
            lblName.text = item.name
            lblNumber.text = item.detail
        }
    }

    override fun getItemCount(): Int = items.size
}
