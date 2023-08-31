package com.roy.gallery.pro.dialogs

import androidx.appcompat.app.AlertDialog
import com.roy.gallery.pro.R
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.dlg.ConfirmationDialog
import com.roy.commons.dlg.FilePickerDialog
import com.roy.commons.ext.getFilenameFromPath
import com.roy.commons.ext.getParentPath
import com.roy.commons.ext.humanizePath
import com.roy.commons.ext.isAValidFilename
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.ext.showKeyboard
import com.roy.commons.ext.toast
import com.roy.commons.ext.value
import kotlinx.android.synthetic.main.dlg_save_as.view.*
import java.io.File

class SaveAsDialog(val activity: BaseSimpleActivity, val path: String, val appendFilename: Boolean, val callback: (savePath: String) -> Unit) {

    init {
        var realPath = path.getParentPath()

        val view = activity.layoutInflater.inflate(R.layout.dlg_save_as, null).apply {
            saveAsPath.text = "${activity.humanizePath(realPath).trimEnd('/')}/"

            val fullName = path.getFilenameFromPath()
            val dotAt = fullName.lastIndexOf(".")
            var name = fullName

            if (dotAt > 0) {
                name = fullName.substring(0, dotAt)
                val extension = fullName.substring(dotAt + 1)
                saveAsExtension.setText(extension)
            }

            if (appendFilename) {
                name += "_1"
            }

            saveAsName.setText(name)
            saveAsPath.setOnClickListener {
                FilePickerDialog(activity, realPath, false, false, true, true) {
                    saveAsPath.text = activity.humanizePath(it)
                    realPath = it
                }
            }
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
                    activity.setupDialogStuff(view, this, R.string.save_as) {
                        showKeyboard(view.saveAsName)
                        getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val filename = view.saveAsName.value
                            val extension = view.saveAsExtension.value

                            if (filename.isEmpty()) {
                                activity.toast(R.string.filename_cannot_be_empty)
                                return@setOnClickListener
                            }

                            if (extension.isEmpty()) {
                                activity.toast(R.string.extension_cannot_be_empty)
                                return@setOnClickListener
                            }

                            val newFilename = "$filename.$extension"
                            val newPath = "${realPath.trimEnd('/')}/$newFilename"
                            if (!newFilename.isAValidFilename()) {
                                activity.toast(R.string.filename_invalid_characters)
                                return@setOnClickListener
                            }

                            if (File(newPath).exists()) {
                                val title = String.format(activity.getString(R.string.file_already_exists_overwrite), newFilename)
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
