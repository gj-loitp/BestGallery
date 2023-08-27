package com.roy.commons.activities

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.util.Pair
import com.roy.commons.R
import com.roy.commons.dlg.ConfirmationDialog
import com.roy.commons.dlg.ExportSettingsDialog
import com.roy.commons.dlg.FileConflictDialog
import com.roy.commons.ext.baseConfig
import com.roy.commons.ext.darkenColor
import com.roy.commons.ext.deleteFromMediaStore
import com.roy.commons.ext.getAppIconColors
import com.roy.commons.ext.getFileOutputStream
import com.roy.commons.ext.getPermissionString
import com.roy.commons.ext.getThemeId
import com.roy.commons.ext.hasPermission
import com.roy.commons.ext.isAppInstalledOnSDCard
import com.roy.commons.ext.isPathOnOTG
import com.roy.commons.ext.isPathOnSD
import com.roy.commons.ext.isShowingOTGDialog
import com.roy.commons.ext.isShowingSAFDialog
import com.roy.commons.ext.rescanPaths
import com.roy.commons.ext.showErrorToast
import com.roy.commons.ext.toast
import com.roy.commons.ext.updateActionBarTitle
import com.roy.commons.ext.updateOTGPathFromPartition
import com.roy.commons.ext.writeLn
import com.roy.commons.helpers.APP_ICON_IDS
import com.roy.commons.helpers.APP_LAUNCHER_NAME
import com.roy.commons.helpers.CONFLICT_KEEP_BOTH
import com.roy.commons.helpers.CONFLICT_SKIP
import com.roy.commons.helpers.MyContextWrapper
import com.roy.commons.helpers.OPEN_DOCUMENT_TREE
import com.roy.commons.helpers.OPEN_DOCUMENT_TREE_OTG
import com.roy.commons.helpers.PERMISSION_WRITE_STORAGE
import com.roy.commons.helpers.getConflictResolution
import com.roy.commons.itf.CopyMoveListener
import com.roy.commons.models.FAQItem
import com.roy.commons.models.FileDirItem
import com.roy.commons.sv.CopyMoveTask
import java.io.File
import java.util.*

abstract class BaseSimpleActivity : AppCompatActivity() {
    var copyMoveCallback: ((destinationPath: String) -> Unit)? = null
    private var actionOnPermission: ((granted: Boolean) -> Unit)? = null
    var isAskingPermissions = false
    var useDynamicTheme = true

    private val GENERIC_PERM_HANDLER = 100

    companion object {
        var funAfterSAFPermission: ((success: Boolean) -> Unit)? = null
    }

    abstract fun getAppIconIDs(): ArrayList<Int>

    abstract fun getAppLauncherName(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        if (useDynamicTheme) {
            setTheme(getThemeId())
        }

        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (useDynamicTheme) {
            setTheme(getThemeId())
            updateBackgroundColor()
        }
        updateActionbarColor()
        updateRecentsAppIcon()
    }

    override fun onStop() {
        super.onStop()
        actionOnPermission = null
    }

    override fun onDestroy() {
        super.onDestroy()
        funAfterSAFPermission = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun attachBaseContext(newBase: Context) {
        if (newBase.baseConfig.useEnglish) {
            super.attachBaseContext(
                MyContextWrapper(newBase).wrap(
                    context = newBase,
                    language = "en"
                )
            )
        } else {
            super.attachBaseContext(newBase)
        }
    }

    fun updateBackgroundColor(color: Int = baseConfig.backgroundColor) {
        window.decorView.setBackgroundColor(color)
    }

    fun updateActionbarColor(color: Int = baseConfig.primaryColor) {
        supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
        updateActionBarTitle(supportActionBar?.title.toString(), color)
        updateStatusbarColor(color)
        try {
            setTaskDescription(
                ActivityManager.TaskDescription(
                    /* label = */ null,
                    /* icon = */null,
                    /* colorPrimary = */color
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateStatusbarColor(color: Int) {
        try {
            window.statusBarColor = color.darkenColor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateRecentsAppIcon() {
        if (baseConfig.isUsingModifiedAppIcon) {
            val appIconIDs = getAppIconIDs()
            val currentAppIconColorIndex = getCurrentAppIconColorIndex()
            if (appIconIDs.size - 1 < currentAppIconColorIndex) {
                return
            }

            val recentsIcon =
                BitmapFactory.decodeResource(resources, appIconIDs[currentAppIconColorIndex])
            val title = getAppLauncherName()
            val color = baseConfig.primaryColor

            val description = ActivityManager.TaskDescription(
                /* label = */ title,
                /* icon = */ recentsIcon,
                /* colorPrimary = */ color
            )
            setTaskDescription(description)
        }
    }

    private fun getCurrentAppIconColorIndex(): Int {
        val appIconColor = baseConfig.appIconColor
        getAppIconColors().forEachIndexed { index, color ->
            if (color == appIconColor) {
                return index
            }
        }
        return 0
    }

    fun setTranslucentNavigation() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == OPEN_DOCUMENT_TREE && resultCode == Activity.RESULT_OK && resultData != null) {
            val data = resultData.data
            if (data != null && isProperSDFolder(data)) {
                if (resultData.dataString == baseConfig.OTGTreeUri) {
                    toast(R.string.sd_card_usb_same)
                    return
                }
                saveTreeUri(resultData)
                funAfterSAFPermission?.invoke(true)
                funAfterSAFPermission = null
            } else {
                toast(R.string.wrong_root_selected)
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, requestCode)
            }
        } else if (requestCode == OPEN_DOCUMENT_TREE_OTG && resultCode == Activity.RESULT_OK && resultData != null) {
            val data = resultData.data
            if (data != null && isProperOTGFolder(data)) {
                if (resultData.dataString == baseConfig.treeUri) {
                    funAfterSAFPermission?.invoke(false)
                    toast(R.string.sd_card_usb_same)
                    return
                }
                resultData.dataString?.let {
                    baseConfig.OTGTreeUri = it
                }
                baseConfig.OTGPartition =
                    baseConfig.OTGTreeUri.removeSuffix("%3A").substringAfterLast('/').trimEnd('/')
                updateOTGPathFromPartition()

                val takeFlags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                applicationContext.contentResolver.takePersistableUriPermission(
                    /* uri = */ data,
                    /* modeFlags = */ takeFlags
                )

                funAfterSAFPermission?.invoke(true)
                funAfterSAFPermission = null
            } else {
                toast(R.string.wrong_root_selected_usb)
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, requestCode)
            }
        }
    }

    private fun saveTreeUri(resultData: Intent) {
        val treeUri = resultData.data
        baseConfig.treeUri = treeUri.toString()

        val takeFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        treeUri?.let {
            applicationContext.contentResolver.takePersistableUriPermission(it, takeFlags)
        }
    }

    private fun isProperSDFolder(uri: Uri) =
        isExternalStorageDocument(uri) && isRootUri(uri) && !isInternalStorage(uri)

    private fun isProperOTGFolder(uri: Uri) =
        isExternalStorageDocument(uri) && isRootUri(uri) && !isInternalStorage(uri)

    private fun isRootUri(uri: Uri) = DocumentsContract.getTreeDocumentId(uri).endsWith(":")

    private fun isInternalStorage(uri: Uri) =
        isExternalStorageDocument(uri) && DocumentsContract.getTreeDocumentId(uri)
            .contains("primary")

    private fun isExternalStorageDocument(uri: Uri) =
        "com.android.externalstorage.documents" == uri.authority

    fun startAboutActivity(
        appNameId: Int,
        licenseMask: Int,
        versionName: String,
        faqItems: ArrayList<FAQItem>,
        showFAQBeforeMail: Boolean,
    ) {
    }

    fun startCustomizationActivity() {
        Intent(applicationContext, CustomizationActivity::class.java).apply {
            putExtra(APP_ICON_IDS, getAppIconIDs())
            putExtra(APP_LAUNCHER_NAME, getAppLauncherName())
            startActivity(this)
        }
    }

    fun handleSAFDialog(path: String, callback: (success: Boolean) -> Unit): Boolean {
        return if (!packageName.startsWith("com.roy")) {
            callback(true)
            false
        } else if (isShowingSAFDialog(path) || isShowingOTGDialog(path)) {
            funAfterSAFPermission = callback
            true
        } else {
            callback(true)
            false
        }
    }

    fun copyMoveFilesTo(
        fileDirItems: ArrayList<FileDirItem>,
        source: String,
        destination: String,
        isCopyOperation: Boolean,
        copyPhotoVideoOnly: Boolean,
        copyHidden: Boolean,
        callback: (destinationPath: String) -> Unit,
    ) {
        if (source == destination) {
            toast(R.string.source_and_destination_same)
            return
        }

        if (!File(destination).exists()) {
            toast(R.string.invalid_destination)
            return
        }

        handleSAFDialog(destination) {
            copyMoveCallback = callback
            var fileCountToCopy = fileDirItems.size
            if (isCopyOperation) {
                startCopyMove(
                    files = fileDirItems,
                    destinationPath = destination,
                    isCopyOperation = isCopyOperation,
                    copyPhotoVideoOnly = copyPhotoVideoOnly,
                    copyHidden = copyHidden
                )
            } else {
                if (isPathOnOTG(source) || isPathOnOTG(destination) || isPathOnSD(source) || isPathOnSD(
                        destination
                    ) || fileDirItems.first().isDirectory
                ) {
                    handleSAFDialog(source) {
                        startCopyMove(
                            files = fileDirItems,
                            destinationPath = destination,
                            isCopyOperation = isCopyOperation,
                            copyPhotoVideoOnly = copyPhotoVideoOnly,
                            copyHidden = copyHidden
                        )
                    }
                } else {
                    try {
                        checkConflicts(
                            files = fileDirItems,
                            destinationPath = destination,
                            index = 0,
                            conflictResolutions = LinkedHashMap()
                        ) {
                            toast(R.string.moving)
                            val updatedPaths = ArrayList<String>(fileDirItems.size)
                            val destinationFolder = File(destination)
                            for (oldFileDirItem in fileDirItems) {
                                var newFile = File(/* parent = */ destinationFolder, /* child = */
                                    oldFileDirItem.name
                                )
                                if (newFile.exists()) {
                                    when {
                                        getConflictResolution(
                                            resolutions = it,
                                            path = newFile.absolutePath
                                        ) == CONFLICT_SKIP -> fileCountToCopy--

                                        getConflictResolution(
                                            resolutions = it,
                                            path = newFile.absolutePath
                                        ) == CONFLICT_KEEP_BOTH -> newFile =
                                            getAlternativeFile(newFile)

                                        else ->
                                            // this file is guaranteed to be on the internal storage, so just delete it this way
                                            newFile.delete()
                                    }
                                }

                                if (!newFile.exists() && File(oldFileDirItem.path).renameTo(newFile)) {
                                    if (!baseConfig.keepLastModified) {
                                        newFile.setLastModified(System.currentTimeMillis())
                                    }
                                    updatedPaths.add(newFile.absolutePath)
                                    deleteFromMediaStore(oldFileDirItem.path)
                                }
                            }

                            if (updatedPaths.isEmpty()) {
                                copyMoveListener.copySucceeded(
                                    copyOnly = false,
                                    copiedAll = fileCountToCopy == 0,
                                    destinationPath = destination
                                )
                            } else {
                                rescanPaths(updatedPaths) {
                                    runOnUiThread {
                                        copyMoveListener.copySucceeded(
                                            copyOnly = false,
                                            copiedAll = fileCountToCopy <= updatedPaths.size,
                                            destinationPath = destination
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
        }
    }

    fun getAlternativeFile(file: File): File {
        var fileIndex = 1
        var newFile: File?
        do {
            val newName =
                String.format("%s(%d).%s", file.nameWithoutExtension, fileIndex, file.extension)
            newFile = File(file.parent, newName)
            fileIndex++
        } while (File(newFile!!.absolutePath).exists())
        return newFile
    }

    private fun startCopyMove(
        files: ArrayList<FileDirItem>,
        destinationPath: String,
        isCopyOperation: Boolean,
        copyPhotoVideoOnly: Boolean,
        copyHidden: Boolean,
    ) {
        checkConflicts(
            files = files,
            destinationPath = destinationPath,
            index = 0,
            conflictResolutions = LinkedHashMap()
        ) {
            toast(if (isCopyOperation) R.string.copying else R.string.moving)
            val pair = Pair(files, destinationPath)
            CopyMoveTask(
                activity = this,
                copyOnly = isCopyOperation,
                copyMediaOnly = copyPhotoVideoOnly,
                conflictResolutions = it,
                listener = copyMoveListener,
                copyHidden = copyHidden
            ).execute(pair)
        }
    }

    private fun checkConflicts(
        files: ArrayList<FileDirItem>,
        destinationPath: String,
        index: Int,
        conflictResolutions: LinkedHashMap<String, Int>,
        callback: (resolutions: LinkedHashMap<String, Int>) -> Unit,
    ) {
        if (index == files.size) {
            callback(conflictResolutions)
            return
        }

        val file = files[index]
        val newFileDirItem =
            FileDirItem("$destinationPath/${file.name}", file.name, file.isDirectory)
        if (File(newFileDirItem.path).exists()) {
            FileConflictDialog(this, newFileDirItem) { resolution, applyForAll ->
                if (applyForAll) {
                    conflictResolutions.clear()
                    conflictResolutions[""] = resolution
                    checkConflicts(
                        files = files,
                        destinationPath = destinationPath,
                        index = files.size,
                        conflictResolutions = conflictResolutions,
                        callback = callback
                    )
                } else {
                    conflictResolutions[newFileDirItem.path] = resolution
                    checkConflicts(
                        files = files,
                        destinationPath = destinationPath,
                        index = index + 1,
                        conflictResolutions = conflictResolutions,
                        callback = callback
                    )
                }
            }
        } else {
            checkConflicts(
                files = files,
                destinationPath = destinationPath,
                index = index + 1,
                conflictResolutions = conflictResolutions,
                callback = callback
            )
        }
    }

    fun handlePermission(permissionId: Int, callback: (granted: Boolean) -> Unit) {
        actionOnPermission = null
        if (hasPermission(permissionId)) {
            callback(true)
        } else {
            isAskingPermissions = true
            actionOnPermission = callback
            ActivityCompat.requestPermissions(
                /* activity = */ this,
                /* permissions = */ arrayOf(getPermissionString(permissionId)),
                /* requestCode = */ GENERIC_PERM_HANDLER
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        isAskingPermissions = false
        if (requestCode == GENERIC_PERM_HANDLER && grantResults.isNotEmpty()) {
            actionOnPermission?.invoke(grantResults[0] == 0)
        }
    }

    private val copyMoveListener = object : CopyMoveListener {
        override fun copySucceeded(copyOnly: Boolean, copiedAll: Boolean, destinationPath: String) {
            if (copyOnly) {
                toast(if (copiedAll) R.string.copying_success else R.string.copying_success_partial)
            } else {
                toast(if (copiedAll) R.string.moving_success else R.string.moving_success_partial)
            }

            copyMoveCallback?.invoke(destinationPath)
            copyMoveCallback = null
        }

        override fun copyFailed() {
            toast(R.string.copy_move_failed)
            copyMoveCallback = null
        }
    }

    fun checkAppOnSDCard() {
        if (!baseConfig.wasAppOnSDShown && isAppInstalledOnSDCard()) {
            baseConfig.wasAppOnSDShown = true
            ConfirmationDialog(this, "", R.string.app_on_sd_card, R.string.ok, 0) {}
        }
    }

    fun exportSettings(configItems: LinkedHashMap<String, Any>, defaultFilename: String) {
        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                ExportSettingsDialog(this, defaultFilename) {
                    val file = File(it)
                    val fileDirItem = FileDirItem(file.absolutePath, file.name)
                    getFileOutputStream(fileDirItem = fileDirItem, allowCreatingNewFile = true) {
                        if (it == null) {
                            toast(R.string.unknown_error_occurred)
                            return@getFileOutputStream
                        }

                        Thread {
                            it.bufferedWriter().use { out ->
                                for ((key, value) in configItems) {
                                    out.writeLn("$key=$value")
                                }
                            }

                            toast(R.string.settings_exported_successfully)
                        }.start()
                    }
                }
            }
        }
    }
}
