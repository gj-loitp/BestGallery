package com.roy.gallery.pro.activities

import android.annotation.SuppressLint
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import android.view.WindowManager
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.addPathToDB
import com.roy.gallery.pro.extensions.config
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.getRealPathFromURI
import com.roy.commons.helpers.isPiePlus

open class SimpleActivity : BaseSimpleActivity() {
    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)

            uri?.let {
                val path = getRealPathFromURI(it)
                if (path != null) {
                    addPathToDB(path)
                }
            }
        }
    }

    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    @SuppressLint("InlinedApi")
    protected fun checkNotchSupport() {
        if (isPiePlus()) {
            val cutoutMode = when {
                config.showNotch -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            window.attributes.layoutInDisplayCutoutMode = cutoutMode
            if (config.showNotch) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            }
        }
    }

    protected fun registerFileUpdateListener() {
        try {
            contentResolver.registerContentObserver(
                /* uri = */ MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                /* notifyForDescendants = */ true,
                /* observer = */ observer
            )
            contentResolver.registerContentObserver(
                /* uri = */ MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                /* notifyForDescendants = */ true,
                /* observer = */ observer
            )
        } catch (ignored: Exception) {
        }
    }

    protected fun unregisterFileUpdateListener() {
        try {
            contentResolver.unregisterContentObserver(observer)
        } catch (ignored: Exception) {
        }
    }
}
