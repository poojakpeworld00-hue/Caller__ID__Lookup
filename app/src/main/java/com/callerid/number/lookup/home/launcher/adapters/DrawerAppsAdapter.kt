package com.callerid.number.lookup.home.launcher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.launcher.models.AppTile

/**
 * Minimal launcher list used by the left panel, one instance per section. The row layouts all
 * share the same two ids, so a single adapter covers grids and lists alike.
 */
class DrawerAppsAdapter(
    private val layoutRes: Int,
    private val itemClick: (AppTile) -> Unit,
) : ListAdapter<AppTile, DrawerAppsAdapter.ViewHolder>(AppTileDiff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(launcher: AppTile) {
            itemView.findViewById<TextView>(R.id.item_title).text = launcher.title
            // the icons are already loaded in memory by the drawer, no need for Glide here
            itemView.findViewById<ImageView>(R.id.item_icon).setImageDrawable(launcher.drawable)
            itemView.setOnClickListener { itemClick(launcher) }
        }
    }
}
