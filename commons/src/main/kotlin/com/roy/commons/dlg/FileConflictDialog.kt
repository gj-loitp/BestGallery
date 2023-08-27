package com.roy.commons.dlg

import android.annotation.SuppressLint
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.roy.commons.R
import com.roy.commons.ext.baseConfig
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.helpers.CONFLICT_KEEP_BOTH
import com.roy.commons.helpers.CONFLICT_MERGE
import com.roy.commons.helpers.CONFLICT_OVERWRITE
import com.roy.commons.helpers.CONFLICT_SKIP
import com.roy.commons.models.FileDirItem
import kotlinx.android.synthetic.main.dlg_file_conflict.view.*

class FileConflictDialog(
    val activity: Activity,
    val fileDirItem: FileDirItem,
    val callback: (resolution: Int, applyForAll: Boolean) -> Unit,
) {
    @SuppressLint("InflateParams")
    val view = activity.layoutInflater.inflate(R.layout.dlg_file_conflict, null)!!

    init {
        view.apply {
            val stringBase =
                if (fileDirItem.isDirectory) R.string.folder_already_exists else R.string.file_already_exists
            conflictDialogTitle.text =
                String.format(activity.getString(stringBase), fileDirItem.name)
            conflictDialogApplyToAll.isChecked = activity.baseConfig.lastConflictApplyToAll
            conflictDialogRadioMerge.beVisibleIf(fileDirItem.isDirectory)

            val resolutionButton = when (activity.baseConfig.lastConflictResolution) {
                CONFLICT_OVERWRITE -> conflictDialogRadioOverwrite
                CONFLICT_MERGE -> conflictDialogRadioMerge
                else -> conflictDialogRadioSkip
            }
            resolutionButton.isChecked = true
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }

    private fun dialogConfirmed() {
        val resolution = when (view.conflictDialogRadioGroup.checkedRadioButtonId) {
            R.id.conflictDialogRadioSkip -> CONFLICT_SKIP
            R.id.conflictDialogRadioMerge -> CONFLICT_MERGE
            R.id.conflictDialogRadioKeepBoth -> CONFLICT_KEEP_BOTH
            else -> CONFLICT_OVERWRITE
        }

        val applyToAll = view.conflictDialogApplyToAll.isChecked
        activity.baseConfig.apply {
            lastConflictApplyToAll = applyToAll
            lastConflictResolution = resolution
        }

        callback(resolution, applyToAll)
    }
}
