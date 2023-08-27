package com.eagle.gallery.pro.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.eagle.gallery.pro.R
import com.eagle.gallery.pro.adapters.ManageHiddenFoldersAdapter
import com.eagle.gallery.pro.extensions.addNoMedia
import com.eagle.gallery.pro.extensions.config
import com.eagle.gallery.pro.extensions.getNoMediaFolders
import com.roy.commons.dlg.FilePickerDialog
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.itf.RefreshRecyclerViewListener
import kotlinx.android.synthetic.main.activity_manage_folders.*

class HiddenFoldersActivity : SimpleActivity(),
    RefreshRecyclerViewListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_folders)
        updateFolders()
    }

    private fun updateFolders() {
        getNoMediaFolders {
            runOnUiThread {
                manage_folders_placeholder.apply {
                    text = getString(R.string.hidden_folders_placeholder)
                    beVisibleIf(it.isEmpty())
                    setTextColor(config.textColor)
                }

                val adapter = ManageHiddenFoldersAdapter(this, it, this, manage_folders_list) {}
                manage_folders_list.adapter = adapter
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_add_folder, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.add_folder -> addFolder()
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
            Thread {
                addNoMedia(it) {
                    updateFolders()
                }
            }.start()
        }
    }
}
