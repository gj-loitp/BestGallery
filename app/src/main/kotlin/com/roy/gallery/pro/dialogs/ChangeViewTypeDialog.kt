package com.roy.gallery.pro.dialogs

import android.view.View
import androidx.appcompat.app.AlertDialog
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.helpers.SHOW_ALL
import com.roy.gallery.pro.helpers.VIEW_TYPE_GRID
import com.roy.gallery.pro.helpers.VIEW_TYPE_LIST
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_change_view_type.view.*

class ChangeViewTypeDialog(val activity: BaseSimpleActivity, val fromFoldersView: Boolean, val path: String = "", val callback: () -> Unit) {
    private var view: View
    private var config = activity.config
    private var pathToUse = if (path.isEmpty()) SHOW_ALL else path

    init {
        view = activity.layoutInflater.inflate(R.layout.dlg_change_view_type, null).apply {
            val viewToCheck = if (fromFoldersView) {
                if (config.viewTypeFolders == VIEW_TYPE_GRID) {
                    changeViewTypeDialogRadioGrid.id
                } else {
                    changeViewTypeDialogRadioList.id
                }
            } else {
                val currViewType = config.getFolderViewType(pathToUse)
                if (currViewType == VIEW_TYPE_GRID) {
                    changeViewTypeDialogRadioGrid.id
                } else {
                    changeViewTypeDialogRadioList.id
                }
            }

            changeViewTypeDialogRadio.check(viewToCheck)
            changeViewTypeDialogGroupDirectSubfolders.apply {
                beVisibleIf(fromFoldersView)
                isChecked = config.groupDirectSubfolders
            }

            changeViewTypeDialogUseForThisFolder.apply {
                beVisibleIf(!fromFoldersView)
                isChecked = config.hasCustomViewType(pathToUse)
            }
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed() }
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
                    activity.setupDialogStuff(view, this)
                }
    }

    private fun dialogConfirmed() {
        val viewType = if (view.changeViewTypeDialogRadio.checkedRadioButtonId == view.changeViewTypeDialogRadioGrid.id) VIEW_TYPE_GRID else VIEW_TYPE_LIST
        if (fromFoldersView) {
            config.viewTypeFolders = viewType
            config.groupDirectSubfolders = view.changeViewTypeDialogGroupDirectSubfolders.isChecked
        } else {
            if (view.changeViewTypeDialogUseForThisFolder.isChecked) {
                config.saveFolderViewType(pathToUse, viewType)
            } else {
                config.removeFolderViewType(pathToUse)
                config.viewTypeFiles = viewType
            }
        }

        callback()
    }
}
