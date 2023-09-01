package com.roy.gallery.pro.activities

import android.app.Activity
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.RecyclerView
import com.roy.gallery.pro.BuildConfig
import com.roy.gallery.pro.R
import com.roy.gallery.pro.databases.GalleryDatabase
import com.roy.gallery.pro.dialogs.ChangeSortingDialog
import com.roy.gallery.pro.dialogs.ChangeViewTypeDialog
import com.roy.gallery.pro.dialogs.FilterMediaDialog
import com.roy.gallery.pro.extensions.addTempFolderIfNeeded
import com.roy.gallery.pro.extensions.checkAppendingHidden
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.extensions.deleteDBPath
import com.roy.gallery.pro.extensions.galleryDB
import com.roy.gallery.pro.extensions.getCachedDirectories
import com.roy.gallery.pro.extensions.getCachedMedia
import com.roy.gallery.pro.extensions.getDirMediaTypes
import com.roy.gallery.pro.extensions.getDirsToShow
import com.roy.gallery.pro.extensions.getDistinctPath
import com.roy.gallery.pro.extensions.getFavoritePaths
import com.roy.gallery.pro.extensions.getPathLocation
import com.roy.gallery.pro.extensions.getSortedDirectories
import com.roy.gallery.pro.extensions.launchAbout
import com.roy.gallery.pro.extensions.launchCamera
import com.roy.gallery.pro.extensions.launchSettings
import com.roy.gallery.pro.extensions.movePathsInRecycleBin
import com.roy.gallery.pro.extensions.movePinnedDirectoriesToFront
import com.roy.gallery.pro.extensions.removeInvalidDBDirectories
import com.roy.gallery.pro.extensions.storeDirectoryItems
import com.roy.gallery.pro.extensions.tryDeleteFileDirItem
import com.roy.gallery.pro.extensions.updateDBDirectory
import com.roy.gallery.pro.extensions.updateWidgets
import com.roy.gallery.pro.helpers.DIRECTORY
import com.roy.gallery.pro.helpers.FAVORITES
import com.roy.gallery.pro.helpers.GET_ANY_INTENT
import com.roy.gallery.pro.helpers.GET_IMAGE_INTENT
import com.roy.gallery.pro.helpers.GET_VIDEO_INTENT
import com.roy.gallery.pro.helpers.GROUP_BY_DATE_TAKEN
import com.roy.gallery.pro.helpers.GROUP_DESCENDING
import com.roy.gallery.pro.helpers.MAX_COLUMN_COUNT
import com.roy.gallery.pro.helpers.MONTH_MILLISECONDS
import com.roy.gallery.pro.helpers.MediaFetcher
import com.roy.gallery.pro.helpers.PICKED_PATHS
import com.roy.gallery.pro.helpers.RECYCLE_BIN
import com.roy.gallery.pro.helpers.SET_WALLPAPER_INTENT
import com.roy.gallery.pro.helpers.SHOW_ALL
import com.roy.gallery.pro.helpers.SHOW_TEMP_HIDDEN_DURATION
import com.roy.gallery.pro.helpers.TYPE_GIFS
import com.roy.gallery.pro.helpers.TYPE_IMAGES
import com.roy.gallery.pro.helpers.TYPE_RAWS
import com.roy.gallery.pro.helpers.TYPE_SVGS
import com.roy.gallery.pro.helpers.TYPE_VIDEOS
import com.roy.gallery.pro.helpers.VIEW_TYPE_GRID
import com.roy.gallery.pro.interfaces.DirectoryDao
import com.roy.gallery.pro.interfaces.DirectoryOperationsListener
import com.roy.gallery.pro.interfaces.MediumDao
import com.roy.gallery.pro.jobs.NewPhotoFetcher
import com.roy.gallery.pro.models.AlbumCover
import com.roy.gallery.pro.models.Directory
import com.roy.gallery.pro.models.Medium
import com.roy.commons.dlg.ConfirmationDialog
import com.roy.commons.dlg.CreateNewFolderDialog
import com.roy.commons.dlg.FilePickerDialog
import com.roy.commons.ext.appLaunched
import com.roy.commons.ext.beGone
import com.roy.commons.ext.beGoneIf
import com.roy.commons.ext.beVisible
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.checkWhatsNew
import com.roy.commons.ext.deleteFiles
import com.roy.commons.ext.getAdjustedPrimaryColor
import com.roy.commons.ext.getFilePublicUri
import com.roy.commons.ext.getFilenameFromPath
import com.roy.commons.ext.getLatestMediaByDateId
import com.roy.commons.ext.getLatestMediaId
import com.roy.commons.ext.getMimeType
import com.roy.commons.ext.getStorageDirectories
import com.roy.commons.ext.handleAppPasswordProtection
import com.roy.commons.ext.handleHiddenFolderPasswordProtection
import com.roy.commons.ext.hasOTGConnected
import com.roy.commons.ext.hasPermission
import com.roy.commons.ext.internalStoragePath
import com.roy.commons.ext.isGif
import com.roy.commons.ext.isGone
import com.roy.commons.ext.isImageFast
import com.roy.commons.ext.isMediaFile
import com.roy.commons.ext.isRawFast
import com.roy.commons.ext.isSvg
import com.roy.commons.ext.isVideoFast
import com.roy.commons.ext.isVisible
import com.roy.commons.ext.onGlobalLayout
import com.roy.commons.ext.sdCardPath
import com.roy.commons.ext.showErrorToast
import com.roy.commons.ext.showOTGPermissionDialog
import com.roy.commons.ext.toFileDirItem
import com.roy.commons.ext.toast
import com.roy.commons.helpers.DAY_SECONDS
import com.roy.commons.helpers.PERMISSION_READ_STORAGE
import com.roy.commons.helpers.PERMISSION_WRITE_STORAGE
import com.roy.commons.helpers.SORT_BY_DATE_MODIFIED
import com.roy.commons.helpers.SORT_BY_DATE_TAKEN
import com.roy.commons.helpers.SORT_BY_SIZE
import com.roy.commons.helpers.SORT_DESCENDING
import com.roy.commons.helpers.WAS_PROTECTION_HANDLED
import com.roy.commons.helpers.isNougatPlus
import com.roy.commons.helpers.sumByLong
import com.roy.commons.models.FileDirItem
import com.roy.commons.models.Release
import com.roy.commons.views.MyGridLayoutManager
import com.roy.commons.views.MyRecyclerView
import kotlinx.android.synthetic.main.a_main.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

class MainActivity : SimpleActivity(),
    DirectoryOperationsListener {

    companion object {
        var isClickHome: Boolean = false
        var leaveTime: Long = 0
    }

    private val PICK_MEDIA = 2
    private val PICK_WALLPAPER = 3
    private val LAST_MEDIA_CHECK_PERIOD = 3000L

    private var mIsPickImageIntent = false
    private var mIsPickVideoIntent = false
    private var mIsGetImageContentIntent = false
    private var mIsGetVideoContentIntent = false
    private var mIsGetAnyContentIntent = false
    private var mIsSetWallpaperIntent = false
    private var mAllowPickingMultiple = false
    private var mIsThirdPartyIntent = false
    private var mIsGettingDirs = false
    private var mLoadedInitialPhotos = false
    private var mIsPasswordProtectionPending = false
    private var mWasProtectionHandled = false
    private var mShouldStopFetching = false
    private var mIsSearchOpen = false
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L
    private var mCurrentPathPrefix =
        ""                 // used at "Group direct subfolders" for navigation
    private var mOpenedSubfolders =
        arrayListOf("")     // used at "Group direct subfolders" for navigating Up with the back button
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mZoomListener: MyRecyclerView.MyZoomListener? = null
    private var mSearchMenuItem: MenuItem? = null
    private var mDirs = ArrayList<Directory>()

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredShowMediaCount = true
    private var mStoredShowInfoBubble = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0

    private lateinit var mMediumDao: MediumDao
    private lateinit var mDirectoryDao: DirectoryDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.a_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        mMediumDao = galleryDB.MediumDao()
        mDirectoryDao = galleryDB.DirectoryDao()

        if (savedInstanceState == null) {
            config.temporarilyShowHidden = false
            config.tempSkipDeleteConfirmation = false
            removeTempFolder()
            checkRecycleBinItems()
            startNewPhotoFetcher()
        }

        mIsPickImageIntent = isPickImageIntent(intent)
        mIsPickVideoIntent = isPickVideoIntent(intent)
        mIsGetImageContentIntent = isGetImageContentIntent(intent)
        mIsGetVideoContentIntent = isGetVideoContentIntent(intent)
        mIsGetAnyContentIntent = isGetAnyContentIntent(intent)
        mIsSetWallpaperIntent = isSetWallpaperIntent(intent)
        mAllowPickingMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        mIsThirdPartyIntent =
            mIsPickImageIntent || mIsPickVideoIntent || mIsGetImageContentIntent || mIsGetVideoContentIntent ||
                    mIsGetAnyContentIntent || mIsSetWallpaperIntent

        directoriesRefreshLayout.setOnRefreshListener { getDirectories() }
        directoriesRefreshLayout.setColorSchemeResources(R.color.color_primary)
        storeStateVariables()
        checkWhatsNewDialog()

        directoriesEmptyText.setOnClickListener {
            showFilterMediaDialog()
        }

        mIsPasswordProtectionPending = config.isAppPasswordProtectionOn
        setupLatestMediaId()

        // notify some users about the Clock app
        /*if (System.currentTimeMillis() < 1523750400000 && !config.wasNewAppShown && config.appRunCount > 100 && config.appRunCount % 50 != 0 && !isPackageInstalled(NEW_APP_PACKAGE)) {
            config.wasNewAppShown = true
            NewAppDialog(this, NEW_APP_PACKAGE, "Simple Clock")
        }*/

        if (!config.wereFavoritesPinned) {
            config.addPinnedFolders(hashSetOf(FAVORITES))
            config.wereFavoritesPinned = true
        }

        if (!config.wasRecycleBinPinned) {
            config.addPinnedFolders(hashSetOf(RECYCLE_BIN))
            config.wasRecycleBinPinned = true
            config.saveFolderGrouping(SHOW_ALL, GROUP_BY_DATE_TAKEN or GROUP_DESCENDING)
        }

        if (!config.wasSVGShowingHandled) {
            config.wasSVGShowingHandled = true
            if (config.filterMedia and TYPE_SVGS == 0) {
                config.filterMedia += TYPE_SVGS
            }
        }

        updateWidgets()
        registerFileUpdateListener()
        registerReceiver()
        EventBus.getDefault().register(this)
        SplashActivity.startActivity(this)
    }

    @Subscribe
    fun onEvent(event: Any) {
    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        config.isThirdPartyIntent = false

        if (mStoredAnimateGifs != config.animateGifs) {
            getRecyclerAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getRecyclerAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredShowMediaCount != config.showMediaCount) {
            getRecyclerAdapter()?.updateShowMediaCount(config.showMediaCount)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            directoriesGrid.adapter = null
            getDirectories()
        }

        if (mStoredTextColor != config.textColor) {
            getRecyclerAdapter()?.updateTextColor(config.textColor)
        }

        if (mStoredPrimaryColor != config.primaryColor) {
            getRecyclerAdapter()?.updatePrimaryColor(config.primaryColor)
            directoriesVerticalFastScroller.updatePrimaryColor()
            directoriesHorizontalFastScroller.updatePrimaryColor()
        }

        directoriesHorizontalFastScroller.updateBubbleColors()
        directoriesVerticalFastScroller.updateBubbleColors()
        directoriesHorizontalFastScroller.allowBubbleDisplay = config.showInfoBubble
        directoriesVerticalFastScroller.allowBubbleDisplay = config.showInfoBubble
        directoriesRefreshLayout.isEnabled = config.enablePullToRefresh
        invalidateOptionsMenu()
        directoriesEmptyTextLabel.setTextColor(config.textColor)
        directoriesEmptyText.setTextColor(getAdjustedPrimaryColor())

        if (mIsPasswordProtectionPending && !mWasProtectionHandled) {
            handleAppPasswordProtection {
                mWasProtectionHandled = it
                if (it) {
                    mIsPasswordProtectionPending = false
                    tryLoadGallery()
                } else {
                    finish()
                }
            }
        } else {
            tryLoadGallery()
        }
    }

    override fun onPause() {
        super.onPause()
        directoriesRefreshLayout.isRefreshing = false
        mIsGettingDirs = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)
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
        if (!isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.tempSkipDeleteConfirmation = false
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
            removeTempFolder()
            unregisterFileUpdateListener()

            if (!config.showAll) {
                GalleryDatabase.destroyInstance()
            }
        }
        isClickHome = false
        unregisterReceiver()
        EventBus.getDefault().unregister(this)
    }

    override fun onBackPressed() {
        if (config.groupDirectSubfolders) {
            if (mCurrentPathPrefix.isEmpty()) {
                super.onBackPressed()
            } else {
                mOpenedSubfolders.removeAt(mOpenedSubfolders.size - 1)
                mCurrentPathPrefix = mOpenedSubfolders.last()
                setupAdapter(mDirs)
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (mIsThirdPartyIntent) {
            menuInflater.inflate(R.menu.menu_main_intent, menu)
        } else {
            menuInflater.inflate(R.menu.menu_main, menu)
            val useBin = config.useRecycleBin
            menu.apply {
                findItem(R.id.increaseColumnCount).isVisible =
                    config.viewTypeFolders == VIEW_TYPE_GRID && config.dirColumnCnt < MAX_COLUMN_COUNT
                findItem(R.id.reduceColumnCount).isVisible =
                    config.viewTypeFolders == VIEW_TYPE_GRID && config.dirColumnCnt > 1
                findItem(R.id.hideTheRecycleBin).isVisible =
                    useBin && config.showRecycleBinAtFolders
                findItem(R.id.showTheRecycleBin).isVisible =
                    useBin && !config.showRecycleBinAtFolders
                setupSearch(this)
            }
        }

        menu.findItem(R.id.temporarilyShowHidden).isVisible = !config.shouldShowHidden
        menu.findItem(R.id.stopShowingHidden).isVisible = config.temporarilyShowHidden

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sort -> showSortingDialog()
            R.id.filter -> showFilterMediaDialog()
            R.id.openCamera -> launchCamera()
            R.id.showAll -> showAllMedia()
            R.id.changeViewType -> changeViewType()
            R.id.temporarilyShowHidden -> tryToggleTemporarilyShowHidden()
            R.id.stopShowingHidden -> tryToggleTemporarilyShowHidden()
            R.id.createNewFolder -> createNewFolder()
            R.id.showTheRecycleBin -> toggleRecycleBin(true)
            R.id.hideTheRecycleBin -> toggleRecycleBin(false)
            R.id.increaseColumnCount -> increaseColumnCount()
            R.id.reduceColumnCount -> reduceColumnCount()
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(WAS_PROTECTION_HANDLED, mWasProtectionHandled)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mWasProtectionHandled = savedInstanceState.getBoolean(WAS_PROTECTION_HANDLED, false)
    }

    private fun getRecyclerAdapter() =
        directoriesGrid.adapter as? com.roy.gallery.pro.adapters.DirectoryAdapter

    private fun storeStateVariables() {
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredShowMediaCount = showMediaCount
            mStoredShowInfoBubble = showInfoBubble
            mStoredTextColor = textColor
            mStoredPrimaryColor = primaryColor
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
                        setupAdapter(mDirs, newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(
            mSearchMenuItem,
            object : MenuItemCompat.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                    mIsSearchOpen = true
                    directoriesRefreshLayout.isEnabled = false
                    return true
                }

                // this triggers on device rotation too, avoid doing anything
                override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                    if (mIsSearchOpen) {
                        mIsSearchOpen = false
                        directoriesRefreshLayout.isEnabled = config.enablePullToRefresh
                        setupAdapter(mDirs, "")
                    }
                    return true
                }
            })
    }

    private fun startNewPhotoFetcher() {
        if (isNougatPlus()) {
            val photoFetcher = NewPhotoFetcher()
            if (!photoFetcher.isScheduled(applicationContext)) {
                photoFetcher.scheduleJob(applicationContext)
            }
        }
    }

    private fun removeTempFolder() {
        if (config.tempFolderPath.isNotEmpty()) {
            val newFolder = File(config.tempFolderPath)
            if (newFolder.exists() && newFolder.isDirectory) {
                if (newFolder.list()?.isEmpty() == true) {
                    toast(
                        String.format(getString(R.string.deleting_folder), config.tempFolderPath),
                        Toast.LENGTH_LONG
                    )
                    tryDeleteFileDirItem(newFolder.toFileDirItem(applicationContext), true, true)
                }
            }
            config.tempFolderPath = ""
        }
    }

    private fun checkOTGPath() {
        Thread {
            if (!config.wasOTGHandled && hasPermission(PERMISSION_WRITE_STORAGE) && hasOTGConnected() && config.OTGPath.isEmpty()) {
                getStorageDirectories().firstOrNull {
                    it.trimEnd('/') != internalStoragePath && it.trimEnd(
                        '/'
                    ) != sdCardPath
                }?.apply {
                    config.wasOTGHandled = true
                    val otgPath = trimEnd('/')
                    config.OTGPath = otgPath
                    config.addIncludedFolder(otgPath)
                }

                if (config.OTGPath.isEmpty()) {
                    runOnUiThread {
                        ConfirmationDialog(
                            this,
                            getString(R.string.usb_detected),
                            positive = R.string.ok,
                            negative = 0
                        ) {
                            config.wasOTGHandled = true
                            showOTGPermissionDialog()
                        }
                    }
                }
            }
        }.start()
    }

    private fun checkDefaultSpamFolders() {
        if (!config.spamFoldersChecked) {
            val spamFolders = arrayListOf(
                "/storage/emulated/0/Android/data/com.facebook.orca/files/stickers"
            )

            spamFolders.forEach {
                if (File(it).exists()) {
                    config.addExcludedFolder(it)
                }
            }
            config.spamFoldersChecked = true
        }
    }

    private fun tryLoadGallery() {
        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                checkOTGPath()
                checkDefaultSpamFolders()

                if (config.showAll) {
                    showAllMedia()
                } else {
                    getDirectories()
                }

                setupLayoutManager()
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    private fun getDirectories() {
        if (mIsGettingDirs) {
            return
        }

        if (mDirs.size > 0) {
            loading.hide()
        } else {
            loading.show()
        }

        mShouldStopFetching = true
        mIsGettingDirs = true
        val getImagesOnly = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideosOnly = mIsPickVideoIntent || mIsGetVideoContentIntent

        getCachedDirectories(getVideosOnly, getImagesOnly, mDirectoryDao) {
            gotDirectories(addTempFolderIfNeeded(it))
        }
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, true, false) {
            directoriesGrid.adapter = null
            if (config.directorySorting and SORT_BY_DATE_MODIFIED > 0 || config.directorySorting and SORT_BY_DATE_TAKEN > 0) {
                getDirectories()
            } else {
                Thread {
                    gotDirectories(getCurrentlyDisplayedDirs())
                }.start()
            }
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            mShouldStopFetching = true
            directoriesRefreshLayout.isRefreshing = true
            directoriesGrid.adapter = null
            getDirectories()
        }
    }

    private fun showAllMedia() {
        config.showAll = true
        Intent(this, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, "")

            if (mIsThirdPartyIntent) {
                handleMediaIntent(this)
            } else {
                startActivity(this)
                finish()
            }
        }
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, true) {
            invalidateOptionsMenu()
            setupLayoutManager()
            directoriesGrid.adapter = null
            setupAdapter(mDirs)
        }
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
        directoriesGrid.adapter = null
        getDirectories()
        invalidateOptionsMenu()
    }

    override fun deleteFolders(folders: ArrayList<File>) {
        val fileDirItems = folders.asSequence().filter { it.isDirectory }
            .map { FileDirItem(it.absolutePath, it.name, true) }
            .toMutableList() as ArrayList<FileDirItem>
        when {
            fileDirItems.isEmpty() -> return
            fileDirItems.size == 1 -> toast(
                String.format(
                    getString(R.string.deleting_folder),
                    fileDirItems.first().name
                )
            )

            else -> {
                val baseString =
                    if (config.useRecycleBin) R.plurals.moving_items_into_bin else R.plurals.delete_items
                val deletingItems =
                    resources.getQuantityString(baseString, fileDirItems.size, fileDirItems.size)
                toast(deletingItems)
            }
        }

        val itemsToDelete = ArrayList<FileDirItem>()
        val filter = config.filterMedia
        val showHidden = config.shouldShowHidden
        fileDirItems.filter { it.isDirectory }.forEach {
            val files = File(it.path).listFiles()
            files?.filter {
                it.absolutePath.isMediaFile() && (showHidden || !it.name.startsWith('.')) &&
                        ((it.isImageFast() && filter and TYPE_IMAGES != 0) ||
                                (it.isVideoFast() && filter and TYPE_VIDEOS != 0) ||
                                (it.isGif() && filter and TYPE_GIFS != 0) ||
                                (it.isRawFast() && filter and TYPE_RAWS != 0) ||
                                (it.isSvg() && filter and TYPE_SVGS != 0))
            }?.mapTo(itemsToDelete) { it.toFileDirItem(this) }
        }

        if (config.useRecycleBin) {
            val pathsToDelete = ArrayList<String>()
            itemsToDelete.mapTo(pathsToDelete) { it.path }

            movePathsInRecycleBin(pathsToDelete, mMediumDao) {
                if (it) {
                    deleteFilteredFileDirItems(itemsToDelete, folders)
                } else {
                    toast(R.string.unknown_error_occurred)
                }
            }
        } else {
            deleteFilteredFileDirItems(itemsToDelete, folders)
        }
    }

    private fun deleteFilteredFileDirItems(
        fileDirItems: ArrayList<FileDirItem>,
        folders: ArrayList<File>,
    ) {
        deleteFiles(fileDirItems) {
            runOnUiThread {
                refreshItems()
            }

            Thread {
                folders.filter { !it.exists() }.forEach {
                    mDirectoryDao.deleteDirPath(it.absolutePath)
                }
            }.start()
        }
    }

    private fun setupLayoutManager() {
        if (config.viewTypeFolders == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = directoriesGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            directoriesRefreshLayout.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            directoriesRefreshLayout.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        layoutManager.spanCount = config.dirColumnCnt
    }

    private fun measureRecyclerViewContent(directories: ArrayList<Directory>) {
        directoriesGrid.onGlobalLayout {
            if (config.scrollHorizontally) {
                calculateContentWidth(directories)
            } else {
                calculateContentHeight(directories)
            }
        }
    }

    private fun calculateContentWidth(directories: ArrayList<Directory>) {
        val layoutManager = directoriesGrid.layoutManager as MyGridLayoutManager
        val thumbnailWidth = layoutManager.getChildAt(0)?.width ?: 0
        val fullWidth = ((directories.size - 1) / layoutManager.spanCount + 1) * thumbnailWidth
        directoriesHorizontalFastScroller.setContentWidth(fullWidth)
        directoriesHorizontalFastScroller.setScrollToX(directoriesGrid.computeHorizontalScrollOffset())
    }

    private fun calculateContentHeight(directories: ArrayList<Directory>) {
        val layoutManager = directoriesGrid.layoutManager as MyGridLayoutManager
        val thumbnailHeight = layoutManager.getChildAt(0)?.height ?: 0
        val fullHeight = ((directories.size - 1) / layoutManager.spanCount + 1) * thumbnailHeight
        directoriesVerticalFastScroller.setContentHeight(fullHeight)
        directoriesVerticalFastScroller.setScrollToY(directoriesGrid.computeVerticalScrollOffset())
    }

    private fun initZoomListener() {
        if (config.viewTypeFolders == VIEW_TYPE_GRID) {
            val layoutManager = directoriesGrid.layoutManager as MyGridLayoutManager
            mZoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            mZoomListener = null
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = directoriesGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        directoriesRefreshLayout.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mZoomListener = null
    }

    private fun toggleRecycleBin(show: Boolean) {
        config.showRecycleBinAtFolders = show
        invalidateOptionsMenu()
        Thread {
            var dirs = getCurrentlyDisplayedDirs()
            if (!show) {
                dirs = dirs.filter { it.path != RECYCLE_BIN } as ArrayList<Directory>
            }
            gotDirectories(dirs)
        }.start()
    }

    private fun createNewFolder() {
        FilePickerDialog(this, internalStoragePath, false, config.shouldShowHidden, false, true) {
            CreateNewFolderDialog(this, it) {
                config.tempFolderPath = it
                Thread {
                    gotDirectories(addTempFolderIfNeeded(getCurrentlyDisplayedDirs()))
                }.start()
            }
        }
    }

    private fun increaseColumnCount() {
        config.dirColumnCnt = ++(directoriesGrid.layoutManager as MyGridLayoutManager).spanCount
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.dirColumnCnt = --(directoriesGrid.layoutManager as MyGridLayoutManager).spanCount
        columnCountChanged()
    }

    private fun columnCountChanged() {
        invalidateOptionsMenu()
        directoriesGrid.adapter?.notifyDataSetChanged()
        getRecyclerAdapter()?.dirs?.apply {
            measureRecyclerViewContent(this)
        }
    }

    private fun isPickImageIntent(intent: Intent) =
        isPickIntent(intent) && (hasImageContentData(intent) || isImageType(intent))

    private fun isPickVideoIntent(intent: Intent) =
        isPickIntent(intent) && (hasVideoContentData(intent) || isVideoType(intent))

    private fun isPickIntent(intent: Intent) = intent.action == Intent.ACTION_PICK

    private fun isGetContentIntent(intent: Intent) =
        intent.action == Intent.ACTION_GET_CONTENT && intent.type != null

    private fun isGetImageContentIntent(intent: Intent) = isGetContentIntent(intent) &&
            (intent.type?.startsWith("image/") == true || intent.type == MediaStore.Images.Media.CONTENT_TYPE)

    private fun isGetVideoContentIntent(intent: Intent) = isGetContentIntent(intent) &&
            (intent.type?.startsWith("video/") == true || intent.type == MediaStore.Video.Media.CONTENT_TYPE)

    private fun isGetAnyContentIntent(intent: Intent) =
        isGetContentIntent(intent) && intent.type == "*/*"

    private fun isSetWallpaperIntent(intent: Intent?) =
        intent?.action == Intent.ACTION_SET_WALLPAPER

    private fun hasImageContentData(intent: Intent) =
        (intent.data == MediaStore.Images.Media.EXTERNAL_CONTENT_URI ||
                intent.data == MediaStore.Images.Media.INTERNAL_CONTENT_URI)

    private fun hasVideoContentData(intent: Intent) =
        (intent.data == MediaStore.Video.Media.EXTERNAL_CONTENT_URI ||
                intent.data == MediaStore.Video.Media.INTERNAL_CONTENT_URI)

    private fun isImageType(intent: Intent) =
        (intent.type?.startsWith("image/") == true || intent.type == MediaStore.Images.Media.CONTENT_TYPE)

    private fun isVideoType(intent: Intent) =
        (intent.type?.startsWith("video/") == true || intent.type == MediaStore.Video.Media.CONTENT_TYPE)

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_MEDIA && resultData != null) {
                val resultIntent = Intent()
                var resultUri: Uri? = null
                if (mIsThirdPartyIntent) {
                    when {
                        intent.extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true && intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 -> {
                            resultUri = fillExtraOutput(resultData)
                        }

                        resultData.extras?.containsKey(PICKED_PATHS) == true -> fillPickedPaths(
                            resultData,
                            resultIntent
                        )

                        else -> fillIntentPath(resultData, resultIntent)
                    }
                }

                if (resultUri != null) {
                    resultIntent.data = resultUri
                    resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else if (requestCode == PICK_WALLPAPER) {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun fillExtraOutput(resultData: Intent): Uri? {
        val file = File(resultData.data?.path ?: "")
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            val output = intent.extras?.get(MediaStore.EXTRA_OUTPUT) as Uri
            inputStream = FileInputStream(file)
            outputStream = contentResolver.openOutputStream(output)
            if (outputStream != null) {
                inputStream.copyTo(outputStream)
            }
        } catch (e: SecurityException) {
            showErrorToast(e)
        } catch (ignored: FileNotFoundException) {
            return getFilePublicUri(file, BuildConfig.APPLICATION_ID)
        } finally {
            inputStream?.close()
            outputStream?.close()
        }

        return null
    }

    private fun fillPickedPaths(resultData: Intent, resultIntent: Intent) {
        val paths = resultData.extras?.getStringArrayList(PICKED_PATHS)
        val uris =
            paths?.map { getFilePublicUri(File(it), BuildConfig.APPLICATION_ID) } as ArrayList
        val clipData =
            ClipData("Attachment", arrayOf("image/*", "video/*"), ClipData.Item(uris.removeAt(0)))

        uris.forEach {
            clipData.addItem(ClipData.Item(it))
        }

        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resultIntent.clipData = clipData
    }

    private fun fillIntentPath(resultData: Intent, resultIntent: Intent) {
        val data = resultData.data
        val path = if (data.toString().startsWith("/")) data.toString() else data?.path
        val uri = getFilePublicUri(File(path), BuildConfig.APPLICATION_ID)
        val type = path?.getMimeType()
        resultIntent.setDataAndTypeAndNormalize(uri, type)
        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun itemClicked(path: String) {
        Intent(this, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, path)
            handleMediaIntent(this)
        }
    }

    private fun handleMediaIntent(intent: Intent) {
        intent.apply {
            if (mIsSetWallpaperIntent) {
                putExtra(SET_WALLPAPER_INTENT, true)
                startActivityForResult(this, PICK_WALLPAPER)
            } else {
                putExtra(GET_IMAGE_INTENT, mIsPickImageIntent || mIsGetImageContentIntent)
                putExtra(GET_VIDEO_INTENT, mIsPickVideoIntent || mIsGetVideoContentIntent)
                putExtra(GET_ANY_INTENT, mIsGetAnyContentIntent)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mAllowPickingMultiple)
                startActivityForResult(this, PICK_MEDIA)
            }
        }
    }

    private fun gotDirectories(newDirs: ArrayList<Directory>) {
        mIsGettingDirs = false
        mShouldStopFetching = false

        // if hidden item showing is disabled but all Favorite items are hidden, hide the Favorites folder
        if (!config.shouldShowHidden) {
            val favoritesFolder = newDirs.firstOrNull { it.areFavorites() }
            if (favoritesFolder != null && favoritesFolder.tmb.getFilenameFromPath()
                    .startsWith('.')
            ) {
                newDirs.remove(favoritesFolder)
            }
        }

        val dirs = getSortedDirectories(newDirs)
        var isPlaceholderVisible = dirs.isEmpty()

        runOnUiThread {
            checkPlaceholderVisibility(dirs)

            val allowHorizontalScroll =
                config.scrollHorizontally && config.viewTypeFolders == VIEW_TYPE_GRID
            directoriesVerticalFastScroller.beVisibleIf(directoriesGrid.isVisible() && !allowHorizontalScroll)
            directoriesHorizontalFastScroller.beVisibleIf(directoriesGrid.isVisible() && allowHorizontalScroll)
            setupAdapter(dirs.clone() as ArrayList<Directory>)
        }

        // cached folders have been loaded, recheck folders one by one starting with the first displayed
        val mediaFetcher = MediaFetcher(applicationContext)
        val getImagesOnly = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideosOnly = mIsPickVideoIntent || mIsGetVideoContentIntent
        val hiddenString = getString(R.string.hidden)
        val albumCovers = config.parseAlbumCovers()
        val includedFolders = config.includedFolders
        val tempFolderPath = config.tempFolderPath
        val isSortingAscending = config.directorySorting and SORT_DESCENDING == 0
        val getProperDateTaken = config.directorySorting and SORT_BY_DATE_TAKEN != 0
        val getProperFileSize = config.directorySorting and SORT_BY_SIZE != 0
        val favoritePaths = getFavoritePaths()
        val dirPathsToRemove = ArrayList<String>()

        try {
            for (directory in dirs) {
                if (mShouldStopFetching) {
                    return
                }

                val curMedia = mediaFetcher.getFilesFrom(
                    directory.path,
                    getImagesOnly,
                    getVideosOnly,
                    getProperDateTaken,
                    getProperFileSize,
                    favoritePaths,
                    false
                )
                val newDir = if (curMedia.isEmpty()) {
                    if (directory.path != tempFolderPath) {
                        dirPathsToRemove.add(directory.path)
                    }
                    directory
                } else {
                    createDirectoryFromMedia(
                        directory.path,
                        curMedia,
                        albumCovers,
                        hiddenString,
                        includedFolders,
                        isSortingAscending,
                        getProperFileSize
                    )
                }

                // we are looping through the already displayed folders looking for changes, do not do anything if nothing changed
                if (directory.copy(subfoldersCount = 0, subfoldersMediaCount = 0) == newDir) {
                    continue
                }

                directory.apply {
                    tmb = newDir.tmb
                    name = newDir.name
                    mediaCnt = newDir.mediaCnt
                    modified = newDir.modified
                    taken = newDir.taken
                    this@apply.size = newDir.size
                    types = newDir.types
                }

                setupAdapter(dirs)

                // update directories and media files in the local db, delete invalid items
                updateDBDirectory(directory, mDirectoryDao)
                if (!directory.isRecycleBin()) {
                    mMediumDao.insertAll(curMedia)
                }
                getCachedMedia(directory.path, getVideosOnly, getImagesOnly, mMediumDao) {
                    it.forEach {
                        if (!curMedia.contains(it)) {
                            val path = (it as? Medium)?.path
                            if (path != null) {
                                deleteDBPath(mMediumDao, path)
                            }
                        }
                    }
                }
            }

            if (dirPathsToRemove.isNotEmpty()) {
                val dirsToRemove = dirs.filter { dirPathsToRemove.contains(it.path) }
                dirsToRemove.forEach {
                    mDirectoryDao.deleteDirPath(it.path)
                }
                dirs.removeAll(dirsToRemove)
                setupAdapter(dirs)
            }
        } catch (ignored: Exception) {
        }

        val foldersToScan = mediaFetcher.getFoldersToScan()
        foldersToScan.add(FAVORITES)
        if (config.useRecycleBin && config.showRecycleBinAtFolders) {
            foldersToScan.add(RECYCLE_BIN)
        } else {
            foldersToScan.remove(RECYCLE_BIN)
        }

        dirs.forEach {
            foldersToScan.remove(it.path)
        }

        // check the remaining folders which were not cached at all yet
        for (folder in foldersToScan) {
            if (mShouldStopFetching) {
                return
            }

            val newMedia = mediaFetcher.getFilesFrom(
                folder,
                getImagesOnly,
                getVideosOnly,
                getProperDateTaken,
                getProperFileSize,
                favoritePaths,
                false
            )
            if (newMedia.isEmpty()) {
                continue
            }

            if (isPlaceholderVisible) {
                isPlaceholderVisible = false
                runOnUiThread {
                    directoriesEmptyTextLabel.beGone()
                    directoriesEmptyText.beGone()
                    directoriesGrid.beVisible()
                }
            }

            val newDir = createDirectoryFromMedia(
                folder,
                newMedia,
                albumCovers,
                hiddenString,
                includedFolders,
                isSortingAscending,
                getProperFileSize
            )
            dirs.add(newDir)
            setupAdapter(dirs)
            try {
                mDirectoryDao.insert(newDir)
                if (folder != RECYCLE_BIN) {
                    mMediumDao.insertAll(newMedia)
                }
            } catch (ignored: Exception) {
            }
        }

        mLoadedInitialPhotos = true
        checkLastMediaChanged()

        runOnUiThread {
            directoriesRefreshLayout.isRefreshing = false
            checkPlaceholderVisibility(dirs)
        }
        checkInvalidDirectories(dirs)

        val everShownFolders = config.everShownFolders as HashSet
        dirs.mapTo(everShownFolders) { it.path }

        try {
            config.everShownFolders = everShownFolders
        } catch (e: Exception) {
            config.everShownFolders = HashSet()
        }
        mDirs = dirs.clone() as ArrayList<Directory>

        if (mDirs.size > 55) {
            excludeSpamFolders()
        }
    }

    private fun checkPlaceholderVisibility(dirs: ArrayList<Directory>) {
        directoriesEmptyTextLabel.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)
        directoriesEmptyText.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)
        directoriesGrid.beVisibleIf(directoriesEmptyTextLabel.isGone())
    }

    private fun createDirectoryFromMedia(
        path: String,
        curMedia: ArrayList<Medium>,
        albumCovers: ArrayList<AlbumCover>,
        hiddenString: String,
        includedFolders: MutableSet<String>,
        isSortingAscending: Boolean,
        getProperFileSize: Boolean,
    ): Directory {
        var thumbnail = curMedia.firstOrNull { File(it.path).exists() }?.path ?: ""
        albumCovers.forEach {
            if (it.path == path && File(it.tmb).exists()) {
                thumbnail = it.tmb
            }
        }

        val firstItem = curMedia.first()
        val lastItem = curMedia.last()
        val dirName = checkAppendingHidden(path, hiddenString, includedFolders)
        val lastModified =
            if (isSortingAscending) Math.min(firstItem.modified, lastItem.modified) else Math.max(
                firstItem.modified,
                lastItem.modified
            )
        val dateTaken =
            if (isSortingAscending) Math.min(firstItem.taken, lastItem.taken) else Math.max(
                firstItem.taken,
                lastItem.taken
            )
        val size = if (getProperFileSize) curMedia.sumByLong { it.size } else 0L
        val mediaTypes = curMedia.getDirMediaTypes()
        return Directory(
            null,
            path,
            thumbnail,
            dirName,
            curMedia.size,
            lastModified,
            dateTaken,
            size,
            getPathLocation(path),
            mediaTypes
        )
    }

    private fun setupAdapter(dirs: ArrayList<Directory>, textToSearch: String = "") {
        if (dirs.size > 0) {
            runOnUiThread {
                loading.hide()
            }
        }

        val currAdapter = directoriesGrid.adapter
        val distinctDirs =
            dirs.distinctBy { it.path.getDistinctPath() }.toMutableList() as ArrayList<Directory>
        val sortedDirs = getSortedDirectories(distinctDirs)
        var dirsToShow =
            getDirsToShow(sortedDirs, mDirs, mCurrentPathPrefix).clone() as ArrayList<Directory>

        if (currAdapter == null) {
            initZoomListener()
            val fastscroller =
                if (config.scrollHorizontally) directoriesHorizontalFastScroller else directoriesVerticalFastScroller
            com.roy.gallery.pro.adapters.DirectoryAdapter(
                this,
                dirsToShow,
                this,
                directoriesGrid,
                isPickIntent(intent) || isGetAnyContentIntent(intent),
                fastscroller
            ) {
                val clickedDir = it as Directory
                val path = clickedDir.path
                if (clickedDir.subfoldersCount == 1 || !config.groupDirectSubfolders) {
                    if (path != config.tempFolderPath) {
                        itemClicked(path)
                    }
                } else {
                    mCurrentPathPrefix = path
                    mOpenedSubfolders.add(path)
                    setupAdapter(mDirs, "")
                }
            }.apply {
                setupZoomListener(mZoomListener)
                runOnUiThread {
                    directoriesGrid.adapter = this
                    setupScrollDirection()
                }
            }
            measureRecyclerViewContent(dirsToShow)
        } else {
            if (textToSearch.isNotEmpty()) {
                dirsToShow = dirsToShow.filter { it.name.contains(textToSearch, true) }
                    .sortedBy { !it.name.startsWith(textToSearch, true) }
                    .toMutableList() as ArrayList
            }
            runOnUiThread {
                (directoriesGrid.adapter as? com.roy.gallery.pro.adapters.DirectoryAdapter)?.updateDirs(
                    dirsToShow
                )
                measureRecyclerViewContent(dirsToShow)
            }
        }

        // recyclerview sometimes becomes empty at init/update, triggering an invisible refresh like this seems to work fine
        directoriesGrid.postDelayed({
            directoriesGrid.scrollBy(0, 0)
        }, 500)
    }

    private fun setupScrollDirection() {
        val allowHorizontalScroll =
            config.scrollHorizontally && config.viewTypeFolders == VIEW_TYPE_GRID
        directoriesVerticalFastScroller.isHorizontal = false
        directoriesVerticalFastScroller.beGoneIf(allowHorizontalScroll)

        directoriesHorizontalFastScroller.isHorizontal = true
        directoriesHorizontalFastScroller.beVisibleIf(allowHorizontalScroll)

        if (allowHorizontalScroll) {
            directoriesHorizontalFastScroller.allowBubbleDisplay = config.showInfoBubble
            directoriesHorizontalFastScroller.setViews(
                directoriesGrid,
                directoriesRefreshLayout
            ) {
                directoriesHorizontalFastScroller.updateBubbleText(getBubbleTextItem(it))
            }
        } else {
            directoriesVerticalFastScroller.allowBubbleDisplay = config.showInfoBubble
            directoriesVerticalFastScroller.setViews(
                directoriesGrid,
                directoriesRefreshLayout
            ) {
                directoriesVerticalFastScroller.updateBubbleText(getBubbleTextItem(it))
            }
        }
    }

    private fun checkInvalidDirectories(dirs: ArrayList<Directory>) {
        val invalidDirs = ArrayList<Directory>()
        dirs.filter { !it.areFavorites() && !it.isRecycleBin() }.forEach {
            if (!File(it.path).exists()) {
                invalidDirs.add(it)
            } else if (it.path != config.tempFolderPath) {
                val children = File(it.path).list()?.asList()
                val hasMediaFile = children?.any { it?.isMediaFile() == true } ?: false
                if (!hasMediaFile) {
                    invalidDirs.add(it)
                }
            }
        }

        if (getFavoritePaths().isEmpty()) {
            val favoritesFolder = dirs.firstOrNull { it.areFavorites() }
            if (favoritesFolder != null) {
                invalidDirs.add(favoritesFolder)
            }
        }

        if (config.useRecycleBin) {
            val binFolder = dirs.firstOrNull { it.path == RECYCLE_BIN }
            if (binFolder != null && mMediumDao.getDeletedMedia().isEmpty()) {
                invalidDirs.add(binFolder)
            }
        }

        if (invalidDirs.isNotEmpty()) {
            dirs.removeAll(invalidDirs)
            setupAdapter(dirs)
            invalidDirs.forEach {
                mDirectoryDao.deleteDirPath(it.path)
            }
        }
    }

    private fun getCurrentlyDisplayedDirs() = getRecyclerAdapter()?.dirs ?: ArrayList()

    private fun getBubbleTextItem(index: Int) =
        getRecyclerAdapter()?.dirs?.getOrNull(index)?.getBubbleText(config.directorySorting, this)
            ?: ""

    private fun setupLatestMediaId() {
        Thread {
            if (hasPermission(PERMISSION_READ_STORAGE)) {
                mLatestMediaId = getLatestMediaId()
                mLatestMediaDateId = getLatestMediaByDateId()
            }
        }.start()
    }

    private fun checkLastMediaChanged() {
        if (isDestroyed) {
            return
        }

        mLastMediaHandler.postDelayed({
            Thread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getDirectories()
                    }
                } else {
                    mLastMediaHandler.removeCallbacksAndMessages(null)
                    checkLastMediaChanged()
                }
            }.start()
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun checkRecycleBinItems() {
        if (config.useRecycleBin && config.lastBinCheck < System.currentTimeMillis() - DAY_SECONDS * 1000) {
            config.lastBinCheck = System.currentTimeMillis()
            Handler().postDelayed({
                Thread {
                    try {
                        mMediumDao.deleteOldRecycleBinItems(System.currentTimeMillis() - MONTH_MILLISECONDS)
                    } catch (e: Exception) {
                    }
                }.start()
            }, 3000L)
        }
    }

    // exclude probably unwanted folders, for example facebook stickers are split between hundreds of separate folders like
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/175139712676531/209575122566323
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/497837993632037/499671223448714
    private fun excludeSpamFolders() {
        Thread {
            try {
                val internalPath = internalStoragePath
                val checkedPaths = ArrayList<String>()
                val oftenRepeatedPaths = ArrayList<String>()
                val paths = mDirs.map { it.path.removePrefix(internalPath) }
                    .toMutableList() as ArrayList<String>
                paths.forEach {
                    val parts = it.split("/")
                    var currentString = ""
                    for (i in 0 until parts.size) {
                        currentString += "${parts[i]}/"

                        if (!checkedPaths.contains(currentString)) {
                            val cnt = paths.count { it.startsWith(currentString) }
                            if (cnt > 50 && currentString.startsWith("/Android/data", true)) {
                                oftenRepeatedPaths.add(currentString)
                            }
                        }

                        checkedPaths.add(currentString)
                    }
                }

                val substringToRemove = oftenRepeatedPaths.filter {
                    val path = it
                    it == "/" || oftenRepeatedPaths.any { it != path && it.startsWith(path) }
                }

                oftenRepeatedPaths.removeAll(substringToRemove)
                oftenRepeatedPaths.forEach {
                    val file = File("$internalPath/$it")
                    if (file.exists()) {
                        config.addExcludedFolder(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
            }
        }.start()
    }

    override fun refreshItems() {
        getDirectories()
    }

    override fun recheckPinnedFolders() {
        Thread {
            gotDirectories(movePinnedDirectoriesToFront(getCurrentlyDisplayedDirs()))
        }.start()
    }

    override fun updateDirectories(directories: ArrayList<Directory>) {
        Thread {
            storeDirectoryItems(directories, mDirectoryDao)
            removeInvalidDBDirectories()
        }.start()
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(213, R.string.release_213))
            add(Release(217, R.string.release_217))
            add(Release(220, R.string.release_220))
            add(Release(221, R.string.release_221))
            add(Release(225, R.string.release_225))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }


    private fun registerReceiver() {
        try {
            registerReceiver(
                mHomeKeyEventReceiver,
                IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun unregisterReceiver() {
        try {
            unregisterReceiver(mHomeKeyEventReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mHomeKeyEventReceiver = object : BroadcastReceiver() {
        var SYSTEM_REASON = "reason"
        var SYSTEM_HOME_KEY = "homekey"

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null && action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                val reason = intent.getStringExtra(SYSTEM_REASON)
                if (TextUtils.equals(reason, SYSTEM_HOME_KEY)) {
                    isClickHome = true
                    leaveTime = System.currentTimeMillis()
                }
            }
        }
    }
}
