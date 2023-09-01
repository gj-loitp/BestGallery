package com.roy.gallery.pro.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.config
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.adt.MyRecyclerViewAdapter
import com.roy.commons.itf.RefreshRecyclerViewListener
import com.roy.commons.views.MyRecyclerView
import kotlinx.android.synthetic.main.v_item_manage_folder.view.*
import java.util.*

class ManageFoldersAdapter(
    activity: BaseSimpleActivity,
    var folders: ArrayList<String>,
    private val isShowingExcludedFolders: Boolean,
    val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewAdapter(
    activity = activity,
    recyclerView = recyclerView,
    fastScroller = null,
    itemClick = itemClick
) {

    private val config = activity.config

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.menu_cab_remove_only

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_remove -> removeSelection()
        }
    }

    override fun getSelectableItemCount() = folders.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = folders.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = folders.indexOfFirst { it.hashCode() == key }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        createViewHolder(R.layout.v_item_manage_folder, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        holder.bindView(
            any = folder,
            allowSingleClick = true,
            allowLongClick = true
        ) { itemView, _ ->
            setupView(itemView, folder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = folders.size

    private fun getSelectedItems() =
        folders.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<String>

    private fun setupView(view: View, folder: String) {
        view.apply {
            manageFolderHolder?.isSelected = selectedKeys.contains(folder.hashCode())
            manageFolderTitle.apply {
                text = folder
                setTextColor(config.textColor)
            }
        }
    }

    private fun removeSelection() {
        val removeFolders = ArrayList<String>(selectedKeys.size)
        val positions = getSelectedItemPositions()

        getSelectedItems().forEach {
            removeFolders.add(it)
            if (isShowingExcludedFolders) {
                config.removeExcludedFolder(it)
            } else {
                config.removeIncludedFolder(it)
            }
        }

        folders.removeAll(removeFolders)
        removeSelectedItems(positions)
        if (folders.isEmpty()) {
            listener?.refreshItems()
        }
    }
}
