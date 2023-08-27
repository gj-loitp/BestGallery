package com.roy.gallery.pro.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.roy.gallery.pro.R
import com.roy.gallery.pro.models.FilterItem
import kotlinx.android.synthetic.main.v_editor_filter_item.view.*
import java.util.*

class FiltersAdapter(val context: Context, val filterItems: ArrayList<FilterItem>, val itemClick: (Int) -> Unit) : RecyclerView.Adapter<com.roy.gallery.pro.adapters.FiltersAdapter.ViewHolder>() {

    private var currentSelection = filterItems.first()
    private var strokeBackground = context.resources.getDrawable(R.drawable.stroke_background)

    override fun onBindViewHolder(holder: com.roy.gallery.pro.adapters.FiltersAdapter.ViewHolder, position: Int) {
        holder.bindView(filterItems[position])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): com.roy.gallery.pro.adapters.FiltersAdapter.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.v_editor_filter_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filterItems.size

    fun getCurrentFilter() = currentSelection

    private fun setCurrentFilter(position: Int) {
        val filterItem = filterItems.getOrNull(position) ?: return
        if (currentSelection != filterItem) {
            currentSelection = filterItem
            notifyDataSetChanged()
            itemClick.invoke(position)
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(filterItem: FilterItem): View {
            itemView.apply {
                editorFilterItemLabel.text = filterItem.filter.name
                editorFilterItemThumbnail.setImageBitmap(filterItem.bitmap)
                editorFilterItemBg.background = if (getCurrentFilter() == filterItem) {
                    strokeBackground
                } else {
                    null
                }

                setOnClickListener {
                    setCurrentFilter(adapterPosition)
                }
            }
            return itemView
        }
    }
}
