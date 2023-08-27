package com.eagle.commons.dialogs

import android.app.Activity
import android.text.Html
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AlertDialog
import com.eagle.commons.R
import com.eagle.commons.ext.launchPurchaseThankYouIntent
import com.eagle.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_purchase_thank_you.view.*

class PurchaseThankYouDialog(val activity: Activity) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dlg_purchase_thank_you, null).apply {
            purchaseThankYou.text = Html.fromHtml(activity.getString(R.string.purchase_thank_you))
            purchaseThankYou.movementMethod = LinkMovementMethod.getInstance()
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.purchase) { _, _ -> activity.launchPurchaseThankYouIntent() }
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }
}
