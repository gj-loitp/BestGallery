package com.eagle.commons.dialogs

import android.app.Activity
import android.text.Html
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AlertDialog
import com.eagle.commons.R
import com.eagle.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_textview.view.*

class NewAppDialog(val activity: Activity, val packageName: String, val title: String) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dlg_textview, null).apply {
            val text = String.format(activity.getString(R.string.new_app), "https://play.google.com/store/apps/details?id=$packageName", title)
            textView.text = Html.fromHtml(text)
            textView.movementMethod = LinkMovementMethod.getInstance()
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .create().apply {
                    activity.setupDialogStuff(view, this)
                }
    }
}
