package com.roy.gallery.pro.dialogs

import androidx.appcompat.app.AlertDialog
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.helpers.*
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_manage_extended_details.view.*

class ManageExtendedDetailsDialog(val activity: BaseSimpleActivity, val callback: (result: Int) -> Unit) {
    private var view = activity.layoutInflater.inflate(R.layout.dlg_manage_extended_details, null)

    init {
        val details = activity.config.extendedDetails
        view.apply {
            manageExtendedDetailsName.isChecked = details and EXT_NAME != 0
            manageExtendedDetailsPath.isChecked = details and EXT_PATH != 0
            manageExtendedDetailsSize.isChecked = details and EXT_SIZE != 0
            manageExtendedDetailsResolution.isChecked = details and EXT_RESOLUTION != 0
            manageExtendedDetailsLastModified.isChecked = details and EXT_LAST_MODIFIED != 0
            manageExtendedDetailsDateTaken.isChecked = details and EXT_DATE_TAKEN != 0
            manageExtendedDetailsCamera.isChecked = details and EXT_CAMERA_MODEL != 0
            manageExtendedDetailsExif.isChecked = details and EXT_EXIF_PROPERTIES != 0
            manageExtendedDetailsDuration.isChecked = details and EXT_DURATION != 0
            manageExtendedDetailsArtist.isChecked = details and EXT_ARTIST != 0
            manageExtendedDetailsAlbum.isChecked = details and EXT_ALBUM != 0
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed() }
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
                    activity.setupDialogStuff(view, this)
                }
    }

    private fun dialogConfirmed() {
        var result = 0
        view.apply {
            if (manageExtendedDetailsName.isChecked)
                result += EXT_NAME
            if (manageExtendedDetailsPath.isChecked)
                result += EXT_PATH
            if (manageExtendedDetailsSize.isChecked)
                result += EXT_SIZE
            if (manageExtendedDetailsResolution.isChecked)
                result += EXT_RESOLUTION
            if (manageExtendedDetailsLastModified.isChecked)
                result += EXT_LAST_MODIFIED
            if (manageExtendedDetailsDateTaken.isChecked)
                result += EXT_DATE_TAKEN
            if (manageExtendedDetailsCamera.isChecked)
                result += EXT_CAMERA_MODEL
            if (manageExtendedDetailsExif.isChecked)
                result += EXT_EXIF_PROPERTIES
            if (manageExtendedDetailsDuration.isChecked)
                result += EXT_DURATION
            if (manageExtendedDetailsArtist.isChecked)
                result += EXT_ARTIST
            if (manageExtendedDetailsAlbum.isChecked)
                result += EXT_ALBUM
        }

        activity.config.extendedDetails = result
        callback(result)
    }
}
