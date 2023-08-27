package com.eagle.commons.dialogs

import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.eagle.commons.R
import com.eagle.commons.ext.setupDialogStuff
import com.eagle.commons.models.Release
import kotlinx.android.synthetic.main.dlg_whats_new.view.*

class WhatsNewDialog(val activity: Activity, val releases: List<Release>) {
    init {
        val view = LayoutInflater.from(activity).inflate(R.layout.dlg_whats_new, null)
        view.whatsNewContent.text = getNewReleases()

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .create().apply {
                    activity.setupDialogStuff(view, this, R.string.whats_new)
                }
    }

    private fun getNewReleases(): String {
        val sb = StringBuilder()

        releases.forEach {
            val parts = activity.getString(it.textId).split("\n").map(String::trim)
            parts.forEach {
                sb.append("- $it\n")
            }
        }

        return sb.toString()
    }
}
