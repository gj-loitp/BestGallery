package com.roy.gallery.pro.activities

import android.app.Activity
import android.app.SearchManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.roy.gallery.pro.R
import com.roy.gallery.pro.databases.GalleryDatabase
import com.roy.gallery.pro.dialogs.ChangeGroupingDialog
import com.roy.gallery.pro.dialogs.ChangeSortingDialog
import com.roy.gallery.pro.dialogs.ChangeViewTypeDialog
import com.roy.gallery.pro.dialogs.ExcludeFolderDialog
import com.roy.gallery.pro.dialogs.FilterMediaDialog
import com.roy.gallery.pro.extensions.addNoMedia
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.extensions.containsNoMedia
import com.roy.gallery.pro.extensions.deleteDBPath
import com.roy.gallery.pro.extensions.emptyAndDisableTheRecycleBin
import com.roy.gallery.pro.extensions.emptyTheRecycleBin
import com.roy.gallery.pro.extensions.galleryDB
import com.roy.gallery.pro.extensions.getCachedMedia
import com.roy.gallery.pro.extensions.getHumanizedFilename
import com.roy.gallery.pro.extensions.isDownloadsFolder
import com.roy.gallery.pro.extensions.launchAbout
import com.roy.gallery.pro.extensions.launchCamera
import com.roy.gallery.pro.extensions.launchSettings
import com.roy.gallery.pro.extensions.movePathsInRecycleBin
import com.roy.gallery.pro.extensions.openPath
import com.roy.gallery.pro.extensions.recycleBinPath
import com.roy.gallery.pro.extensions.removeNoMedia
import com.roy.gallery.pro.extensions.restoreRecycleBinPaths
import com.roy.gallery.pro.extensions.showRecycleBinEmptyingDialog
import com.roy.gallery.pro.extensions.tryDeleteFileDirItem
import com.roy.gallery.pro.extensions.updateWidgets
import com.roy.gallery.pro.helpers.DIRECTORY
import com.roy.gallery.pro.helpers.FAVORITES
import com.roy.gallery.pro.helpers.GET_ANY_INTENT
import com.roy.gallery.pro.helpers.GET_IMAGE_INTENT
import com.roy.gallery.pro.helpers.GET_VIDEO_INTENT
import com.roy.gallery.pro.helpers.GROUP_BY_NONE
import com.roy.gallery.pro.helpers.MAX_COLUMN_COUNT
import com.roy.gallery.pro.helpers.MediaFetcher
import com.roy.gallery.pro.helpers.PATH
import com.roy.gallery.pro.helpers.PICKED_PATHS
import com.roy.gallery.pro.helpers.RECYCLE_BIN
import com.roy.gallery.pro.helpers.SET_WALLPAPER_INTENT
import com.roy.gallery.pro.helpers.SHOW_ALL
import com.roy.gallery.pro.helpers.SHOW_FAVORITES
import com.roy.gallery.pro.helpers.SHOW_RECYCLE_BIN
import com.roy.gallery.pro.helpers.SHOW_TEMP_HIDDEN_DURATION
import com.roy.gallery.pro.helpers.SLIDESHOW_START_ON_ENTER
import com.roy.gallery.pro.helpers.VIEW_TYPE_GRID
import com.roy.gallery.pro.interfaces.DirectoryDao
import com.roy.gallery.pro.interfaces.MediaOperationsListener
import com.roy.gallery.pro.interfaces.MediumDao
import com.roy.gallery.pro.models.Medium
import com.roy.gallery.pro.models.ThumbnailItem
import com.roy.gallery.pro.models.ThumbnailSection
import com.roy.commons.dlg.ConfirmationDialog
import com.roy.commons.ext.beGoneIf
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.deleteFiles
import com.roy.commons.ext.getAdjustedPrimaryColor
import com.roy.commons.ext.getFilenameFromPath
import com.roy.commons.ext.getLatestMediaByDateId
import com.roy.commons.ext.getLatestMediaId
import com.roy.commons.ext.handleHiddenFolderPasswordProtection
import com.roy.commons.ext.isGone
import com.roy.commons.ext.isMediaFile
import com.roy.commons.ext.isVideoFast
import com.roy.commons.ext.isVisible
import com.roy.commons.ext.onGlobalLayout
import com.roy.commons.ext.showErrorToast
import com.roy.commons.ext.toast
import com.roy.commons.ext.updateActionBarTitle
import com.roy.commons.helpers.PERMISSION_WRITE_STORAGE
import com.roy.commons.helpers.REQUEST_EDIT_IMAGE
import com.roy.commons.helpers.SORT_BY_RANDOM
import com.roy.commons.models.FileDirItem
import com.roy.commons.views.MyGridLayoutManager
import com.roy.commons.views.MyRecyclerView
import com.wang.avi.AVLoadingIndicatorView
import kotlinx.android.synthetic.main.activity_media.media_empty_text
import kotlinx.android.synthetic.main.activity_media.media_empty_text_label
import kotlinx.android.synthetic.main.activity_media.mediaGrid
import kotlinx.android.synthetic.main.activity_media.mediaHorizontalFastScroller
import kotlinx.android.synthetic.main.activity_media.media_refresh_layout
import kotlinx.android.synthetic.main.activity_media.mediaVerticalFastScroller
import kotlinx.android.synthetic.main.activity_media.viewStub
import java.io.File
import java.io.IOException

class MediaActivity : SimpleActivity(), MediaOperationsListener {
    private val LAST_MEDIA_CHECK_PERIOD = 3000L

    private var mPath = ""
    private var mIsGetImageIntent = false
    private var mIsGetVideoIntent = false
    private var mIsGetAnyIntent = false
    private var mIsGettingMedia = false
    private var mAllowPickingMultiple = false
    private var mShowAll = false
    private var mLoadedInitialPhotos = false
    private var mIsSearchOpen = false
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mCurrAsyncTask: com.roy.gallery.pro.asynctasks.GetMediaAsynctask? = null
    private var mZoomListener: MyRecyclerView.MyZoomListener? = null
    private var mSearchMenuItem: MenuItem? = null
    private var loadingMedia: AVLoadingIndicatorView? = null

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredShowInfoBubble = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0

    private lateinit var mMediumDao: MediumDao
    private lateinit var mDirectoryDao: DirectoryDao

    companion object {
        var mMedia = ArrayList<ThumbnailItem>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media)

        mMediumDao = galleryDB.MediumDao()
        mDirectoryDao = galleryDB.DirectoryDao()

        intent.apply {
            mIsGetImageIntent = getBooleanExtra(GET_IMAGE_INTENT, false)
            mIsGetVideoIntent = getBooleanExtra(GET_VIDEO_INTENT, false)
            mIsGetAnyIntent = getBooleanExtra(GET_ANY_INTENT, false)
            mAllowPickingMultiple = getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }

        media_refresh_layout.setColorSchemeResources(R.color.color_primary)
        media_refresh_layout.setOnRefreshListener { getMedia() }
        try {
            mPath = intent.getStringExtra(DIRECTORY) ?: ""
        } catch (e: Exception) {
            showErrorToast(e)
            finish()
            return
        }

        storeStateVariables()

        if (mShowAll) {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            registerFileUpdateListener()
            val loadingRoot: View = viewStub.inflate()
            loadingMedia = loadingRoot.findViewById<AVLoadingIndicatorView>(R.id.loadingMedia)
        }

        media_empty_text.setOnClickListener {
            showFilterMediaDialog()
        }

        updateWidgets()
    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        if (mStoredAnimateGifs != config.animateGifs) {
            getMediaAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getMediaAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            mediaGrid.adapter = null
            getMedia()
        }

        if (mStoredTextColor != config.textColor) {
            getMediaAdapter()?.updateTextColor(config.textColor)
        }

        if (mStoredPrimaryColor != config.primaryColor) {
            getMediaAdapter()?.updatePrimaryColor(config.primaryColor)
            mediaHorizontalFastScroller.updatePrimaryColor()
            mediaVerticalFastScroller.updatePrimaryColor()
        }

        mediaHorizontalFastScroller.updateBubbleColors()
        mediaVerticalFastScroller.updateBubbleColors()
        mediaHorizontalFastScroller.allowBubbleDisplay = config.showInfoBubble
        mediaVerticalFastScroller.allowBubbleDisplay = config.showInfoBubble
        media_refresh_layout.isEnabled = config.enablePullToRefresh
        invalidateOptionsMenu()
        media_empty_text_label.setTextColor(config.textColor)
        media_empty_text.setTextColor(getAdjustedPrimaryColor())

        if (mMedia.isEmpty() || config.getFileSorting(mPath) and SORT_BY_RANDOM == 0) {
            tryLoadGallery()
        }
    }

    override fun onPause() {
        super.onPause()
        mIsGettingMedia = false
        media_refresh_layout.isRefreshing = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)

        if (!mMedia.isEmpty()) {
            mCurrAsyncTask?.stopFetching()
        }
    }

    override fun onStop() {
        super.onStop()
        mSearchMenuItem?.collapseActionView()

        if (config.temporarilyShowHidden || config.tempSkipDeleteConfirmation) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.tempSkipDeleteConfirmation = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (config.showAll && !isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.tempSkipDeleteConfirmation = false
            unregisterFileUpdateListener()
            GalleryDatabase.destroyInstance()
        }

        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        mMedia.clear()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_media, menu)

        val isFolderHidden = File(mPath).containsNoMedia()
        menu.apply {
            findItem(R.id.group).isVisible = !config.scrollHorizontally

            findItem(R.id.hideFolder).isVisible = !isFolderHidden && !mShowAll && mPath != FAVORITES && mPath != RECYCLE_BIN
            findItem(R.id.unhideFolder).isVisible = isFolderHidden && !mShowAll && mPath != FAVORITES && mPath != RECYCLE_BIN
            findItem(R.id.excludeFolder).isVisible = !mShowAll && mPath != FAVORITES && mPath != RECYCLE_BIN

            findItem(R.id.emptyRecycleBin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.emptyDisableRecycleBin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.restoreAllFiles).isVisible = mPath == RECYCLE_BIN

            findItem(R.id.folderView).isVisible = mShowAll
            findItem(R.id.openCamera).isVisible = mShowAll
            findItem(R.id.about).isVisible = false

            findItem(R.id.temporarilyShowHidden).isVisible = !config.shouldShowHidden
            findItem(R.id.stopShowingHidden).isVisible = config.temporarilyShowHidden

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            findItem(R.id.increaseColumnCount).isVisible = viewType == VIEW_TYPE_GRID && config.mediaColumnCnt < MAX_COLUMN_COUNT
            findItem(R.id.reduceColumnCount).isVisible = viewType == VIEW_TYPE_GRID && config.mediaColumnCnt > 1
            findItem(R.id.toggleFilename).isVisible = viewType == VIEW_TYPE_GRID
        }

        setupSearch(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sort -> showSortingDialog()
            R.id.filter -> showFilterMediaDialog()
            R.id.emptyRecycleBin -> emptyRecycleBin()
            R.id.emptyDisableRecycleBin -> emptyAndDisableRecycleBin()
            R.id.restoreAllFiles -> restoreAllFiles()
            R.id.toggleFilename -> toggleFilenameVisibility()
            R.id.openCamera -> launchCamera()
            R.id.folderView -> switchToFolderView()
            R.id.changeViewType -> changeViewType()
            R.id.group -> showGroupByDialog()
            R.id.hideFolder -> tryHideFolder()
            R.id.unhideFolder -> unhideFolder()
            R.id.excludeFolder -> tryExcludeFolder()
            R.id.temporarilyShowHidden -> tryToggleTemporarilyShowHidden()
            R.id.stopShowingHidden -> tryToggleTemporarilyShowHidden()
            R.id.increaseColumnCount -> increaseColumnCount()
            R.id.reduceColumnCount -> reduceColumnCount()
            R.id.slideshow -> startSlideshow()
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun startSlideshow() {
        if (mMedia.isNotEmpty()) {
            Intent(this, ViewPagerActivity::class.java).apply {
                val item = mMedia.firstOrNull { it is Medium } as? Medium
                        ?: return
                putExtra(PATH, item.path)
                putExtra(SHOW_ALL, mShowAll)
                putExtra(SLIDESHOW_START_ON_ENTER, true)
                startActivity(this)
            }
        }
    }

    private fun storeStateVariables() {
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredShowInfoBubble = showInfoBubble
            mStoredTextColor = textColor
            mStoredPrimaryColor = primaryColor
            mShowAll = showAll
        }
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)
        (mSearchMenuItem?.actionView as? SearchView)?.apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (mIsSearchOpen) {
                        searchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                mIsSearchOpen = true
                media_refresh_layout.isEnabled = false
                return true
            }

            // this triggers on device rotation too, avoid doing anything
            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                if (mIsSearchOpen) {
                    mIsSearchOpen = false
                    media_refresh_layout.isEnabled = config.enablePullToRefresh
                    searchQueryChanged("")
                }
                return true
            }
        })
    }

    private fun searchQueryChanged(text: String) {
        Thread {
            try {
                val filtered = mMedia.filter { it is Medium && it.name.contains(text, true) } as ArrayList
                filtered.sortBy { it is Medium && !it.name.startsWith(text, true) }
                val grouped = MediaFetcher(applicationContext).groupMedia(filtered as ArrayList<Medium>, mPath)
                runOnUiThread {
                    getMediaAdapter()?.updateMedia(grouped)
                    measureRecyclerViewContent(grouped)
                }
            } catch (ignored: Exception) {
            }
        }.start()
    }

    private fun tryLoadGallery() {
        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                val dirName = when {
                    mPath == FAVORITES -> getString(R.string.favorites)
                    mPath == RECYCLE_BIN -> getString(R.string.recycle_bin)
                    mPath == config.OTGPath -> getString(R.string.usb)
                    else -> getHumanizedFilename(mPath)
                }
                updateActionBarTitle(if (mShowAll) resources.getString(R.string.all_folders) else dirName)
                getMedia()
                setupLayoutManager()
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    private fun getMediaAdapter() = mediaGrid.adapter as? com.roy.gallery.pro.adapters.MediaAdapter

    private fun setupAdapter() {
        if (!mShowAll && isDirEmpty()) {
            return
        }

        val currAdapter = mediaGrid.adapter
        if (currAdapter == null) {
            initZoomListener()
            val fastscroller = if (config.scrollHorizontally) mediaHorizontalFastScroller else mediaVerticalFastScroller
            com.roy.gallery.pro.adapters.MediaAdapter(this, mMedia.clone() as ArrayList<ThumbnailItem>, this, mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent,
                    mAllowPickingMultiple, mPath, mediaGrid, fastscroller) {
                if (it is Medium) {
                    itemClicked(it.path)
                }
            }.apply {
                setupZoomListener(mZoomListener)
                mediaGrid.adapter = this
            }
            setupLayoutManager()
        } else {
            (currAdapter as com.roy.gallery.pro.adapters.MediaAdapter).updateMedia(mMedia)
        }

        measureRecyclerViewContent(mMedia)
        setupScrollDirection()
    }

    private fun setupScrollDirection() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        val allowHorizontalScroll = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
        mediaVerticalFastScroller.isHorizontal = false
        mediaVerticalFastScroller.beGoneIf(allowHorizontalScroll)

        mediaHorizontalFastScroller.isHorizontal = true
        mediaHorizontalFastScroller.beVisibleIf(allowHorizontalScroll)

        val sorting = config.getFileSorting(if (mShowAll) SHOW_ALL else mPath)
        if (allowHorizontalScroll) {
            mediaHorizontalFastScroller.allowBubbleDisplay = config.showInfoBubble
            mediaHorizontalFastScroller.setViews(mediaGrid, media_refresh_layout) {
                mediaHorizontalFastScroller.updateBubbleText(getBubbleTextItem(it, sorting))
            }
        } else {
            mediaVerticalFastScroller.allowBubbleDisplay = config.showInfoBubble
            mediaVerticalFastScroller.setViews(mediaGrid, media_refresh_layout) {
                mediaVerticalFastScroller.updateBubbleText(getBubbleTextItem(it, sorting))
            }
        }
    }

    private fun getBubbleTextItem(index: Int, sorting: Int): String {
        var realIndex = index
        val mediaAdapter = getMediaAdapter()
        if (mediaAdapter?.isASectionTitle(index) == true) {
            realIndex++
        }
        return mediaAdapter?.getItemBubbleText(realIndex, sorting) ?: ""
    }

    private fun checkLastMediaChanged() {
        if (isDestroyed || config.getFileSorting(mPath) and SORT_BY_RANDOM != 0) {
            return
        }

        mLastMediaHandler.removeCallbacksAndMessages(null)
        mLastMediaHandler.postDelayed({
            Thread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getMedia()
                    }
                } else {
                    checkLastMediaChanged()
                }
            }.start()
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, false, true, mPath) {
            mLoadedInitialPhotos = false
            mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            mLoadedInitialPhotos = false
            media_refresh_layout.isRefreshing = true
            mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun emptyRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyTheRecycleBin {
                finish()
            }
        }
    }

    private fun emptyAndDisableRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyAndDisableTheRecycleBin {
                finish()
            }
        }
    }

    private fun restoreAllFiles() {
        val paths = mMedia.filter { it is Medium }.map { (it as Medium).path } as ArrayList<String>
        restoreRecycleBinPaths(paths, mMediumDao) {
            Thread {
                mDirectoryDao.deleteDirPath(RECYCLE_BIN)
            }.start()
            finish()
        }
    }

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        getMediaAdapter()?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun switchToFolderView() {
        config.showAll = false
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, false, mPath) {
            invalidateOptionsMenu()
            setupLayoutManager()
            mediaGrid.adapter = null
            setupAdapter()
        }
    }

    private fun showGroupByDialog() {
        ChangeGroupingDialog(this, mPath) {
            mLoadedInitialPhotos = false
            mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun tryHideFolder() {
        if (config.wasHideFolderTooltipShown) {
            hideFolder()
        } else {
            ConfirmationDialog(this, getString(R.string.hide_folder_description)) {
                config.wasHideFolderTooltipShown = true
                hideFolder()
            }
        }
    }

    private fun hideFolder() {
        addNoMedia(mPath) {
            runOnUiThread {
                if (!config.shouldShowHidden) {
                    finish()
                } else {
                    invalidateOptionsMenu()
                }
            }
        }
    }

    private fun unhideFolder() {
        removeNoMedia(mPath) {
            runOnUiThread {
                invalidateOptionsMenu()
            }
        }
    }

    private fun tryExcludeFolder() {
        ExcludeFolderDialog(this, arrayListOf(mPath)) {
            finish()
        }
    }

    private fun deleteDirectoryIfEmpty() {
        val fileDirItem = FileDirItem(mPath, mPath.getFilenameFromPath(), true)
        if (config.deleteEmptyFolders && !fileDirItem.isDownloadsFolder() && fileDirItem.isDirectory && fileDirItem.getProperFileCount(true) == 0) {
            tryDeleteFileDirItem(fileDirItem, true, true)
        }
    }

    private fun getMedia() {
        if (mIsGettingMedia) {
            return
        }

        if (mMedia.size > 0) {
            loadingMedia?.hide()
        } else {
            loadingMedia?.show()
        }

        mIsGettingMedia = true
        if (mLoadedInitialPhotos) {
            startAsyncTask()
        } else {
            getCachedMedia(mPath, mIsGetVideoIntent, mIsGetImageIntent, mMediumDao) {
                if (it.isEmpty()) {
                    runOnUiThread {
                        media_refresh_layout.isRefreshing = true
                    }
                } else {
                    gotMedia(it, true)
                }
                startAsyncTask()
            }
        }

        mLoadedInitialPhotos = true
    }

    private fun startAsyncTask() {
        mCurrAsyncTask?.stopFetching()
        mCurrAsyncTask = com.roy.gallery.pro.asynctasks.GetMediaAsynctask(applicationContext, mPath, mIsGetImageIntent, mIsGetVideoIntent, mShowAll) {
            Thread {
                val oldMedia = mMedia.clone() as ArrayList<ThumbnailItem>
                val newMedia = it
                gotMedia(newMedia, false)
                try {
                    oldMedia.filter { !newMedia.contains(it) }.mapNotNull { it as? Medium }.filter { !File(it.path).exists() }.forEach {
                        mMediumDao.deleteMediumPath(it.path)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }

        try {
            mCurrAsyncTask!!.execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isDirEmpty(): Boolean {
        return if (mMedia.size <= 0 && config.filterMedia > 0) {
            if (mPath != FAVORITES && mPath != RECYCLE_BIN) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
            }

            if (mPath == FAVORITES) {
                Thread {
                    mDirectoryDao.deleteDirPath(FAVORITES)
                }.start()
            }

            finish()
            true
        } else {
            false
        }
    }

    private fun deleteDBDirectory() {
        Thread {
            mDirectoryDao.deleteDirPath(mPath)
        }.start()
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            handleHiddenFolderPasswordProtection {
                toggleTemporarilyShowHidden(true)
            }
        }
    }

    private fun toggleTemporarilyShowHidden(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowHidden = show
        getMedia()
        invalidateOptionsMenu()
    }

    private fun setupLayoutManager() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = mediaGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            media_refresh_layout.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            media_refresh_layout.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        layoutManager.spanCount = config.mediaColumnCnt
        val adapter = getMediaAdapter()
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter?.isASectionTitle(position) == true) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    private fun measureRecyclerViewContent(media: ArrayList<ThumbnailItem>) {
        mediaGrid.onGlobalLayout {
            if (config.scrollHorizontally) {
                calculateContentWidth(media)
            } else {
                calculateContentHeight(media)
            }
        }
    }

    private fun calculateContentWidth(media: ArrayList<ThumbnailItem>) {
        val layoutManager = mediaGrid.layoutManager as MyGridLayoutManager
        val thumbnailWidth = layoutManager.getChildAt(0)?.width ?: 0
        val fullWidth = ((media.size - 1) / layoutManager.spanCount + 1) * thumbnailWidth
        mediaHorizontalFastScroller.setContentWidth(fullWidth)
        mediaHorizontalFastScroller.setScrollToX(mediaGrid.computeHorizontalScrollOffset())
    }

    private fun calculateContentHeight(media: ArrayList<ThumbnailItem>) {
        val layoutManager = mediaGrid.layoutManager as MyGridLayoutManager
        val pathToCheck = if (mPath.isEmpty()) SHOW_ALL else mPath
        val hasSections = config.getFolderGrouping(pathToCheck) and GROUP_BY_NONE == 0 && !config.scrollHorizontally
        val sectionTitleHeight = if (hasSections) layoutManager.getChildAt(0)?.height ?: 0 else 0
        val thumbnailHeight = if (hasSections) layoutManager.getChildAt(1)?.height ?: 0 else layoutManager.getChildAt(0)?.height ?: 0

        var fullHeight = 0
        var curSectionItems = 0
        media.forEach {
            if (it is ThumbnailSection) {
                fullHeight += sectionTitleHeight
                if (curSectionItems != 0) {
                    val rows = ((curSectionItems - 1) / layoutManager.spanCount + 1)
                    fullHeight += rows * thumbnailHeight
                }
                curSectionItems = 0
            } else {
                curSectionItems++
            }
        }

        fullHeight += ((curSectionItems - 1) / layoutManager.spanCount + 1) * thumbnailHeight
        mediaVerticalFastScroller.setContentHeight(fullHeight)
        mediaVerticalFastScroller.setScrollToY(mediaGrid.computeVerticalScrollOffset())
    }

    private fun initZoomListener() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            val layoutManager = mediaGrid.layoutManager as MyGridLayoutManager
            mZoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getMediaAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getMediaAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            mZoomListener = null
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = mediaGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        media_refresh_layout.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mZoomListener = null
    }

    private fun increaseColumnCount() {
        config.mediaColumnCnt = ++(mediaGrid.layoutManager as MyGridLayoutManager).spanCount
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.mediaColumnCnt = --(mediaGrid.layoutManager as MyGridLayoutManager).spanCount
        columnCountChanged()
    }

    private fun columnCountChanged() {
        invalidateOptionsMenu()
        mediaGrid.adapter?.notifyDataSetChanged()
        measureRecyclerViewContent(mMedia)
    }

    private fun isSetWallpaperIntent() = intent.getBooleanExtra(SET_WALLPAPER_INTENT, false)

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_EDIT_IMAGE) {
            if (resultCode == Activity.RESULT_OK && resultData != null) {
                mMedia.clear()
                refreshItems()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun itemClicked(path: String) {
        if (isSetWallpaperIntent()) {
            toast(R.string.setting_wallpaper)

            val wantedWidth = wallpaperDesiredMinimumWidth
            val wantedHeight = wallpaperDesiredMinimumHeight
            val ratio = wantedWidth.toFloat() / wantedHeight

            val options = RequestOptions()
                    .override((wantedWidth * ratio).toInt(), wantedHeight)
                    .fitCenter()

            Glide.with(this)
                    .asBitmap()
                    .load(File(path))
                    .apply(options)
                    .into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            try {
                                WallpaperManager.getInstance(applicationContext).setBitmap(resource)
                                setResult(Activity.RESULT_OK)
                            } catch (ignored: IOException) {
                            }

                            finish()
                        }
                    })
        } else if (mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent) {
            Intent().apply {
                data = Uri.parse(path)
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        } else {
            val isVideo = path.isVideoFast()
            if (isVideo) {
                openPath(path, false)
            } else {
                Intent(this, ViewPagerActivity::class.java).apply {
                    putExtra(PATH, path)
                    putExtra(SHOW_ALL, mShowAll)
                    putExtra(SHOW_FAVORITES, mPath == FAVORITES)
                    putExtra(SHOW_RECYCLE_BIN, mPath == RECYCLE_BIN)
                    startActivity(this)
                }
            }
        }
    }

    private fun gotMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean) {
        mIsGettingMedia = false
        checkLastMediaChanged()
        mMedia = media

        runOnUiThread {
            if (media != null && media.size > 0) {
                loadingMedia?.hide()
            }

            media_refresh_layout.isRefreshing = false
            media_empty_text_label.beVisibleIf(media.isEmpty() && !isFromCache)
            media_empty_text.beVisibleIf(media.isEmpty() && !isFromCache)
            mediaGrid.beVisibleIf(media_empty_text_label.isGone())

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            val allowHorizontalScroll = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
            mediaVerticalFastScroller.beVisibleIf(mediaGrid.isVisible() && !allowHorizontalScroll)
            mediaHorizontalFastScroller.beVisibleIf(mediaGrid.isVisible() && allowHorizontalScroll)
            setupAdapter()
        }

        mLatestMediaId = getLatestMediaId()
        mLatestMediaDateId = getLatestMediaByDateId()
        if (!isFromCache) {
            val mediaToInsert = (mMedia).filter { it is Medium && it.deletedTS == 0L }.map { it as Medium }
            try {
                mMediumDao.insertAll(mediaToInsert)
            } catch (e: Exception) {
            }
        }
    }

    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>) {
        val filtered = fileDirItems.filter { File(it.path).isFile && it.path.isMediaFile() } as ArrayList
        if (filtered.isEmpty()) {
            return
        }

        if (config.useRecycleBin && !filtered.first().path.startsWith(recycleBinPath)) {
            val movingItems = resources.getQuantityString(R.plurals.moving_items_into_bin, filtered.size, filtered.size)
            toast(movingItems)

            movePathsInRecycleBin(filtered.map { it.path } as ArrayList<String>, mMediumDao) {
                if (it) {
                    deleteFilteredFiles(filtered)
                } else {
                    toast(R.string.unknown_error_occurred)
                }
            }
        } else {
            val deletingItems = resources.getQuantityString(R.plurals.deleting_items, filtered.size, filtered.size)
            toast(deletingItems)
            deleteFilteredFiles(filtered)
        }
    }

    private fun deleteFilteredFiles(filtered: ArrayList<FileDirItem>) {
        deleteFiles(filtered) {
            if (!it) {
                toast(R.string.unknown_error_occurred)
                return@deleteFiles
            }

            mMedia.removeAll { filtered.map { it.path }.contains((it as? Medium)?.path) }

            Thread {
                val useRecycleBin = config.useRecycleBin
                filtered.forEach {
                    if (it.path.startsWith(recycleBinPath) || !useRecycleBin) {
                        deleteDBPath(mMediumDao, it.path)
                    }
                }
            }.start()

            if (mMedia.isEmpty()) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
                finish()
            }
        }
    }

    override fun refreshItems() {
        getMedia()
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        Intent().apply {
            putExtra(PICKED_PATHS, paths)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }
}
