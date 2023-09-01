package com.roy.gallery.pro.dialogs

import android.annotation.SuppressLint
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.roy.gallery.pro.R
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.ext.showKeyboard
import com.roy.commons.ext.value
import kotlinx.android.synthetic.main.dlg_custom_aspect_ratio.view.*

@SuppressLint("InflateParams")
class CustomAspectRatioDialog(
    val activity: BaseSimpleActivity,
    private val defaultCustomAspectRatio: Pair<Int, Int>?,
    val callback: (aspectRatio: Pair<Int, Int>) -> Unit,
) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dlg_custom_aspect_ratio, null).apply {
            aspectRatioWidth.setText(defaultCustomAspectRatio?.first?.toString() ?: "")
            aspectRatioHeight.setText(defaultCustomAspectRatio?.second?.toString() ?: "")
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this) {
                    showKeyboard(view.aspectRatioWidth)
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val width = getViewValue(view.aspectRatioWidth)
                        val height = getViewValue(view.aspectRatioHeight)
                        callback(Pair(width, height))
                        dismiss()
                    }
                }
            }
    }

    private fun getViewValue(view: EditText): Int {
        val textValue = view.value
        return if (textValue.isEmpty()) 0 else textValue.toInt()
    }
}
