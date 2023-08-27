package com.roy.commons.dlg

import android.annotation.SuppressLint
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.roy.commons.R
import com.roy.commons.ext.baseConfig
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.helpers.DATE_FORMAT_FOUR
import com.roy.commons.helpers.DATE_FORMAT_ONE
import com.roy.commons.helpers.DATE_FORMAT_THREE
import com.roy.commons.helpers.DATE_FORMAT_TWO
import kotlinx.android.synthetic.main.dlg_change_date_time_format.view.*

class ChangeDateTimeFormatDialog(val activity: Activity, val callback: () -> Unit) {
    @SuppressLint("InflateParams")
    val view = activity.layoutInflater.inflate(R.layout.dlg_change_date_time_format, null)!!

    init {
        view.apply {
            changeDateTimeDialogRadioOne.text = DATE_FORMAT_ONE
            changeDateTimeDialogRadioTwo.text = DATE_FORMAT_TWO
            changeDateTimeDialogRadioThree.text = DATE_FORMAT_THREE
            changeDateTimeDialogRadioFour.text = DATE_FORMAT_FOUR

            changeDateTimeDialog24Hour.isChecked = activity.baseConfig.use24HourFormat

            val formatButton = when (activity.baseConfig.dateFormat) {
                DATE_FORMAT_ONE -> changeDateTimeDialogRadioOne
                DATE_FORMAT_TWO -> changeDateTimeDialogRadioTwo
                DATE_FORMAT_THREE -> changeDateTimeDialogRadioThree
                else -> changeDateTimeDialogRadioFour
            }
            formatButton.isChecked = true
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }

    private fun dialogConfirmed() {
        activity.baseConfig.dateFormat =
            when (view.changeDateTimeDialogRadioGroup.checkedRadioButtonId) {
                R.id.changeDateTimeDialogRadioOne -> DATE_FORMAT_ONE
                R.id.changeDateTimeDialogRadioTwo -> DATE_FORMAT_TWO
                R.id.changeDateTimeDialogRadioThree -> DATE_FORMAT_THREE
                else -> DATE_FORMAT_FOUR
            }

        activity.baseConfig.use24HourFormat = view.changeDateTimeDialog24Hour.isChecked
        callback()
    }
}
