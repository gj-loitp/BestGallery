package com.roy.gallery.pro.dialogs

import androidx.appcompat.app.AlertDialog
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.helpers.*
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_filter_media.view.*

class FilterMediaDialog(val activity: BaseSimpleActivity, val callback: (result: Int) -> Unit) {
    private var view = activity.layoutInflater.inflate(R.layout.dlg_filter_media, null)

    init {
        val filterMedia = activity.config.filterMedia
        view.apply {
            filterMediaImages.isChecked = filterMedia and TYPE_IMAGES != 0
            filterMediaVideos.isChecked = filterMedia and TYPE_VIDEOS != 0
            filterMediaGifs.isChecked = filterMedia and TYPE_GIFS != 0
            filterMediaRaws.isChecked = filterMedia and TYPE_RAWS != 0
            filterMediaSvgs.isChecked = filterMedia and TYPE_SVGS != 0
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed() }
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
                    activity.setupDialogStuff(view, this, R.string.filter_media)
                }
    }

    private fun dialogConfirmed() {
        var result = 0
        if (view.filterMediaImages.isChecked)
            result += TYPE_IMAGES
        if (view.filterMediaVideos.isChecked)
            result += TYPE_VIDEOS
        if (view.filterMediaGifs.isChecked)
            result += TYPE_GIFS
        if (view.filterMediaRaws.isChecked)
            result += TYPE_RAWS
        if (view.filterMediaSvgs.isChecked)
            result += TYPE_SVGS

        activity.config.filterMedia = result
        callback(result)
    }
}
