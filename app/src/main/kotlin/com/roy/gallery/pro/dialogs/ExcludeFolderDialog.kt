package com.roy.gallery.pro.dialogs

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.config
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.getBasePath
import com.roy.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_exclude_folder.view.*

@SuppressLint("InflateParams")
class ExcludeFolderDialog(
    val activity: BaseSimpleActivity,
    private val selectedPaths: List<String>,
    val callback: () -> Unit,
) {
    private val alternativePaths = getAlternativePathsList()
    private var radioGroup: RadioGroup? = null

    init {
        val view = activity.layoutInflater.inflate(R.layout.dlg_exclude_folder, null).apply {
            excludeFolderParent.beVisibleIf(alternativePaths.size > 1)

            radioGroup = excludeFolderRadioGroup
            excludeFolderRadioGroup.beVisibleIf(alternativePaths.size > 1)
        }

        alternativePaths.forEachIndexed { index, _ ->
            val radioButton = (activity.layoutInflater.inflate(
                R.layout.v_radio_button,
                null
            ) as RadioButton).apply {
                text = alternativePaths[index]
                isChecked = index == 0
                id = index
            }
            radioGroup?.addView(
                /* child = */ radioButton,
                /* params = */ RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }

    private fun dialogConfirmed() {
        val path =
            if (alternativePaths.isEmpty()) selectedPaths[0] else alternativePaths[radioGroup!!.checkedRadioButtonId]
        activity.config.addExcludedFolder(path)
        callback()
    }

    private fun getAlternativePathsList(): List<String> {
        val pathsList = ArrayList<String>()
        if (selectedPaths.size > 1)
            return pathsList

        val path = selectedPaths[0]
        var basePath = path.getBasePath(activity)
        val relativePath = path.substring(basePath.length)
        val parts = relativePath.split("/").filter(String::isNotEmpty)
        if (parts.isEmpty())
            return pathsList

        pathsList.add(basePath)
        if (basePath == "/")
            basePath = ""

        for (part in parts) {
            basePath += "/$part"
            pathsList.add(basePath)
        }

        return pathsList.reversed()
    }
}
