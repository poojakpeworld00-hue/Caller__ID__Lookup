package com.callerid.number.lookup.home.shell.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.shell.entities.AppTile

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
            itemView.findViewById<TextView>(R.id.item_titleVw).text = launcher.title

            itemView.findViewById<ImageView>(R.id.item_iconVw).setImageDrawable(launcher.drawable)
            itemView.setOnClickListener { itemClick(launcher) }
        }
    }
}
