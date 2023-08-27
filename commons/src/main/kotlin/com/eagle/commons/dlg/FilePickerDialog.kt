package com.eagle.commons.dlg

import android.annotation.SuppressLint
import android.os.Environment
import android.os.Parcelable
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.eagle.commons.R
import com.eagle.commons.activities.BaseSimpleActivity
import com.eagle.commons.adt.FilepickerItemsAdapter
import com.eagle.commons.ext.*
import com.eagle.commons.helpers.SORT_BY_SIZE
import com.eagle.commons.models.FileDirItem
import com.eagle.commons.views.Breadcrumbs
import kotlinx.android.synthetic.main.dlg_filepicker.view.*
import java.io.File
import java.util.*

/**
 * The only filepicker constructor with a couple optional parameters
 *
 * @param activity has to be activity to avoid some Theme.AppCompat issues
 * @param currPath initial path of the dialog, defaults to the external storage
 * @param pickFile toggle used to determine if we are picking a file or a folder
 * @param showHidden toggle for showing hidden items, whose name starts with a dot
 * @param showFAB toggle the displaying of a Floating Action Button for creating new folders
 * @param callback the callback used for returning the selected file/folder
 */
class FilePickerDialog(
    val activity: BaseSimpleActivity,
    private var currPath: String = Environment.getExternalStorageDirectory().toString(),
    private val pickFile: Boolean = true,
    private var showHidden: Boolean = false,
    val showFAB: Boolean = false,
    private val canAddShowHiddenButton: Boolean = false,
    val callback: (pickedPath: String) -> Unit,
) : Breadcrumbs.BreadcrumbsListener {

    private var mFirstUpdate = true
    private var mPrevPath = ""
    private var mScrollStates = HashMap<String, Parcelable>()

    private lateinit var mDialog: AlertDialog
    @SuppressLint("InflateParams")
    private var mDialogView = activity.layoutInflater.inflate(R.layout.dlg_filepicker, null)

    init {
        if (!File(currPath).exists()) {
            currPath = activity.internalStoragePath
        }

        if (!File(currPath).isDirectory) {
            currPath = currPath.getParentPath()
        }

        // do not allow copying files in the recycle bin manually
        if (currPath.startsWith(activity.filesDir.absolutePath)) {
            currPath = activity.internalStoragePath
        }

        mDialogView.filePickerBreadcrumbs.listener = this
        tryUpdateItems()

        val builder = AlertDialog.Builder(activity)
            .setNegativeButton(R.string.cancel, null)
            .setOnKeyListener { _, i, keyEvent ->
                if (keyEvent.action == KeyEvent.ACTION_UP && i == KeyEvent.KEYCODE_BACK) {
                    val breadcrumbs = mDialogView.filePickerBreadcrumbs
                    if (breadcrumbs.childCount > 1) {
                        breadcrumbs.removeBreadcrumb()
                        currPath = breadcrumbs.getLastItem().path.trimEnd('/')
                        tryUpdateItems()
                    } else {
                        mDialog.dismiss()
                    }
                }
                true
            }

        if (!pickFile)
            builder.setPositiveButton(R.string.ok, null)

        if (showFAB) {
            mDialogView.filePickerFab.apply {
                beVisible()
                setOnClickListener { createNewFolder() }
            }
        }

        val secondaryFabBottomMargin =
            activity.resources.getDimension(if (showFAB) R.dimen.secondary_fab_bottom_margin else R.dimen.activity_margin)
                .toInt()
        mDialogView.filePickerFabShowHidden.apply {
            beVisibleIf(!showHidden && canAddShowHiddenButton)
            (layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = secondaryFabBottomMargin
            setOnClickListener {
                activity.handleHiddenFolderPasswordProtection {
                    beGone()
                    showHidden = true
                    tryUpdateItems()
                }
            }
        }

        mDialog = builder.create().apply {
            activity.setupDialogStuff(mDialogView, this, getTitle())
        }

        if (!pickFile) {
            mDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                verifyPath()
            }
        }
    }

    private fun getTitle() = if (pickFile) R.string.select_file else R.string.select_folder

    private fun createNewFolder() {
        CreateNewFolderDialog(activity, currPath) {
            callback(it)
            mDialog.dismiss()
        }
    }

    private fun tryUpdateItems() {
        Thread {
            getItems(
                path = currPath,
                getProperFileSize = activity.baseConfig.sorting and SORT_BY_SIZE != 0
            ) {
                activity.runOnUiThread {
                    updateItems(it)
                }
            }
        }.start()
    }

    private fun updateItems(items: List<FileDirItem>) {
        if (!containsDirectory(items) && !mFirstUpdate && !pickFile && !showFAB) {
            verifyPath()
            return
        }

        val sortedItems =
            items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.getDefault()) }))

        val adapter = FilepickerItemsAdapter(activity, sortedItems, mDialogView.filePickerList) {
            if ((it as FileDirItem).isDirectory) {
                currPath = it.path
                tryUpdateItems()
            } else if (pickFile) {
                currPath = it.path
                verifyPath()
            }
        }
        adapter.addVerticalDividers(true)

        val layoutManager = mDialogView.filePickerList.layoutManager as LinearLayoutManager
        mScrollStates[mPrevPath.trimEnd('/')] = layoutManager.onSaveInstanceState()!!

        mDialogView.apply {
            filePickerList.adapter = adapter
            filePickerBreadcrumbs.setBreadcrumb(currPath)
            filePickerFastscroller.allowBubbleDisplay = context.baseConfig.showInfoBubble
            filePickerFastscroller.setViews(filePickerList) {
                filePickerFastscroller.updateBubbleText(
                    sortedItems.getOrNull(it)?.getBubbleText(context) ?: ""
                )
            }

            layoutManager.onRestoreInstanceState(mScrollStates[currPath.trimEnd('/')])
            filePickerList.onGlobalLayout {
                filePickerFastscroller.setScrollToY(filePickerList.computeVerticalScrollOffset())
            }
        }

        mFirstUpdate = false
        mPrevPath = currPath
    }

    private fun verifyPath() {
        val file = File(currPath)
        if ((pickFile && file.isFile) || (!pickFile && file.isDirectory)) {
            sendSuccess()
        }
    }

    private fun sendSuccess() {
        currPath = if (currPath.length == 1) {
            currPath
        } else {
            currPath.trimEnd('/')
        }
        callback(currPath)
        mDialog.dismiss()
    }

    private fun getItems(
        path: String,
        getProperFileSize: Boolean,
        callback: (List<FileDirItem>) -> Unit,
    ) {
        val items = ArrayList<FileDirItem>()
        val base = File(path)
        val files = base.listFiles()
        if (files == null) {
            callback(items)
            return
        }

        for (file in files) {
            if (!showHidden && file.isHidden) {
                continue
            }

            val curPath = file.absolutePath
            val curName = curPath.getFilenameFromPath()
            val size = if (getProperFileSize) file.getProperSize(showHidden) else file.length()
            items.add(
                FileDirItem(
                    path = curPath,
                    name = curName,
                    isDirectory = file.isDirectory,
                    children = file.getDirectChildrenCount(showHidden),
                    size = size
                )
            )
        }
        callback(items)
    }

    private fun containsDirectory(items: List<FileDirItem>) = items.any { it.isDirectory }

    override fun breadcrumbClicked(id: Int) {
        if (id == 0) {
            StoragePickerDialog(activity, currPath) {
                currPath = it
                tryUpdateItems()
            }
        } else {
            val item = mDialogView.filePickerBreadcrumbs.getChildAt(id).tag as FileDirItem
            if (currPath != item.path.trimEnd('/')) {
                currPath = item.path
                tryUpdateItems()
            }
        }
    }
}
