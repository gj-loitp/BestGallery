package com.roy.commons.dlg

import android.annotation.SuppressLint
import androidx.appcompat.app.AlertDialog
import com.roy.commons.R
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.getFilenameFromPath
import com.roy.commons.ext.humanizePath
import com.roy.commons.ext.internalStoragePath
import com.roy.commons.ext.isAValidFilename
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.ext.toast
import com.roy.commons.ext.value
import kotlinx.android.synthetic.main.dlg_export_settings.view.*
import java.io.File

@SuppressLint("InflateParams")
class ExportSettingsDialog(
    val activity: BaseSimpleActivity,
    private val defaultFilename: String,
    callback: (path: String) -> Unit,
) {
    init {
        var folder = activity.internalStoragePath
        val view = activity.layoutInflater.inflate(R.layout.dlg_export_settings, null).apply {
            exportSettingsFileName.setText(defaultFilename)
            exportSettingsPath.text = activity.humanizePath(folder)
            exportSettingsPath.setOnClickListener {
                FilePickerDialog(activity, folder, false, showFAB = true) {
                    exportSettingsPath.text = activity.humanizePath(it)
                    folder = it
                }
            }
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.export_settings) {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = view.exportSettingsFileName.value
                        if (filename.isEmpty()) {
                            activity.toast(R.string.filename_cannot_be_empty)
                            return@setOnClickListener
                        }

                        val newPath = "${folder.trimEnd('/')}/$filename"
                        if (!newPath.getFilenameFromPath().isAValidFilename()) {
                            activity.toast(R.string.filename_invalid_characters)
                            return@setOnClickListener
                        }

                        if (File(newPath).exists()) {
                            val title = String.format(
                                activity.getString(R.string.file_already_exists_overwrite),
                                newPath.getFilenameFromPath()
                            )
                            ConfirmationDialog(activity, title) {
                                callback(newPath)
                                dismiss()
                            }
                        } else {
                            callback(newPath)
                            dismiss()
                        }
                    }
                }
            }
    }
}
