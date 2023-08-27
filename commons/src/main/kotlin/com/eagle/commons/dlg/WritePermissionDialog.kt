package com.eagle.commons.dlg

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.eagle.commons.R
import com.eagle.commons.activities.BaseSimpleActivity
import com.eagle.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_write_permission.view.*
import kotlinx.android.synthetic.main.dlg_write_permission_otg.view.*

class WritePermissionDialog(activity: Activity, private val isOTG: Boolean, val callback: () -> Unit) {
    var dialog: AlertDialog

    init {
        val layout = if (isOTG) R.layout.dlg_write_permission_otg else R.layout.dlg_write_permission
        val view = activity.layoutInflater.inflate(layout, null)

        val glide = Glide.with(activity)
        val crossFade = DrawableTransitionOptions.withCrossFade()
        if (isOTG) {
            glide.load(R.drawable.img_write_storage_otg).transition(crossFade)
                .into(view.writePermissionsDialogOtgImage)
        } else {
            glide.load(R.drawable.img_write_storage).transition(crossFade)
                .into(view.writePermissionsDialogImage)
            glide.load(R.drawable.img_write_storage_sd).transition(crossFade)
                .into(view.writePermissionsDialogImageSd)
        }

        dialog = AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { _, _ -> dialogConfirmed() }
            .setOnCancelListener { BaseSimpleActivity.funAfterSAFPermission = null }
            .create().apply {
                activity.setupDialogStuff(
                    view = view,
                    dialog = this,
                    titleId = R.string.confirm_storage_access_title
                )
            }
    }

    private fun dialogConfirmed() {
        dialog.dismiss()
        callback()
    }
}
