package com.callerid.number.lookup.home.shell.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.fossify.commons.extensions.getProperTextColor
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.shell.screens.ShellBaseActivity
import com.callerid.number.lookup.home.databinding.CellWidgetListItemsHolderBinding
import com.callerid.number.lookup.home.databinding.CellWidgetListSectionBinding
import com.callerid.number.lookup.home.databinding.CellWidgetPreviewBinding
import com.callerid.number.lookup.home.shell.support.WIDGET_LIST_ITEMS_HOLDER
import com.callerid.number.lookup.home.shell.support.WIDGET_LIST_SECTION
import com.callerid.number.lookup.home.shell.contracts.WidgetPanelListener
import com.callerid.number.lookup.home.shell.entities.GadgetRow
import com.callerid.number.lookup.home.shell.entities.GadgetRowHolder
import com.callerid.number.lookup.home.shell.entities.GadgetSection

class GadgetAdapter(
    val activity: ShellBaseActivity,
    var widgetListItems: ArrayList<GadgetRow>,
    val widgetsFragmentListener: WidgetPanelListener,
    val itemClick: () -> Unit
) : RecyclerView.Adapter<GadgetAdapter.ViewHolder>() {

    private var textColor = activity.getProperTextColor()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = when (viewType) {
            WIDGET_LIST_SECTION -> CellWidgetListSectionBinding.inflate(inflater, parent, false)
            else -> CellWidgetListItemsHolderBinding.inflate(inflater, parent, false)
        }

        return ViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val widgetListItem = widgetListItems[position]
        holder.bindView(widgetListItems[position]) { itemView, layoutPosition ->
            when (widgetListItem) {
                is GadgetSection -> setupListSection(itemView, widgetListItem)
                is GadgetRowHolder -> setupListItemsHolder(itemView, widgetListItem)
            }
        }
    }

    override fun getItemCount() = widgetListItems.size

    override fun getItemViewType(position: Int) = when {
        widgetListItems[position] is GadgetSection -> WIDGET_LIST_SECTION
        else -> WIDGET_LIST_ITEMS_HOLDER
    }

    private fun setupListSection(view: View, section: GadgetSection) {
        CellWidgetListSectionBinding.bind(view).apply {
            widgetAppTitle.text = section.appTitle
            widgetAppTitle.setTextColor(textColor)
            widgetAppIcon.setImageDrawable(section.appIcon)
        }
    }

    private fun setupListItemsHolder(view: View, listItem: GadgetRowHolder) {
        val binding = CellWidgetListItemsHolderBinding.bind(view)
        binding.widgetListItemsHolder.removeAllViews()
        binding.widgetListItemsScrollView.scrollX = 0
        listItem.widgets.forEachIndexed { index, widget ->
            val imageSize = activity.resources.getDimension(R.dimen.widget_preview_size).toInt()
            val widgetPreview = CellWidgetPreviewBinding.inflate(LayoutInflater.from(activity))
            binding.widgetListItemsHolder.addView(widgetPreview.root)

            val endMargin = if (index == listItem.widgets.size - 1) {
                activity.resources.getDimension(org.fossify.commons.R.dimen.medium_margin).toInt()
            } else {
                0
            }

            widgetPreview.widgetTitle.apply {
                text = widget.widgetTitle
                setTextColor(textColor)
            }

            widgetPreview.widgetSize.apply {
                text = if (widget.isShortcut) {
                    activity.getString(org.fossify.commons.R.string.shortcut)
                } else {
                    "${widget.widthCells} x ${widget.heightCells}"
                }
                setTextColor(textColor)
            }

            (widgetPreview.widgetImage.layoutParams as RelativeLayout.LayoutParams).apply {
                marginStart = activity.resources.getDimension(org.fossify.commons.R.dimen.activity_margin).toInt()
                marginEnd = endMargin
                width = imageSize
                height = imageSize
            }

            Glide.with(activity)
                .load(widget.widgetPreviewImage)
                .into(widgetPreview.widgetImage)

            widgetPreview.root.setOnClickListener { itemClick() }

            widgetPreview.root.setOnLongClickListener { view ->
                widgetsFragmentListener.onWidgetLongPressed(widget)
                true
            }
        }
    }

    fun updateItems(newItems: ArrayList<GadgetRow>) {
        val oldSum = widgetListItems.sumOf { it.getHashToCompare() }
        val newSum = newItems.sumOf { it.getHashToCompare() }
        if (oldSum != newSum) {
            widgetListItems = newItems
            notifyDataSetChanged()
        }
    }

    fun updateTextColor(newTextColor: Int) {
        if (newTextColor != textColor) {
            textColor = newTextColor
            notifyDataSetChanged()
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(widgetListItem: GadgetRow, callback: (itemView: View, adapterPosition: Int) -> Unit) {
            itemView.apply {
                callback(this, adapterPosition)
            }
        }
    }
}
