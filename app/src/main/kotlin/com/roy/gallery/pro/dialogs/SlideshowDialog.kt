package com.roy.gallery.pro.dialogs

import android.annotation.SuppressLint
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.helpers.SLIDESHOW_DEFAULT_INTERVAL
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.hideKeyboard
import com.roy.commons.ext.setupDialogStuff
import kotlinx.android.synthetic.main.dlg_slideshow.view.*

class SlideshowDialog(val activity: BaseSimpleActivity, val callback: () -> Unit) {
    @SuppressLint("InflateParams")
    val view: View = activity.layoutInflater.inflate(R.layout.dlg_slideshow, null).apply {
        intervalValue.setOnClickListener {
            val text = intervalValue.text
            if (text?.isNotEmpty() == true) {
                text.replace(0, 1, text.subSequence(0, 1), 0, 1)
                intervalValue.selectAll()
            }
        }

        intervalValue.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus)
                activity.hideKeyboard(v)
        }

        includeVideosHolder.setOnClickListener {
            intervalValue.clearFocus()
            includeVideos.toggle()
        }

        includeGifsHolder.setOnClickListener {
            intervalValue.clearFocus()
            includeGifs.toggle()
        }

        randomOrderHolder.setOnClickListener {
            intervalValue.clearFocus()
            randomOrder.toggle()
        }

        useFadeHolder.setOnClickListener {
            intervalValue.clearFocus()
            use_fade.toggle()
        }

        moveBackwardsHolder.setOnClickListener {
            intervalValue.clearFocus()
            moveBackwards.toggle()
        }

        loopSlideshowHolder.setOnClickListener {
            intervalValue.clearFocus()
            loopSlideshow.toggle()
        }
    }

    init {
        setupValues()

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this) {
                    hideKeyboard()
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        storeValues()
                        callback()
                        dismiss()
                    }
                }
            }
    }

    private fun setupValues() {
        val config = activity.config
        view.apply {
            intervalValue.setText(config.slideshowInterval.toString())
            includeVideos.isChecked = config.slideshowIncludeVideos
            includeGifs.isChecked = config.slideshowIncludeGIFs
            randomOrder.isChecked = config.slideshowRandomOrder
            use_fade.isChecked = config.slideshowUseFade
            moveBackwards.isChecked = config.slideshowMoveBackwards
            loopSlideshow.isChecked = config.loopSlideshow
        }
    }

    private fun storeValues() {
        var interval = view.intervalValue.text.toString()
        if (interval.trim('0').isEmpty())
            interval = SLIDESHOW_DEFAULT_INTERVAL.toString()

        activity.config.apply {
            slideshowInterval = interval.toInt()
            slideshowIncludeVideos = view.includeVideos.isChecked
            slideshowIncludeGIFs = view.includeGifs.isChecked
            slideshowRandomOrder = view.randomOrder.isChecked
            slideshowUseFade = view.use_fade.isChecked
            slideshowMoveBackwards = view.moveBackwards.isChecked
            loopSlideshow = view.loopSlideshow.isChecked
        }
    }
}
