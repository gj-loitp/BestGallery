package com.eagle.commons.dialogs

import android.annotation.SuppressLint
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.eagle.commons.R
import com.eagle.commons.activities.BaseSimpleActivity
import com.eagle.commons.ext.*
import kotlinx.android.synthetic.main.dlg_create_new_folder.view.*
import java.io.File

@SuppressLint("InflateParams", "SetTextI18n")
class CreateNewFolderDialog(
    val activity: BaseSimpleActivity,
    val path: String,
    val callback: (path: String) -> Unit,
) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dlg_create_new_folder, null)
        view.folderPath.text = "${activity.humanizePath(path).trimEnd('/')}/"

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.create_new_folder) {
                    showKeyboard(view.folderName)
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(View.OnClickListener {
                        val name = view.folderName.value
                        when {
                            name.isEmpty() -> activity.toast(R.string.empty_name)
                            name.isAValidFilename() -> {
                                val file = File(path, name)
                                if (file.exists()) {
                                    activity.toast(R.string.name_taken)
                                    return@OnClickListener
                                }

                                createFolder("$path/$name", this)
                            }

                            else -> activity.toast(R.string.invalid_name)
                        }
                    })
                }
            }
    }

    private fun createFolder(path: String, alertDialog: AlertDialog) {
        try {
            when {
                activity.needsStupidWritePermissions(path) -> activity.handleSAFDialog(path) {
                    try {
                        val documentFile = activity.getDocumentFile(path.getParentPath())
                        if (documentFile?.createDirectory(path.getFilenameFromPath()) != null) {
                            sendSuccess(alertDialog, path)
                        } else {
                            activity.toast(R.string.unknown_error_occurred)
                        }
                    } catch (e: SecurityException) {
                        activity.showErrorToast(e)
                    }
                }

                File(path).mkdirs() -> sendSuccess(alertDialog, path)
                else -> activity.toast(R.string.unknown_error_occurred)
            }
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    private fun sendSuccess(alertDialog: AlertDialog, path: String) {
        callback(path.trimEnd('/'))
        alertDialog.dismiss()
    }
}
