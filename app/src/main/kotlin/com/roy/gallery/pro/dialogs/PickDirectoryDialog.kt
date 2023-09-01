package com.roy.gallery.pro.dialogs

import android.annotation.SuppressLint
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.*
import com.roy.gallery.pro.helpers.VIEW_TYPE_GRID
import com.roy.gallery.pro.models.Directory
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.dlg.FilePickerDialog
import com.roy.commons.ext.beGone
import com.roy.commons.ext.beGoneIf
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.handleHiddenFolderPasswordProtection
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.ext.toast
import com.roy.commons.views.MyGridLayoutManager
import kotlinx.android.synthetic.main.dlg_directory_picker.view.*

class PickDirectoryDialog(
    val activity: BaseSimpleActivity,
    val sourcePath: String,
    showOtherFolderButton: Boolean,
    val callback: (path: String) -> Unit,
) {
    private var dialog: AlertDialog
    private var shownDirectories = ArrayList<Directory>()
    private var allDirectories = ArrayList<Directory>()
    private var openedSubfolders = arrayListOf("")

    @SuppressLint("InflateParams")
    private var view = activity.layoutInflater.inflate(R.layout.dlg_directory_picker, null)
    private var isGridViewType = activity.config.viewTypeFolders == VIEW_TYPE_GRID
    private var showHidden = activity.config.shouldShowHidden
    private var currentPathPrefix = ""

    init {
        (view.directoriesGrid.layoutManager as MyGridLayoutManager).apply {
            orientation =
                if (activity.config.scrollHorizontally && isGridViewType) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL
            spanCount = if (isGridViewType) activity.config.dirColumnCnt else 1
        }

        val builder = AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .setOnKeyListener { _, i, keyEvent ->
                if (keyEvent.action == KeyEvent.ACTION_UP && i == KeyEvent.KEYCODE_BACK) {
                    backPressed()
                }
                true
            }

        if (showOtherFolderButton) {
            builder.setNeutralButton(R.string.other_folder) { _, i -> showOtherFolder() }
        }

        dialog = builder.create().apply {
            activity.setupDialogStuff(view, this, R.string.select_destination) {
                view.directoriesShowHidden.beVisibleIf(!context.config.shouldShowHidden)
                view.directoriesShowHidden.setOnClickListener {
                    activity.handleHiddenFolderPasswordProtection {
                        view.directoriesShowHidden.beGone()
                        showHidden = true
                        fetchDirectories(true)
                    }
                }
            }
        }

        fetchDirectories(false)
    }

    private fun fetchDirectories(forceShowHidden: Boolean) {
        activity.getCachedDirectories(forceShowHidden = forceShowHidden) {
            if (it.isNotEmpty()) {
                it.forEach {
                    it.subfoldersMediaCount = it.mediaCnt
                }

                activity.runOnUiThread {
                    gotDirectories(activity.addTempFolderIfNeeded(it))
                }
            }
        }
    }

    private fun showOtherFolder() {
        FilePickerDialog(
            activity = activity,
            currPath = sourcePath,
            pickFile = false,
            showHidden = showHidden,
            showFAB = true,
            canAddShowHiddenButton = true
        ) {
            callback(it)
        }
    }

    private fun gotDirectories(newDirs: ArrayList<Directory>) {
        if (allDirectories.isEmpty()) {
            allDirectories = newDirs.clone() as ArrayList<Directory>
        }
        val distinctDirs =
            newDirs.distinctBy { it.path.getDistinctPath() }.toMutableList() as ArrayList<Directory>
        val sortedDirs = activity.getSortedDirectories(distinctDirs)
        val dirs = activity.getDirsToShow(sortedDirs, allDirectories, currentPathPrefix)
            .clone() as ArrayList<Directory>
        if (dirs.hashCode() == shownDirectories.hashCode()) {
            return
        }

        shownDirectories = dirs
        val adapter = com.roy.gallery.pro.adapters.DirectoryAdapter(
            activity = activity,
            dirs = dirs.clone() as ArrayList<Directory>,
            listener = null,
            recyclerView = view.directoriesGrid,
            isPickIntent = true
        ) {
            val clickedDir = it as Directory
            val path = clickedDir.path
            if (clickedDir.subfoldersCount == 1 || !activity.config.groupDirectSubfolders) {
                if (path.trimEnd('/') == sourcePath) {
                    activity.toast(R.string.source_and_destination_same)
                    return@DirectoryAdapter
                } else {
                    callback(path)
                    dialog.dismiss()
                }
            } else {
                currentPathPrefix = path
                openedSubfolders.add(path)
                gotDirectories(allDirectories)
            }
        }

        val scrollHorizontally = activity.config.scrollHorizontally && isGridViewType
        val sorting = activity.config.directorySorting
        view.apply {
            directoriesGrid.adapter = adapter

            directoriesVerticalFastScroller.isHorizontal = false
            directoriesVerticalFastScroller.beGoneIf(scrollHorizontally)

            directoriesHorizontalFastScroller.isHorizontal = true
            directoriesHorizontalFastScroller.beVisibleIf(scrollHorizontally)

            if (scrollHorizontally) {
                directoriesHorizontalFastScroller.allowBubbleDisplay =
                    activity.config.showInfoBubble
                directoriesHorizontalFastScroller.setViews(directoriesGrid) {
                    directoriesHorizontalFastScroller.updateBubbleText(
                        dirs[it].getBubbleText(
                            sorting = sorting,
                            context = activity
                        )
                    )
                }
            } else {
                directoriesVerticalFastScroller.allowBubbleDisplay = activity.config.showInfoBubble
                directoriesVerticalFastScroller.setViews(directoriesGrid) {
                    directoriesVerticalFastScroller.updateBubbleText(
                        dirs[it].getBubbleText(
                            sorting = sorting,
                            context = activity
                        )
                    )
                }
            }
        }
    }

    private fun backPressed() {
        if (activity.config.groupDirectSubfolders) {
            if (currentPathPrefix.isEmpty()) {
                dialog.dismiss()
            } else {
                openedSubfolders.removeAt(openedSubfolders.size - 1)
                currentPathPrefix = openedSubfolders.last()
                gotDirectories(allDirectories)
            }
        } else {
            dialog.dismiss()
        }
    }
}
