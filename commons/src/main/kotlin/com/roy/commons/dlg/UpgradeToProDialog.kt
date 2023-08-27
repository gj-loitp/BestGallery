package com.roy.commons.dlg

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.roy.commons.R
import com.roy.commons.ext.launchUpgradeToProIntent
import com.roy.commons.ext.launchViewIntent
import com.roy.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_upgrade_to_pro.view.*

class UpgradeToProDialog(val activity: Activity) {

    init {
        val view = activity.layoutInflater.inflate(R.layout.dlg_upgrade_to_pro, null).apply {
            upgradeToPro.text = activity.getString(R.string.upgrade_to_pro_long)
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.upgrade) { _, _ -> upgradeApp() }
            .setNeutralButton(
                /* textId = */ R.string.more_info,
                /* listener = */ null
            )     // do not dismiss the dialog on pressing More Info
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this)
                getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    moreInfo()
                }
            }
    }

    private fun upgradeApp() {
        activity.launchUpgradeToProIntent()
    }

    private fun moreInfo() {
        activity.launchViewIntent("https://medium.com/@tibbi/some-simple-mobile-tools-apps-are-becoming-paid-d053268f0fb2")
    }
}
