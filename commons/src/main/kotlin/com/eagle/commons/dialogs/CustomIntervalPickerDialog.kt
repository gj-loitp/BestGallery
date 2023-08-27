package com.eagle.commons.dialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.eagle.commons.R
import com.eagle.commons.ext.*
import com.eagle.commons.helpers.DAY_SECONDS
import com.eagle.commons.helpers.HOUR_SECONDS
import com.eagle.commons.helpers.MINUTE_SECONDS
import kotlinx.android.synthetic.main.dlg_custom_interval_picker.view.*

class CustomIntervalPickerDialog(
    val activity: Activity,
    private val selectedSeconds: Int = 0,
    val showSeconds: Boolean = false,
    val callback: (minutes: Int) -> Unit,
) {
    var dialog: AlertDialog

    @SuppressLint("InflateParams")
    var view =
        (activity.layoutInflater.inflate(R.layout.dlg_custom_interval_picker, null) as ViewGroup)

    init {
        view.apply {
            dlgRadioSeconds.beVisibleIf(showSeconds)
            when {
                selectedSeconds == 0 -> dlgRadioView.check(R.id.dlgRadioMinutes)
                selectedSeconds % DAY_SECONDS == 0 -> {
                    dlgRadioView.check(R.id.dialogRadioDays)
                    dlgCustomIntervalValue.setText((selectedSeconds / DAY_SECONDS).toString())
                }

                selectedSeconds % HOUR_SECONDS == 0 -> {
                    dlgRadioView.check(R.id.dialogRadioHours)
                    dlgCustomIntervalValue.setText((selectedSeconds / HOUR_SECONDS).toString())
                }

                selectedSeconds % MINUTE_SECONDS == 0 -> {
                    dlgRadioView.check(R.id.dlgRadioMinutes)
                    dlgCustomIntervalValue.setText((selectedSeconds / MINUTE_SECONDS).toString())
                }

                else -> {
                    dlgRadioView.check(R.id.dlgRadioSeconds)
                    dlgCustomIntervalValue.setText(selectedSeconds.toString())
                }
            }
        }

        dialog = AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { _, _ -> confirmReminder() }
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this) {
                    showKeyboard(view.dlgCustomIntervalValue)
                }
            }
    }

    private fun confirmReminder() {
        val value = view.dlgCustomIntervalValue.value
        val multiplier = getMultiplier(view.dlgRadioView.checkedRadioButtonId)
        val minutes = Integer.valueOf(value.ifEmpty { "0" })
        callback(minutes * multiplier)
        activity.hideKeyboard()
        dialog.dismiss()
    }

    private fun getMultiplier(id: Int) = when (id) {
        R.id.dialogRadioDays -> DAY_SECONDS
        R.id.dialogRadioHours -> HOUR_SECONDS
        R.id.dlgRadioMinutes -> MINUTE_SECONDS
        else -> 1
    }
}
