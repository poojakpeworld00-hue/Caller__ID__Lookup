package com.callerid.number.lookup.home.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.callerid.number.lookup.home.data.ContactItem
import com.callerid.number.lookup.home.databinding.TileContactBinding

class ContactAdapter(
    private val items: List<ContactItem>
) : RecyclerView.Adapter<ContactAdapter.VH>() {

    inner class VH(val binding: TileContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = TileContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvAvatar.text = item.initials
            tvName.text = item.name
            tvNumber.text = item.detail
        }
    }

    override fun getItemCount(): Int = items.size
}
