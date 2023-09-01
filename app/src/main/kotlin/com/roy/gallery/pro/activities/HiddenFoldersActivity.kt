package com.roy.gallery.pro.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.roy.gallery.pro.R
import com.roy.gallery.pro.adapters.ManageHiddenFoldersAdapter
import com.roy.gallery.pro.extensions.addNoMedia
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.extensions.getNoMediaFolders
import com.roy.commons.dlg.FilePickerDialog
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.itf.RefreshRecyclerViewListener
import kotlinx.android.synthetic.main.a_manage_folders.*

class HiddenFoldersActivity : SimpleActivity(),
    RefreshRecyclerViewListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.a_manage_folders)
        updateFolders()
    }

    private fun updateFolders() {
        getNoMediaFolders {
            runOnUiThread {
                manageFoldersPlaceHolder.apply {
                    text = getString(R.string.hidden_folders_placeholder)
                    beVisibleIf(it.isEmpty())
                    setTextColor(config.textColor)
                }

                val adapter = ManageHiddenFoldersAdapter(
                    activity = this,
                    folders = it,
                    listener = this,
                    recyclerView = manageFoldersList
                ) {}
                manageFoldersList.adapter = adapter
            }
        }
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
        FilePickerDialog(
            activity = this,
            currPath = config.lastFilepickerPath,
            pickFile = false,
            showHidden = config.shouldShowHidden,
            showFAB = false,
            canAddShowHiddenButton = true
        ) {
            config.lastFilepickerPath = it
            Thread {
                addNoMedia(it) {
                    updateFolders()
                }
            }.start()
        }
    }
}
