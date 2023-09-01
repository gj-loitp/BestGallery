package com.roy.gallery.pro.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.config
import com.roy.commons.dlg.FilePickerDialog
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.scanPathRecursively
import com.roy.commons.itf.RefreshRecyclerViewListener
import kotlinx.android.synthetic.main.a_manage_folders.*

class IncludedFoldersActivity : SimpleActivity(),
    RefreshRecyclerViewListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.a_manage_folders)
        updateFolders()
    }

    private fun updateFolders() {
        val folders = ArrayList<String>()
        config.includedFolders.mapTo(folders) { it }
        manageFoldersPlaceHolder.apply {
            text = getString(R.string.included_activity_placeholder)
            beVisibleIf(folders.isEmpty())
            setTextColor(config.textColor)
        }

        val adapter = com.roy.gallery.pro.adapters.ManageFoldersAdapter(this, folders, false, this, manageFoldersList) {}
        manageFoldersList.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_add_folder, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.addFolder -> addFolder()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun refreshItems() {
        updateFolders()
    }

    private fun addFolder() {
        FilePickerDialog(this, config.lastFilepickerPath, false, config.shouldShowHidden, false, true) {
            config.lastFilepickerPath = it
            config.addIncludedFolder(it)
            updateFolders()
            Thread {
                scanPathRecursively(it)
            }.start()
        }
    }
}
