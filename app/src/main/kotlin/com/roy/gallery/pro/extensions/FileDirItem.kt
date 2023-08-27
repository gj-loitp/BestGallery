package com.roy.gallery.pro.extensions

import android.os.Environment
import com.roy.commons.models.FileDirItem

fun FileDirItem.isDownloadsFolder() = path.equals(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString(), true)
