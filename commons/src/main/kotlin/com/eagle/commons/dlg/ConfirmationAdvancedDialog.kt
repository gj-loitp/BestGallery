package com.eagle.commons.dlg

import android.annotation.SuppressLint
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.eagle.commons.R
import com.eagle.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_message.view.*

// similar fo ConfirmationDialog, but has a callback for negative button too
@SuppressLint("InflateParams")
class ConfirmationAdvancedDialog(
    activity: Activity,
    message: String = "",
    messageId: Int = R.string.proceed_with_deletion,
    positive: Int = R.string.yes,
    negative: Int,
    val callback: (result: Boolean) -> Unit,
) {
    var dialog: AlertDialog

    init {
        val view = activity.layoutInflater.inflate(R.layout.dlg_message, null)
        view.message.text =
            message.ifEmpty { activity.resources.getString(messageId) }

        dialog = AlertDialog.Builder(activity)
            .setPositiveButton(positive) { _, _ -> positivePressed() }
            .setNegativeButton(negative) { _, _ -> negativePressed() }
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }

    private fun positivePressed() {
        dialog.dismiss()
        callback(true)
    }

    private fun negativePressed() {
        dialog.dismiss()
        callback(false)
    }
}
