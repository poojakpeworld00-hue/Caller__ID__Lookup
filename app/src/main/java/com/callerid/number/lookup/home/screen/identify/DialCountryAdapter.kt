package com.callerid.number.lookup.home.screen.identify

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.callerid.number.lookup.home.databinding.CellCountryBinding

class DialCountryAdapter(
    private val onClick: (DialCountry) -> Unit
) : RecyclerView.Adapter<DialCountryAdapter.VH>() {

    private val items = mutableListOf<DialCountry>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<DialCountry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: CellCountryBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onClick(items[p])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = CellCountryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        with(holder.binding) {
            lblFlag.text = DialCountries.flag(c.iso2)
            lblName.text = c.name
            lblDial.text = "+${c.dial}"
        }
    }

    override fun getItemCount(): Int = items.size
}
