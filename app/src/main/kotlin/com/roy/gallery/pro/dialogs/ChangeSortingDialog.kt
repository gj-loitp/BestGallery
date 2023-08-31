package com.roy.gallery.pro.dialogs

import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.helpers.SHOW_ALL
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.helpers.SORT_BY_DATE_MODIFIED
import com.roy.commons.helpers.SORT_BY_DATE_TAKEN
import com.roy.commons.helpers.SORT_BY_NAME
import com.roy.commons.helpers.SORT_BY_PATH
import com.roy.commons.helpers.SORT_BY_RANDOM
import com.roy.commons.helpers.SORT_BY_SIZE
import com.roy.commons.helpers.SORT_DESCENDING
import kotlinx.android.synthetic.main.dlg_change_sorting.view.*

class ChangeSortingDialog(val activity: BaseSimpleActivity, val isDirectorySorting: Boolean, showFolderCheckbox: Boolean,
                          val path: String = "", val callback: () -> Unit) :
        DialogInterface.OnClickListener {
    private var currSorting = 0
    private var config = activity.config
    private var pathToUse = if (!isDirectorySorting && path.isEmpty()) SHOW_ALL else path
    private var view: View

    init {
        view = activity.layoutInflater.inflate(R.layout.dlg_change_sorting, null).apply {
            useForThisFolderDivider.beVisibleIf(showFolderCheckbox)
            sortingDialogUseForThisFolder.beVisibleIf(showFolderCheckbox)
            sortingDialogBottomNote.beVisibleIf(!isDirectorySorting)
            sortingDialogUseForThisFolder.isChecked = config.hasCustomSorting(pathToUse)
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, this)
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
                    activity.setupDialogStuff(view, this, R.string.sort_by)
                }

        currSorting = if (isDirectorySorting) config.directorySorting else config.getFileSorting(pathToUse)
        setupSortRadio()
        setupOrderRadio()
    }

    private fun setupSortRadio() {
        val sortingRadio = view.sortingDialogRadioSorting

        val sortBtn = when {
            currSorting and SORT_BY_PATH != 0 -> sortingRadio.sortingDialogRadioPath
            currSorting and SORT_BY_SIZE != 0 -> sortingRadio.sortingDialogRadioSize
            currSorting and SORT_BY_DATE_MODIFIED != 0 -> sortingRadio.sortingDialogRadioLastModified
            currSorting and SORT_BY_DATE_TAKEN != 0 -> sortingRadio.sortingDialogRadioDateTaken
            currSorting and SORT_BY_RANDOM != 0 -> sortingRadio.sortingDialogRadioRandom
            else -> sortingRadio.sortingDialogRadioName
        }
        sortBtn.isChecked = true
    }

    private fun setupOrderRadio() {
        val orderRadio = view.sortingDialogRadioOrder
        var orderBtn = orderRadio.sortingDialogRadioAscending

        if (currSorting and SORT_DESCENDING != 0) {
            orderBtn = orderRadio.sortingDialogRadioDescending
        }
        orderBtn.isChecked = true
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        val sortingRadio = view.sortingDialogRadioSorting
        var sorting = when (sortingRadio.checkedRadioButtonId) {
            R.id.sortingDialogRadioName -> SORT_BY_NAME
            R.id.sortingDialogRadioPath -> SORT_BY_PATH
            R.id.sortingDialogRadioSize -> SORT_BY_SIZE
            R.id.sortingDialogRadioLastModified -> SORT_BY_DATE_MODIFIED
            R.id.sortingDialogRadioRandom -> SORT_BY_RANDOM
            else -> SORT_BY_DATE_TAKEN
        }

        if (view.sortingDialogRadioOrder.checkedRadioButtonId == R.id.sortingDialogRadioDescending) {
            sorting = sorting or SORT_DESCENDING
        }

        if (isDirectorySorting) {
            config.directorySorting = sorting
        } else {
            if (view.sortingDialogUseForThisFolder.isChecked) {
                config.saveFileSorting(pathToUse, sorting)
            } else {
                config.removeFileSorting(pathToUse)
                config.sorting = sorting
            }
        }
        callback()
    }
}
