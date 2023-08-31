package com.roy.gallery.pro.dialogs

import androidx.appcompat.app.AlertDialog
import com.roy.gallery.pro.R
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_other_aspect_ratio.view.*

class OtherAspectRatioDialog(val activity: BaseSimpleActivity, val lastOtherAspectRatio: Pair<Int, Int>?, val callback: (aspectRatio: Pair<Int, Int>) -> Unit) {
    private val dialog: AlertDialog

    init {
        val view = activity.layoutInflater.inflate(R.layout.dlg_other_aspect_ratio, null).apply {
            otherAspectRatio21.setOnClickListener { ratioPicked(Pair(2, 1)) }
            otherAspectRatio32.setOnClickListener { ratioPicked(Pair(3, 2)) }
            otherAspectRatio43.setOnClickListener { ratioPicked(Pair(4, 3)) }
            otherAspectRatio53.setOnClickListener { ratioPicked(Pair(5, 3)) }
            otherAspectRatio169.setOnClickListener { ratioPicked(Pair(16, 9)) }
            otherAspectRatio199.setOnClickListener { ratioPicked(Pair(19, 9)) }
            otherAspectRatioCustom.setOnClickListener { customRatioPicked() }

            otherAspectRatio12.setOnClickListener { ratioPicked(Pair(1, 2)) }
            otherAspectRatio23.setOnClickListener { ratioPicked(Pair(2, 3)) }
            otherAspectRatio34.setOnClickListener { ratioPicked(Pair(3, 4)) }
            otherAspectRatio35.setOnClickListener { ratioPicked(Pair(3, 5)) }
            otherAspectRatio916.setOnClickListener { ratioPicked(Pair(9, 16)) }
            otherAspectRatio919.setOnClickListener { ratioPicked(Pair(9, 19)) }

            val radio1SelectedItemId = when (lastOtherAspectRatio) {
                Pair(2, 1) -> otherAspectRatio21.id
                Pair(3, 2) -> otherAspectRatio32.id
                Pair(4, 3) -> otherAspectRatio43.id
                Pair(5, 3) -> otherAspectRatio53.id
                Pair(16, 9) -> otherAspectRatio169.id
                Pair(19, 9) -> otherAspectRatio199.id
                else -> 0
            }
            otherAspectRatioDialogRadio1.check(radio1SelectedItemId)

            val radio2SelectedItemId = when (lastOtherAspectRatio) {
                Pair(1, 2) -> otherAspectRatio12.id
                Pair(2, 3) -> otherAspectRatio23.id
                Pair(3, 4) -> otherAspectRatio34.id
                Pair(3, 5) -> otherAspectRatio35.id
                Pair(9, 16) -> otherAspectRatio916.id
                Pair(9, 19) -> otherAspectRatio919.id
                else -> 0
            }
            otherAspectRatioDialogRadio2.check(radio2SelectedItemId)

            if (radio1SelectedItemId == 0 && radio2SelectedItemId == 0) {
                otherAspectRatioDialogRadio1.check(otherAspectRatioCustom.id)
            }
        }

        dialog = AlertDialog.Builder(activity)
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
                    activity.setupDialogStuff(view, this)
                }
    }

    private fun customRatioPicked() {
        CustomAspectRatioDialog(activity, lastOtherAspectRatio) {
            callback(it)
            dialog.dismiss()
        }
    }

    private fun ratioPicked(pair: Pair<Int, Int>) {
        callback(pair)
        dialog.dismiss()
    }
}
