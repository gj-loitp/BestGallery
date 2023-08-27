package com.roy.gallery.pro.extensions

import android.annotation.TargetApi
import android.app.Activity
import android.content.ContentProviderOperation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.roy.gallery.pro.BuildConfig
import com.roy.gallery.pro.R
import com.roy.gallery.pro.dialogs.PickDirectoryDialog
import com.roy.gallery.pro.helpers.NOMEDIA
import com.roy.gallery.pro.helpers.RECYCLE_BIN
import com.roy.gallery.pro.interfaces.MediumDao
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.dlg.ConfirmationDialog
import com.roy.commons.ext.deleteFile
import com.roy.commons.ext.getCompressionFormat
import com.roy.commons.ext.getDocumentFile
import com.roy.commons.ext.getFileInputStreamSync
import com.roy.commons.ext.getFileOutputStream
import com.roy.commons.ext.getFileOutputStreamSync
import com.roy.commons.ext.getFileUri
import com.roy.commons.ext.getFilenameFromPath
import com.roy.commons.ext.getMimeType
import com.roy.commons.ext.getParentPath
import com.roy.commons.ext.isJpg
import com.roy.commons.ext.needsStupidWritePermissions
import com.roy.commons.ext.openEditorIntent
import com.roy.commons.ext.openPathIntent
import com.roy.commons.ext.renameFile
import com.roy.commons.ext.rescanPaths
import com.roy.commons.ext.saveExifRotation
import com.roy.commons.ext.saveImageRotation
import com.roy.commons.ext.scanFileRecursively
import com.roy.commons.ext.scanPathRecursively
import com.roy.commons.ext.setAsIntent
import com.roy.commons.ext.sharePathIntent
import com.roy.commons.ext.sharePathsIntent
import com.roy.commons.ext.showErrorToast
import com.roy.commons.ext.toFileDirItem
import com.roy.commons.ext.toast
import com.roy.commons.ext.updateLastModified
import com.roy.commons.helpers.LICENSE_CROPPER
import com.roy.commons.helpers.LICENSE_EXOPLAYER
import com.roy.commons.helpers.LICENSE_FILTERS
import com.roy.commons.helpers.LICENSE_GESTURE_VIEWS
import com.roy.commons.helpers.LICENSE_GIF_DRAWABLE
import com.roy.commons.helpers.LICENSE_GLIDE
import com.roy.commons.helpers.LICENSE_PANORAMA_VIEW
import com.roy.commons.helpers.LICENSE_PATTERN
import com.roy.commons.helpers.LICENSE_PICASSO
import com.roy.commons.helpers.LICENSE_REPRINT
import com.roy.commons.helpers.LICENSE_RTL
import com.roy.commons.helpers.LICENSE_SANSELAN
import com.roy.commons.helpers.LICENSE_SUBSAMPLING
import com.roy.commons.models.FAQItem
import com.roy.commons.models.FileDirItem
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

fun Activity.sharePath(path: String) {
    sharePathIntent(path, BuildConfig.APPLICATION_ID)
}

fun Activity.sharePaths(paths: ArrayList<String>) {
    sharePathsIntent(paths, BuildConfig.APPLICATION_ID)
}

fun Activity.shareMediumPath(path: String) {
    sharePath(path)
}

fun Activity.shareMediaPaths(paths: ArrayList<String>) {
    sharePaths(paths)
}

fun Activity.setAs(path: String) {
    setAsIntent(path, BuildConfig.APPLICATION_ID)
}

fun Activity.openPath(path: String, forceChooser: Boolean) {
    openPathIntent(path, forceChooser, BuildConfig.APPLICATION_ID)
}

fun Activity.openEditor(path: String, forceChooser: Boolean = false) {
    val newPath = path.removePrefix("file://")
    openEditorIntent(newPath, forceChooser, BuildConfig.APPLICATION_ID)
}

fun Activity.launchCamera() {
    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        toast(R.string.no_app_found)
    }
}

fun com.roy.gallery.pro.activities.SimpleActivity.launchAbout() {
    val licenses =
        LICENSE_GLIDE or LICENSE_CROPPER or LICENSE_RTL or LICENSE_SUBSAMPLING or LICENSE_PATTERN or LICENSE_REPRINT or LICENSE_GIF_DRAWABLE or
                LICENSE_PICASSO or LICENSE_EXOPLAYER or LICENSE_PANORAMA_VIEW or LICENSE_SANSELAN or LICENSE_FILTERS or LICENSE_GESTURE_VIEWS

    val faqItems = arrayListOf(
        FAQItem(R.string.faq_5_title_commons, R.string.faq_5_text_commons),
        FAQItem(R.string.faq_1_title, R.string.faq_1_text),
        FAQItem(R.string.faq_2_title, R.string.faq_2_text),
        FAQItem(R.string.faq_3_title, R.string.faq_3_text),
        FAQItem(R.string.faq_4_title, R.string.faq_4_text),
        FAQItem(R.string.faq_5_title, R.string.faq_5_text),
        FAQItem(R.string.faq_6_title, R.string.faq_6_text),
        FAQItem(R.string.faq_7_title, R.string.faq_7_text),
        FAQItem(R.string.faq_8_title, R.string.faq_8_text),
        FAQItem(R.string.faq_10_title, R.string.faq_10_text),
        FAQItem(R.string.faq_11_title, R.string.faq_11_text),
        FAQItem(R.string.faq_12_title, R.string.faq_12_text),
        FAQItem(R.string.faq_13_title, R.string.faq_13_text),
        FAQItem(R.string.faq_14_title, R.string.faq_14_text),
        FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons),
        FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons)
    )

    startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
}

fun AppCompatActivity.showSystemUI(toggleActionBarVisibility: Boolean) {
    if (toggleActionBarVisibility) {
        supportActionBar?.show()
    }

    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
}

fun AppCompatActivity.hideSystemUI(toggleActionBarVisibility: Boolean) {
    if (toggleActionBarVisibility) {
        supportActionBar?.hide()
    }

    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE
}

fun BaseSimpleActivity.addNoMedia(path: String, callback: () -> Unit) {
    val file = File(path, NOMEDIA)
    if (file.exists()) {
        callback()
        return
    }

    if (needsStupidWritePermissions(path)) {
        handleSAFDialog(file.absolutePath) {
            val fileDocument = getDocumentFile(path)
            if (fileDocument?.exists() == true && fileDocument.isDirectory) {
                fileDocument.createFile("", NOMEDIA)
                applicationContext.scanFileRecursively(file) {
                    callback()
                }
            } else {
                toast(R.string.unknown_error_occurred)
                callback()
            }
        }
    } else {
        try {
            file.createNewFile()
            applicationContext.scanFileRecursively(file) {
                callback()
            }
        } catch (e: Exception) {
            showErrorToast(e)
            callback()
        }
    }
}

fun BaseSimpleActivity.removeNoMedia(path: String, callback: (() -> Unit)? = null) {
    val file = File(path, NOMEDIA)
    if (!file.exists()) {
        callback?.invoke()
        return
    }

    tryDeleteFileDirItem(file.toFileDirItem(applicationContext), false, false) {
        scanPathRecursively(file.parent)
        callback?.invoke()
    }
}

fun BaseSimpleActivity.toggleFileVisibility(
    oldPath: String,
    hide: Boolean,
    callback: ((newPath: String) -> Unit)? = null,
) {
    val path = oldPath.getParentPath()
    var filename = oldPath.getFilenameFromPath()
    if ((hide && filename.startsWith('.')) || (!hide && !filename.startsWith('.'))) {
        callback?.invoke(oldPath)
        return
    }

    filename = if (hide) {
        ".${filename.trimStart('.')}"
    } else {
        filename.substring(1, filename.length)
    }

    val newPath = "$path/$filename"
    renameFile(oldPath, newPath) {
        callback?.invoke(newPath)
        Thread {
            updateDBMediaPath(oldPath, newPath)
        }.start()
    }
}

fun BaseSimpleActivity.tryCopyMoveFilesTo(
    fileDirItems: ArrayList<FileDirItem>,
    isCopyOperation: Boolean,
    callback: (destinationPath: String) -> Unit,
) {
    if (fileDirItems.isEmpty()) {
        toast(R.string.unknown_error_occurred)
        return
    }

    val source = fileDirItems[0].getParentPath()
    PickDirectoryDialog(this, source, true) {
        copyMoveFilesTo(
            fileDirItems,
            source.trimEnd('/'),
            it,
            isCopyOperation,
            true,
            config.shouldShowHidden,
            callback
        )
    }
}

fun BaseSimpleActivity.tryDeleteFileDirItem(
    fileDirItem: FileDirItem, allowDeleteFolder: Boolean = false, deleteFromDatabase: Boolean,
    callback: ((wasSuccess: Boolean) -> Unit)? = null,
) {
    deleteFile(fileDirItem, allowDeleteFolder) {
        if (deleteFromDatabase) {
            Thread {
                deleteDBPath(galleryDB.MediumDao(), fileDirItem.path)
                runOnUiThread {
                    callback?.invoke(it)
                }
            }.start()
        } else {
            callback?.invoke(it)
        }
    }
}

fun BaseSimpleActivity.movePathsInRecycleBin(
    paths: ArrayList<String>,
    mediumDao: MediumDao = galleryDB.MediumDao(),
    callback: ((wasSuccess: Boolean) -> Unit)?,
) {
    Thread {
        var pathsCnt = paths.size
        paths.forEach {
            val file = File(it)
            val internalFile = File(recycleBinPath, it)
            try {
                if (file.copyRecursively(internalFile, true)) {
                    mediumDao.updateDeleted("$RECYCLE_BIN$it", System.currentTimeMillis(), it)
                    pathsCnt--
                }
            } catch (e: Exception) {
                showErrorToast(e)
                return@forEach
            }
        }
        callback?.invoke(pathsCnt == 0)
    }.start()
}

fun BaseSimpleActivity.restoreRecycleBinPath(path: String, callback: () -> Unit) {
    restoreRecycleBinPaths(arrayListOf(path), galleryDB.MediumDao(), callback)
}

fun BaseSimpleActivity.restoreRecycleBinPaths(
    paths: ArrayList<String>,
    mediumDao: MediumDao = galleryDB.MediumDao(),
    callback: () -> Unit,
) {
    Thread {
        val newPaths = ArrayList<String>()
        paths.forEach {
            val source = it
            val destination = it.removePrefix(recycleBinPath)

            var inputStream: InputStream? = null
            var out: OutputStream? = null
            try {
                out = getFileOutputStreamSync(destination, source.getMimeType())
                inputStream = getFileInputStreamSync(source)!!
                inputStream.copyTo(out!!)
                if (File(source).length() == File(destination).length()) {
                    mediumDao.updateDeleted(
                        destination.removePrefix(recycleBinPath),
                        0,
                        "$RECYCLE_BIN$destination"
                    )
                }
                newPaths.add(destination)
            } catch (e: Exception) {
                showErrorToast(e)
            } finally {
                inputStream?.close()
                out?.close()
            }
        }

        runOnUiThread {
            callback()
        }

        fixDateTaken(newPaths)
    }.start()
}

fun BaseSimpleActivity.emptyTheRecycleBin(callback: (() -> Unit)? = null) {
    Thread {
        recycleBin.deleteRecursively()
        galleryDB.MediumDao().clearRecycleBin()
        galleryDB.DirectoryDao().deleteRecycleBin()
        toast(R.string.recycle_bin_emptied)
        callback?.invoke()
    }.start()
}

fun BaseSimpleActivity.emptyAndDisableTheRecycleBin(callback: () -> Unit) {
    Thread {
        emptyTheRecycleBin {
            config.useRecycleBin = false
            callback()
        }
    }.start()
}

fun BaseSimpleActivity.showRecycleBinEmptyingDialog(callback: () -> Unit) {
    ConfirmationDialog(
        this,
        "",
        R.string.empty_recycle_bin_confirmation,
        R.string.yes,
        R.string.no
    ) {
        callback()
    }
}

fun BaseSimpleActivity.updateFavoritePaths(
    fileDirItems: ArrayList<FileDirItem>,
    destination: String,
) {
    Thread {
        fileDirItems.forEach {
            val newPath = "$destination/${it.name}"
            updateDBMediaPath(it.path, newPath)
        }
    }.start()
}

fun Activity.hasNavBar(): Boolean {
    val display = windowManager.defaultDisplay

    val realDisplayMetrics = DisplayMetrics()
    display.getRealMetrics(realDisplayMetrics)

    val displayMetrics = DisplayMetrics()
    display.getMetrics(displayMetrics)

    return (realDisplayMetrics.widthPixels - displayMetrics.widthPixels > 0) || (realDisplayMetrics.heightPixels - displayMetrics.heightPixels > 0)
}

fun Activity.fixDateTaken(paths: ArrayList<String>, callback: (() -> Unit)? = null) {
    val BATCH_SIZE = 50
    toast(R.string.fixing)
    try {
        var didUpdateFile = false
        val operations = ArrayList<ContentProviderOperation>()
        val mediumDao = galleryDB.MediumDao()
        for (path in paths) {
            val dateTime = ExifInterface(path).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: ExifInterface(path).getAttribute(ExifInterface.TAG_DATETIME) ?: continue

            // some formats contain a "T" in the middle, some don't
            // sample dates: 2015-07-26T14:55:23, 2018:09:05 15:09:05
            val t = if (dateTime.substring(10, 11) == "T") "\'T\'" else " "
            val separator = dateTime.substring(4, 5)
            val format = "yyyy${separator}MM${separator}dd${t}kk:mm:ss"
            val formatter = SimpleDateFormat(format, Locale.getDefault())
            val timestamp = formatter.parse(dateTime).time

            val uri = getFileUri(path)
            ContentProviderOperation.newUpdate(uri).apply {
                val selection = "${MediaStore.Images.Media.DATA} = ?"
                val selectionArgs = arrayOf(path)
                withSelection(selection, selectionArgs)
                withValue(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                operations.add(build())
            }

            if (operations.size % BATCH_SIZE == 0) {
                contentResolver.applyBatch(MediaStore.AUTHORITY, operations)
                operations.clear()
            }

            mediumDao.updateFavoriteDateTaken(path, timestamp)
            didUpdateFile = true
        }

        val resultSize = contentResolver.applyBatch(MediaStore.AUTHORITY, operations).size
        if (resultSize == 0) {
            didUpdateFile = false
            rescanPaths(paths)
        }

        toast(if (didUpdateFile) R.string.dates_fixed_successfully else R.string.unknown_error_occurred)
        runOnUiThread {
            callback?.invoke()
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun BaseSimpleActivity.saveRotatedImageToFile(
    oldPath: String,
    newPath: String,
    degrees: Int,
    showToasts: Boolean,
    callback: () -> Unit,
) {
    var newDegrees = degrees
    if (newDegrees < 0) {
        newDegrees += 360
    }

    if (oldPath == newPath && oldPath.isJpg()) {
        if (tryRotateByExif(oldPath, newDegrees, showToasts, callback)) {
            return
        }
    }

    val tmpPath = "$recycleBinPath/.tmp_${newPath.getFilenameFromPath()}"
    val tmpFileDirItem = FileDirItem(tmpPath, tmpPath.getFilenameFromPath())
    try {
        getFileOutputStream(tmpFileDirItem) {
            if (it == null) {
                if (showToasts) {
                    toast(R.string.unknown_error_occurred)
                }
                return@getFileOutputStream
            }

            val oldLastModified = File(oldPath).lastModified()
            if (oldPath.isJpg()) {
                copyFile(oldPath, tmpPath)
                saveExifRotation(ExifInterface(tmpPath), newDegrees)
            } else {
                val inputstream = getFileInputStreamSync(oldPath)
                val bitmap = BitmapFactory.decodeStream(inputstream)
                saveFile(tmpPath, bitmap, it as FileOutputStream, newDegrees)
            }

            if (File(newPath).exists()) {
                tryDeleteFileDirItem(
                    FileDirItem(newPath, newPath.getFilenameFromPath()),
                    false,
                    true
                )
            }

            copyFile(tmpPath, newPath)
            scanPathRecursively(newPath)
            fileRotatedSuccessfully(newPath, oldLastModified)

            it.flush()
            it.close()
            callback.invoke()
        }
    } catch (e: OutOfMemoryError) {
        if (showToasts) {
            toast(R.string.out_of_memory_error)
        }
    } catch (e: Exception) {
        if (showToasts) {
            showErrorToast(e)
        }
    } finally {
        tryDeleteFileDirItem(tmpFileDirItem, false, true)
    }
}

@TargetApi(Build.VERSION_CODES.N)
fun Activity.tryRotateByExif(
    path: String,
    degrees: Int,
    showToasts: Boolean,
    callback: () -> Unit,
): Boolean {
    return try {
        val file = File(path)
        val oldLastModified = file.lastModified()
        if (saveImageRotation(path, degrees)) {
            fileRotatedSuccessfully(path, oldLastModified)
            callback.invoke()
            if (showToasts) {
                toast(R.string.file_saved)
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        if (showToasts) {
            showErrorToast(e)
        }
        false
    }
}

fun Activity.fileRotatedSuccessfully(path: String, lastModified: Long) {
    if (config.keepLastModified) {
        File(path).setLastModified(lastModified)
        updateLastModified(path, lastModified)
    }

    Picasso.get().invalidate(path.getFileKey())
    // we cannot refresh a specific image in Glide Cache, so just clear it all
    val glide = Glide.get(applicationContext)
    glide.clearDiskCache()
    runOnUiThread {
        glide.clearMemory()
    }
}

fun BaseSimpleActivity.copyFile(source: String, destination: String) {
    var inputStream: InputStream? = null
    var out: OutputStream? = null
    try {
        out = getFileOutputStreamSync(destination, source.getMimeType())
        inputStream = getFileInputStreamSync(source)
        inputStream?.copyTo(out!!)
    } finally {
        inputStream?.close()
        out?.close()
    }
}

fun saveFile(path: String, bitmap: Bitmap, out: FileOutputStream, degrees: Int) {
    val matrix = Matrix()
    matrix.postRotate(degrees.toFloat())
    val bmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    bmp.compress(path.getCompressionFormat(), 90, out)
}
