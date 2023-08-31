package com.roy.gallery.pro.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.roy.gallery.pro.R
import com.roy.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_delete_with_remember.view.*

class DeleteWithRememberDialog(
    val activity: Activity,
    val message: String,
    val callback: (remember: Boolean) -> Unit,
) {
    private var dialog: AlertDialog
    val view = activity.layoutInflater.inflate(R.layout.dlg_delete_with_remember, null)!!

    init {
        view.deleteRememberTitle.text = message
        val builder = AlertDialog.Builder(activity)
            .setPositiveButton(R.string.yes) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(R.string.no, null)

        dialog = builder.create().apply {
            activity.setupDialogStuff(view, this)
        }
    }

    private fun dialogConfirmed() {
        dialog.dismiss()
        callback(view.deleteRememberCheckBox.isChecked)
    }
}
