package com.roy.commons.dlg

import androidx.appcompat.app.AlertDialog
import com.google.android.material.internal.ViewUtils.showKeyboard
import com.roy.commons.R
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.beGone
import com.roy.commons.ext.getFilenameFromPath
import com.roy.commons.ext.getParentPath
import com.roy.commons.ext.humanizePath
import com.roy.commons.ext.isAValidFilename
import com.roy.commons.ext.renameFile
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.ext.toast
import com.roy.commons.ext.value
import kotlinx.android.synthetic.main.dlg_rename_item.view.*
import java.io.File
import java.util.*

class RenameItemDialog(
    val activity: BaseSimpleActivity,
    val path: String,
    val callback: (newPath: String) -> Unit,
) {
    init {
        val fullName = path.getFilenameFromPath()
        val dotAt = fullName.lastIndexOf(".")
        var name = fullName

        val view = activity.layoutInflater.inflate(R.layout.dlg_rename_item, null).apply {
            if (dotAt > 0 && !File(path).isDirectory) {
                name = fullName.substring(0, dotAt)
                val extension = fullName.substring(dotAt + 1)
                renameItemExtension.setText(extension)
            } else {
                renameItemExtensionLabel.beGone()
                renameItemExtension.beGone()
            }

            renameItemName.setText(name)
            renameItemPath.text = activity.humanizePath(path.getParentPath())
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.rename) {
                    showKeyboard(view.renameItemName)
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        var newName = view.renameItemName.value
                        val newExtension = view.renameItemExtension.value

                        if (newName.isEmpty()) {
                            activity.toast(R.string.empty_name)
                            return@setOnClickListener
                        }

                        if (!newName.isAValidFilename()) {
                            activity.toast(R.string.invalid_name)
                            return@setOnClickListener
                        }

                        val updatedPaths = ArrayList<String>()
                        updatedPaths.add(path)
                        if (newExtension.isNotEmpty()) {
                            newName += ".$newExtension"
                        }

                        if (!File(path).exists()) {
                            activity.toast(
                                String.format(
                                    activity.getString(R.string.source_file_doesnt_exist),
                                    path
                                )
                            )
                            return@setOnClickListener
                        }

                        val newPath = "${path.getParentPath()}/$newName"
                        if (File(newPath).exists()) {
                            activity.toast(R.string.name_taken)
                            return@setOnClickListener
                        }

                        updatedPaths.add(newPath)
                        activity.renameFile(path, newPath) {
                            if (it) {
                                callback(newPath)
                                dismiss()
                            } else {
                                activity.toast(R.string.unknown_error_occurred)
                            }
                        }
                    }
                }
            }
    }
}
