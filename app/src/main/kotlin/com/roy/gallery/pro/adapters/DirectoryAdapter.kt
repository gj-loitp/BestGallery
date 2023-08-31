package com.roy.gallery.pro.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.roy.gallery.pro.R
import com.roy.gallery.pro.dialogs.ExcludeFolderDialog
import com.roy.gallery.pro.dialogs.PickMediumDialog
import com.roy.gallery.pro.extensions.*
import com.roy.gallery.pro.helpers.*
import com.roy.gallery.pro.interfaces.DirectoryOperationsListener
import com.roy.gallery.pro.models.AlbumCover
import com.roy.gallery.pro.models.Directory
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.adt.MyRecyclerViewAdapter
import com.roy.commons.dlg.ConfirmationDialog
import com.roy.commons.dlg.PropertiesDialog
import com.roy.commons.dlg.RenameItemDialog
import com.roy.commons.dlg.RenameItemsDialog
import com.roy.commons.ext.applyColorFilter
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.getFilenameFromPath
import com.roy.commons.ext.handleDeletePasswordProtection
import com.roy.commons.ext.isAStorageRootFolder
import com.roy.commons.ext.isGif
import com.roy.commons.ext.isImageFast
import com.roy.commons.ext.isMediaFile
import com.roy.commons.ext.isRawFast
import com.roy.commons.ext.isSvg
import com.roy.commons.ext.isVideoFast
import com.roy.commons.ext.isVisible
import com.roy.commons.ext.needsStupidWritePermissions
import com.roy.commons.ext.toast
import com.roy.commons.models.FileDirItem
import com.roy.commons.views.FastScroller
import com.roy.commons.views.MyRecyclerView
import kotlinx.android.synthetic.main.v_directory_item_list.view.*
import java.io.File

class DirectoryAdapter(
    activity: BaseSimpleActivity,
    var dirs: ArrayList<Directory>,
    val listener: DirectoryOperationsListener?,
    recyclerView: MyRecyclerView,
    val isPickIntent: Boolean,
    fastScroller: FastScroller? = null,
    itemClick: (Any) -> Unit,
) :
    MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    private val config = activity.config
    private val isListViewType = config.viewTypeFolders == VIEW_TYPE_LIST
    private var pinnedFolders = config.pinnedFolders
    private var scrollHorizontally = config.scrollHorizontally
    private var showMediaCount = config.showMediaCount
    private var animateGifs = config.animateGifs
    private var cropThumbnails = config.cropThumbnails
    private var groupDirectSubfolders = config.groupDirectSubfolders
    private var currentDirectoriesHash = dirs.hashCode()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_directories

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutType =
            if (isListViewType) R.layout.v_directory_item_list else R.layout.v_directory_item_grid
        return createViewHolder(layoutType, parent)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        val dir = dirs.getOrNull(position) ?: return
        holder.bindView(dir, true, !isPickIntent) { itemView, adapterPosition ->
            setupView(itemView, dir)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = dirs.size

    override fun prepareActionMode(menu: Menu) {
        val selectedPaths = getSelectedPaths()
        if (selectedPaths.isEmpty()) {
            return
        }

        val isOneItemSelected = isOneItemSelected()
        menu.apply {
            findItem(R.id.cabRename).isVisible =
                !selectedPaths.contains(FAVORITES) && !selectedPaths.contains(RECYCLE_BIN)
            findItem(R.id.cabChangeCoverImage).isVisible = isOneItemSelected

            findItem(R.id.cabEmptyRecycleBin).isVisible =
                isOneItemSelected && selectedPaths.first() == RECYCLE_BIN
            findItem(R.id.cabEmptyDisableRecycleBin).isVisible =
                isOneItemSelected && selectedPaths.first() == RECYCLE_BIN

            checkHideBtnVisibility(this, selectedPaths)
            checkPinBtnVisibility(this, selectedPaths)
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cabProperties -> showProperties()
            R.id.cabRename -> renameDir()
            R.id.cabPin -> pinFolders(true)
            R.id.cabUnpin -> pinFolders(false)
            R.id.cabEmptyRecycleBin -> tryEmptyRecycleBin(true)
            R.id.cabEmptyDisableRecycleBin -> emptyAndDisableRecycleBin()
            R.id.cabHide -> toggleFoldersVisibility(true)
            R.id.cabUnhide -> toggleFoldersVisibility(false)
            R.id.cabExclude -> tryExcludeFolder()
            R.id.cabCopyTo -> copyMoveTo(true)
            R.id.cabMoveTo -> moveFilesTo()
            R.id.cabSelectAll -> selectAll()
            R.id.cabDelete -> askConfirmDelete()
            R.id.cabSelectPhoto -> changeAlbumCover(false)
            R.id.cabUseDefault -> changeAlbumCover(true)
        }
    }

    override fun getSelectableItemCount() = dirs.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = dirs.getOrNull(position)?.path?.hashCode()

    override fun getItemKeyPosition(key: Int) = dirs.indexOfFirst { it.path.hashCode() == key }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed) {
            Glide.with(activity).clear(holder.itemView.dirThumbnail!!)
        }
    }

    private fun checkHideBtnVisibility(menu: Menu, selectedPaths: ArrayList<String>) {
        menu.findItem(R.id.cabHide).isVisible =
            selectedPaths.any { !File(it).doesThisOrParentHaveNoMedia() }
        menu.findItem(R.id.cabUnhide).isVisible =
            selectedPaths.any { File(it).doesThisOrParentHaveNoMedia() }
    }

    private fun checkPinBtnVisibility(menu: Menu, selectedPaths: ArrayList<String>) {
        val pinnedFolders = config.pinnedFolders
        menu.findItem(R.id.cabPin).isVisible = selectedPaths.any { !pinnedFolders.contains(it) }
        menu.findItem(R.id.cabUnpin).isVisible = selectedPaths.any { pinnedFolders.contains(it) }
    }

    private fun showProperties() {
        if (selectedKeys.size <= 1) {
            val path = getFirstSelectedItemPath() ?: return
            if (path != FAVORITES && path != RECYCLE_BIN) {
                PropertiesDialog(activity, path, config.shouldShowHidden)
            }
        } else {
            PropertiesDialog(
                activity,
                getSelectedPaths().filter { it != FAVORITES && it != RECYCLE_BIN }.toMutableList(),
                config.shouldShowHidden
            )
        }
    }

    private fun renameDir() {
        if (selectedKeys.size == 1) {
            val firstDir = getFirstSelectedItem() ?: return
            val sourcePath = firstDir.path
            val dir = File(sourcePath)
            if (activity.isAStorageRootFolder(dir.absolutePath)) {
                activity.toast(R.string.rename_folder_root)
                return
            }

            RenameItemDialog(activity, dir.absolutePath) {
                activity.runOnUiThread {
                    firstDir.apply {
                        path = it
                        name = it.getFilenameFromPath()
                        tmb = File(it, tmb.getFilenameFromPath()).absolutePath
                    }
                    updateDirs(dirs)
                    Thread {
                        activity.galleryDB.DirectoryDao().updateDirectoryAfterRename(
                            firstDir.tmb,
                            firstDir.name,
                            firstDir.path,
                            sourcePath
                        )
                        listener?.refreshItems()
                    }.start()
                }
            }
        } else {
            val paths =
                getSelectedPaths().filter { !activity.isAStorageRootFolder(it) } as ArrayList<String>
            RenameItemsDialog(activity, paths) {
                listener?.refreshItems()
            }
        }
    }

    private fun toggleFoldersVisibility(hide: Boolean) {
        val selectedPaths = getSelectedPaths()
        if (hide && selectedPaths.contains(RECYCLE_BIN)) {
            config.showRecycleBinAtFolders = false
            if (selectedPaths.size == 1) {
                listener?.refreshItems()
                finishActMode()
            }
        }

        selectedPaths.filter { it != FAVORITES && it != RECYCLE_BIN }.forEach {
            val path = it
            if (hide) {
                if (config.wasHideFolderTooltipShown) {
                    hideFolder(path)
                } else {
                    config.wasHideFolderTooltipShown = true
                    ConfirmationDialog(
                        activity,
                        activity.getString(R.string.hide_folder_description)
                    ) {
                        hideFolder(path)
                    }
                }
            } else {
                activity.removeNoMedia(path) {
                    if (activity.config.shouldShowHidden) {
                        updateFolderNames()
                    } else {
                        activity.runOnUiThread {
                            listener?.refreshItems()
                            finishActMode()
                        }
                    }
                }
            }
        }
    }

    private fun tryEmptyRecycleBin(askConfirmation: Boolean) {
        if (askConfirmation) {
            activity.showRecycleBinEmptyingDialog {
                emptyRecycleBin()
            }
        } else {
            emptyRecycleBin()
        }
    }

    private fun emptyRecycleBin() {
        activity.emptyTheRecycleBin {
            listener?.refreshItems()
        }
    }

    private fun emptyAndDisableRecycleBin() {
        activity.showRecycleBinEmptyingDialog {
            activity.emptyAndDisableTheRecycleBin {
                listener?.refreshItems()
            }
        }
    }

    private fun updateFolderNames() {
        val includedFolders = activity.config.includedFolders
        val hidden = activity.getString(R.string.hidden)
        dirs.forEach {
            it.name = activity.checkAppendingHidden(it.path, hidden, includedFolders)
        }
        listener?.updateDirectories(dirs.toMutableList() as ArrayList)
        activity.runOnUiThread {
            updateDirs(dirs)
        }
    }

    private fun hideFolder(path: String) {
        activity.addNoMedia(path) {
            if (activity.config.shouldShowHidden) {
                updateFolderNames()
            } else {
                val affectedPositions = ArrayList<Int>()
                val includedFolders = activity.config.includedFolders
                val newDirs = dirs.filterIndexed { index, directory ->
                    val removeDir =
                        File(directory.path).doesThisOrParentHaveNoMedia() && !includedFolders.contains(
                            directory.path
                        )
                    if (removeDir) {
                        affectedPositions.add(index)
                    }
                    !removeDir
                } as ArrayList<Directory>

                activity.runOnUiThread {
                    affectedPositions.sortedDescending().forEach {
                        notifyItemRemoved(it)
                    }

                    currentDirectoriesHash = newDirs.hashCode()
                    dirs = newDirs

                    finishActMode()
                    listener?.updateDirectories(newDirs)
                }
            }
        }
    }

    private fun tryExcludeFolder() {
        val selectedPaths = getSelectedPaths()
        val paths =
            selectedPaths.filter { it != PATH && it != RECYCLE_BIN && it != FAVORITES }.toSet()
        if (selectedPaths.contains(RECYCLE_BIN)) {
            config.showRecycleBinAtFolders = false
            if (selectedPaths.size == 1) {
                listener?.refreshItems()
                finishActMode()
            }
        }

        if (paths.size == 1) {
            ExcludeFolderDialog(activity, paths.toMutableList()) {
                listener?.refreshItems()
                finishActMode()
            }
        } else if (paths.size > 1) {
            activity.config.addExcludedFolders(paths)
            listener?.refreshItems()
            finishActMode()
        }
    }

    private fun pinFolders(pin: Boolean) {
        if (pin) {
            config.addPinnedFolders(getSelectedPaths().toHashSet())
        } else {
            config.removePinnedFolders(getSelectedPaths().toHashSet())
        }

        currentDirectoriesHash = 0
        pinnedFolders = config.pinnedFolders
        listener?.recheckPinnedFolders()
    }

    private fun moveFilesTo() {
        activity.handleDeletePasswordProtection {
            copyMoveTo(false)
        }
    }

    private fun copyMoveTo(isCopyOperation: Boolean) {
        val paths = ArrayList<String>()
        val showHidden = activity.config.shouldShowHidden
        getSelectedPaths().forEach {
            val filter = config.filterMedia
            File(it).listFiles()?.filter {
                !File(it.absolutePath).isDirectory &&
                        it.absolutePath.isMediaFile() && (showHidden || !it.name.startsWith('.')) &&
                        ((it.isImageFast() && filter and TYPE_IMAGES != 0) ||
                                (it.isVideoFast() && filter and TYPE_VIDEOS != 0) ||
                                (it.isGif() && filter and TYPE_GIFS != 0) ||
                                (it.isRawFast() && filter and TYPE_RAWS != 0) ||
                                (it.isSvg() && filter and TYPE_SVGS != 0))
            }?.mapTo(paths) { it.absolutePath }
        }

        val fileDirItems =
            paths.map { FileDirItem(it, it.getFilenameFromPath()) } as ArrayList<FileDirItem>
        activity.tryCopyMoveFilesTo(fileDirItems, isCopyOperation) {
            config.tempFolderPath = ""
            listener?.refreshItems()
            finishActMode()
        }
    }

    private fun askConfirmDelete() {
        when {
            config.isDeletePasswordProtectionOn -> activity.handleDeletePasswordProtection {
                deleteFolders()
            }

            config.skipDeleteConfirmation -> deleteFolders()
            else -> {
                val itemsCnt = selectedKeys.size
                val items = if (itemsCnt == 1) {
                    "\"${getSelectedPaths().first().getFilenameFromPath()}\""
                } else {
                    resources.getQuantityString(R.plurals.delete_items, itemsCnt, itemsCnt)
                }

                val fileDirItem = getFirstSelectedItem() ?: return
                val baseString =
                    if (!config.useRecycleBin || (isOneItemSelected() && fileDirItem.isRecycleBin()) || (isOneItemSelected() && fileDirItem.areFavorites())) {
                        R.string.deletion_confirmation
                    } else {
                        R.string.move_to_recycle_bin_confirmation
                    }

                var question = String.format(resources.getString(baseString), items)
                val warning =
                    resources.getQuantityString(R.plurals.delete_warning, itemsCnt, itemsCnt)
                question += "\n\n$warning"
                ConfirmationDialog(activity, question) {
                    deleteFolders()
                }
            }
        }
    }

    private fun deleteFolders() {
        if (selectedKeys.isEmpty()) {
            return
        }

        var SAFPath = ""
        val selectedDirs = getSelectedItems()
        selectedDirs.forEach {
            val path = it.path
            if (activity.needsStupidWritePermissions(path) && config.treeUri.isEmpty()) {
                SAFPath = path
            }
        }

        activity.handleSAFDialog(SAFPath) {
            val foldersToDelete = ArrayList<File>(selectedKeys.size)
            selectedDirs.forEach {
                if (it.areFavorites() || it.isRecycleBin()) {
                    if (it.isRecycleBin()) {
                        tryEmptyRecycleBin(false)
                    } else {
                        Thread {
                            activity.galleryDB.MediumDao().clearFavorites()
                            listener?.refreshItems()
                        }.start()
                    }

                    if (selectedKeys.size == 1) {
                        finishActMode()
                    }
                } else {
                    foldersToDelete.add(File(it.path))
                }
            }

            listener?.deleteFolders(foldersToDelete)
        }
    }

    private fun changeAlbumCover(useDefault: Boolean) {
        if (selectedKeys.size != 1)
            return

        val path = getFirstSelectedItemPath() ?: return

        if (useDefault) {
            val albumCovers = getAlbumCoversWithout(path)
            storeCovers(albumCovers)
        } else {
            pickMediumFrom(path, path)
        }
    }

    private fun pickMediumFrom(targetFolder: String, path: String) {
        PickMediumDialog(activity, path) {
            if (File(it).isDirectory) {
                pickMediumFrom(targetFolder, it)
            } else {
                val albumCovers = getAlbumCoversWithout(path)
                val cover = AlbumCover(targetFolder, it)
                albumCovers.add(cover)
                storeCovers(albumCovers)
            }
        }
    }

    private fun getAlbumCoversWithout(path: String) =
        config.parseAlbumCovers().filterNot { it.path == path } as ArrayList

    private fun storeCovers(albumCovers: ArrayList<AlbumCover>) {
        activity.config.albumCovers = Gson().toJson(albumCovers)
        finishActMode()
        listener?.refreshItems()
    }

    private fun getSelectedItems() =
        dirs.filter { selectedKeys.contains(it.path.hashCode()) } as ArrayList<Directory>

    private fun getSelectedPaths() = getSelectedItems().map { it.path } as ArrayList<String>

    private fun getFirstSelectedItem() = getItemWithKey(selectedKeys.first())

    private fun getFirstSelectedItemPath() = getFirstSelectedItem()?.path

    private fun getItemWithKey(key: Int): Directory? =
        dirs.firstOrNull { it.path.hashCode() == key }

    fun updateDirs(newDirs: ArrayList<Directory>) {
        val directories = newDirs.clone() as ArrayList<Directory>
        if (directories.hashCode() != currentDirectoriesHash) {
            currentDirectoriesHash = directories.hashCode()
            dirs = directories
            notifyDataSetChanged()
            finishActMode()
        }
    }

    fun updateAnimateGifs(animateGifs: Boolean) {
        this.animateGifs = animateGifs
        notifyDataSetChanged()
    }

    fun updateCropThumbnails(cropThumbnails: Boolean) {
        this.cropThumbnails = cropThumbnails
        notifyDataSetChanged()
    }

    fun updateShowMediaCount(showMediaCount: Boolean) {
        this.showMediaCount = showMediaCount
        notifyDataSetChanged()
    }

    private fun setupView(view: View, directory: Directory) {
        val isSelected = selectedKeys.contains(directory.path.hashCode())
        view.apply {
            dirName.text =
                if (groupDirectSubfolders) "${directory.name} (${directory.subfoldersCount})" else directory.name
            dirPath?.text = "${directory.path.substringBeforeLast("/")}/"
            photoCnt.text = directory.subfoldersMediaCount.toString()
            val thumbnailType = when {
                directory.tmb.isVideoFast() -> TYPE_VIDEOS
                directory.tmb.isGif() -> TYPE_GIFS
                directory.tmb.isRawFast() -> TYPE_RAWS
                directory.tmb.isSvg() -> TYPE_SVGS
                else -> TYPE_IMAGES
            }

            dirCheck?.beVisibleIf(isSelected)
            if (isSelected) {
                dirCheck.background?.applyColorFilter(primaryColor)
            }

            activity.loadImage(
                thumbnailType,
                directory.tmb,
                dirThumbnail,
                scrollHorizontally,
                animateGifs,
                cropThumbnails
            )
            dirPin.beVisibleIf(pinnedFolders.contains(directory.path))
            dirLocation.beVisibleIf(directory.location != LOCAITON_INTERNAL)
            if (dirLocation.isVisible()) {
                dirLocation.setImageResource(if (directory.location == LOCATION_SD) R.drawable.ic_sd_card else R.drawable.ic_usb)
            }

            photoCnt.beVisibleIf(showMediaCount)

            if (isListViewType) {
                dirName.setTextColor(textColor)
                dirPath.setTextColor(textColor)
                photoCnt.setTextColor(textColor)
                dirPin.applyColorFilter(textColor)
                dirLocation.applyColorFilter(textColor)
            }
        }
    }
}
