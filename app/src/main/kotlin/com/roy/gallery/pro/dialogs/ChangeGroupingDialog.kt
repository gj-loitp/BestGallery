package com.roy.gallery.pro.dialogs

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.helpers.*
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_change_grouping.view.*

class ChangeGroupingDialog(
    val activity: BaseSimpleActivity,
    val path: String = "",
    val callback: () -> Unit,
) :
    DialogInterface.OnClickListener {
    private var currGrouping = 0
    private var config = activity.config
    private val pathToUse = path.ifEmpty { SHOW_ALL }

    @SuppressLint("InflateParams")
    private var view: View =
        activity.layoutInflater.inflate(R.layout.dlg_change_grouping, null).apply {
            groupingDialogUseForThisFolder.isChecked = config.hasCustomGrouping(pathToUse)
            groupingDialogRadioFolder.beVisibleIf(path.isEmpty())
        }

    init {

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, this)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view = view, dialog = this, titleId = R.string.group_by)
            }

        currGrouping = config.getFolderGrouping(pathToUse)
        setupGroupRadio()
        setupOrderRadio()
    }

    private fun setupGroupRadio() {
        val groupingRadio = view.groupingDialogRadioGrouping

        val groupBtn = when {
            currGrouping and GROUP_BY_NONE != 0 -> groupingRadio.groupingDialogRadioNone
            currGrouping and GROUP_BY_LAST_MODIFIED != 0 -> groupingRadio.groupingDialogRadioLastModified
            currGrouping and GROUP_BY_DATE_TAKEN != 0 -> groupingRadio.groupingDialogRadioDateTaken
            currGrouping and GROUP_BY_FILE_TYPE != 0 -> groupingRadio.groupingDialogRadioFileType
            currGrouping and GROUP_BY_EXTENSION != 0 -> groupingRadio.groupingDialogRadioExtension
            else -> groupingRadio.groupingDialogRadioFolder
        }
        groupBtn.isChecked = true
    }

    private fun setupOrderRadio() {
        val orderRadio = view.groupingDialogRadioOrder
        var orderBtn = orderRadio.groupingDialogRadioAscending

        if (currGrouping and GROUP_DESCENDING != 0) {
            orderBtn = orderRadio.groupingDialogRadioDescending
        }
        orderBtn.isChecked = true
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        val groupingRadio = view.groupingDialogRadioGrouping
        var grouping = when (groupingRadio.checkedRadioButtonId) {
            R.id.groupingDialogRadioNone -> GROUP_BY_NONE
            R.id.groupingDialogRadioLastModified -> GROUP_BY_LAST_MODIFIED
            R.id.groupingDialogRadioDateTaken -> GROUP_BY_DATE_TAKEN
            R.id.groupingDialogRadioFileType -> GROUP_BY_FILE_TYPE
            R.id.groupingDialogRadioExtension -> GROUP_BY_EXTENSION
            else -> GROUP_BY_FOLDER
        }

        if (view.groupingDialogRadioOrder.checkedRadioButtonId == R.id.groupingDialogRadioDescending) {
            grouping = grouping or GROUP_DESCENDING
        }

        if (view.groupingDialogUseForThisFolder.isChecked) {
            config.saveFolderGrouping(path = pathToUse, value = grouping)
        } else {
            config.removeFolderGrouping(pathToUse)
            config.groupBy = grouping
        }
        callback()
    }
}
