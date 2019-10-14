package com.eagle.gallery.pro.extensions

import android.os.Environment
import com.eagle.commons.models.FileDirItem

fun FileDirItem.isDownloadsFolder() = path.equals(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString(), true)
