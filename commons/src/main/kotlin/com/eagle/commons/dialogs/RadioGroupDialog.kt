package com.eagle.commons.dialogs

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.eagle.commons.R
import com.eagle.commons.ext.onGlobalLayout
import com.eagle.commons.ext.setupDialogStuff
import com.eagle.commons.models.RadioItem
import kotlinx.android.synthetic.main.dlg_radio_group.view.*
import java.util.*

class RadioGroupDialog(
    val activity: Activity,
    val items: ArrayList<RadioItem>,
    val checkedItemId: Int = -1,
    private val titleId: Int = 0,
    showOKButton: Boolean = false,
    val cancelCallback: (() -> Unit)? = null,
    val callback: (newValue: Any) -> Unit,
) {
    private val dialog: AlertDialog
    private var wasInit = false
    private var selectedItemId = -1

    init {
        val view = activity.layoutInflater.inflate(R.layout.dlg_radio_group, null)
        view.dialogRadioGroup.apply {
            for (i in 0 until items.size) {
                val radioButton = (activity.layoutInflater.inflate(
                    /* resource = */ R.layout.v_radio_button,
                    /* root = */ null
                ) as RadioButton).apply {
                    text = items[i].title
                    isChecked = items[i].id == checkedItemId
                    id = i
                    setOnClickListener { itemSelected(i) }
                }

                if (items[i].id == checkedItemId) {
                    selectedItemId = i
                }

                addView(
                    radioButton,
                    RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        val builder = AlertDialog.Builder(activity)
            .setOnCancelListener { cancelCallback?.invoke() }

        if (selectedItemId != -1 && showOKButton) {
            builder.setPositiveButton(R.string.ok) { dialog, which -> itemSelected(selectedItemId) }
        }

        dialog = builder.create().apply {
            activity.setupDialogStuff(view, this, titleId)
        }

        if (selectedItemId != -1) {
            view.dialogRadioHolder.apply {
                onGlobalLayout {
                    scrollY =
                        view.dialogRadioGroup.findViewById<View>(selectedItemId).bottom - height
                }
            }
        }

        wasInit = true
    }

    private fun itemSelected(checkedId: Int) {
        if (wasInit) {
            callback(items[checkedId].value)
            dialog.dismiss()
        }
    }
}
