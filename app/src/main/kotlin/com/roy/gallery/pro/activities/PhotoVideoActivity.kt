package com.roy.gallery.pro.activities

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.roy.gallery.pro.BuildConfig
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.extensions.hideSystemUI
import com.roy.gallery.pro.extensions.navigationBarHeight
import com.roy.gallery.pro.extensions.openEditor
import com.roy.gallery.pro.extensions.openPath
import com.roy.gallery.pro.extensions.parseFileChannel
import com.roy.gallery.pro.extensions.setAs
import com.roy.gallery.pro.extensions.sharePath
import com.roy.gallery.pro.extensions.showSystemUI
import com.roy.gallery.pro.fragments.PhotoFragment
import com.roy.gallery.pro.fragments.VideoFragment
import com.roy.gallery.pro.fragments.ViewPagerFragment
import com.roy.gallery.pro.helpers.BOTTOM_ACTION_EDIT
import com.roy.gallery.pro.helpers.BOTTOM_ACTION_PROPERTIES
import com.roy.gallery.pro.helpers.BOTTOM_ACTION_SET_AS
import com.roy.gallery.pro.helpers.BOTTOM_ACTION_SHARE
import com.roy.gallery.pro.helpers.IS_VIEW_INTENT
import com.roy.gallery.pro.helpers.MEDIUM
import com.roy.gallery.pro.helpers.PATH
import com.roy.gallery.pro.helpers.TYPE_GIFS
import com.roy.gallery.pro.helpers.TYPE_IMAGES
import com.roy.gallery.pro.helpers.TYPE_RAWS
import com.roy.gallery.pro.helpers.TYPE_SVGS
import com.roy.gallery.pro.helpers.TYPE_VIDEOS
import com.roy.gallery.pro.models.Medium
import com.roy.commons.dlg.PropertiesDialog
import com.roy.commons.ext.beGone
import com.roy.commons.ext.beVisible
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.getFilenameFromPath
import com.roy.commons.ext.getFilenameFromUri
import com.roy.commons.ext.getFinalUriFromPath
import com.roy.commons.ext.getParentPath
import com.roy.commons.ext.getRealPathFromURI
import com.roy.commons.ext.getUriMimeType
import com.roy.commons.ext.isGif
import com.roy.commons.ext.isGone
import com.roy.commons.ext.isRawFast
import com.roy.commons.ext.isSvg
import com.roy.commons.ext.isVideoFast
import com.roy.commons.ext.scanPathRecursively
import com.roy.commons.ext.showSideloadingDialog
import com.roy.commons.ext.toast
import com.roy.commons.helpers.IS_FROM_GALLERY
import com.roy.commons.helpers.PERMISSION_WRITE_STORAGE
import com.roy.commons.helpers.REAL_FILE_PATH
import com.roy.commons.helpers.SIDELOADING_TRUE
import kotlinx.android.synthetic.main.v_bottom_actions.*
import kotlinx.android.synthetic.main.f_holder.bottom_actions
import kotlinx.android.synthetic.main.f_holder.fragmentHolder
import kotlinx.android.synthetic.main.f_holder.topShadow
import kotlinx.android.synthetic.main.v_bottom_actions.bottomEdit
import kotlinx.android.synthetic.main.v_bottom_actions.bottomSetAs
import kotlinx.android.synthetic.main.v_bottom_actions.bottomShare
import java.io.File
import java.io.FileInputStream

open class PhotoVideoActivity : SimpleActivity(),
    ViewPagerFragment.FragmentListener {
    private var mMedium: Medium? = null
    private var mIsFullScreen = false
    private var mIsFromGallery = false
    private var mFragment: ViewPagerFragment? = null
    private var mUri: Uri? = null

    var mIsVideo = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.f_holder)

        if (config.appSideloadingStatus == SIDELOADING_TRUE) {
            showSideloadingDialog()
            return
        }

        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                checkIntent(savedInstanceState)
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.statusBarColor = Color.TRANSPARENT

        if (config.bottomActions) {
            window.navigationBarColor = Color.TRANSPARENT
        } else {
            setTranslucentNavigation()
        }

        if (config.blackBackground) {
            updateStatusbarColor(Color.BLACK)
        }
    }

    private fun checkIntent(savedInstanceState: Bundle? = null) {
        mUri = intent.data ?: return
        val uri = mUri.toString()
        if (uri.startsWith("content:/") && uri.contains("/storage/")) {
            val guessedPath = uri.substring(uri.indexOf("/storage/"))
            if (File(guessedPath).exists()) {
                val extras = intent.extras ?: Bundle()
                extras.apply {
                    putString(REAL_FILE_PATH, guessedPath)
                    intent.putExtras(this)
                }
            }
        }

        var filename = getFilenameFromUri(mUri!!)
        mIsFromGallery = intent.getBooleanExtra(IS_FROM_GALLERY, false)
        if (mIsFromGallery && filename.isVideoFast() && config.openVideosOnSeparateScreen) {
            launchVideoPlayer()
            return
        }

        if (intent.extras?.containsKey(REAL_FILE_PATH) == true) {
            val realPath = intent.extras!!.getString(REAL_FILE_PATH)
            if (realPath != null && File(realPath).exists()) {
                if (realPath.getFilenameFromPath().contains('.') || filename.contains('.')) {
                    sendViewPagerIntent(realPath)
                    finish()
                    return
                } else {
                    filename = realPath.getFilenameFromPath()
                }
            }
        }

        if (mUri!!.scheme == "file") {
            if (filename.contains('.')) {
                scanPathRecursively(mUri!!.path ?: "")
                sendViewPagerIntent(mUri!!.path ?: "")
                finish()
                return
            }
        } else {
            val path = applicationContext.getRealPathFromURI(mUri!!) ?: ""
            if (path != mUri.toString() && path.isNotEmpty() && mUri!!.authority != "mms" && filename.contains(
                    '.'
                )
            ) {
                scanPathRecursively(mUri!!.path ?: "")
                sendViewPagerIntent(path)
                finish()
                return
            }
        }

        checkNotchSupport()
        showSystemUI(true)
        val bundle = Bundle()
        val file = File(mUri.toString())
        val type = when {
            filename.isVideoFast() -> TYPE_VIDEOS
            filename.isGif() -> TYPE_GIFS
            filename.isRawFast() -> TYPE_RAWS
            filename.isSvg() -> TYPE_SVGS
            else -> TYPE_IMAGES
        }

        mIsVideo = type == TYPE_VIDEOS
        mMedium = Medium(
            null,
            filename,
            mUri.toString(),
            mUri!!.path?.getParentPath() ?: "",
            0,
            0,
            file.length(),
            type,
            0,
            false,
            0L
        )
        supportActionBar?.title = mMedium!!.name
        bundle.putSerializable(MEDIUM, mMedium)

        if (savedInstanceState == null) {
            mFragment = if (mIsVideo) VideoFragment() else PhotoFragment()
            mFragment!!.listener = this
            mFragment!!.arguments = bundle
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentPlaceholder, mFragment!!).commit()
        }

        if (config.blackBackground) {
            fragmentHolder.background = ColorDrawable(Color.BLACK)
        }

        if (config.maxBrightness) {
            val attributes = window.attributes
            attributes.screenBrightness = 1f
            window.attributes = attributes
        }

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            val isFullscreen = visibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
            mFragment?.fullscreenToggled(isFullscreen)
        }

        initBottomActions()
    }

    private fun launchVideoPlayer() {
        val newUri = getFinalUriFromPath(mUri.toString(), BuildConfig.APPLICATION_ID)
        if (newUri == null) {
            toast(R.string.unknown_error_occurred)
            return
        }

        var isPanorama = false
        val realPath = intent?.extras?.getString(REAL_FILE_PATH) ?: ""
        try {
            if (realPath.isNotEmpty()) {
                val fis = FileInputStream(File(realPath))
                parseFileChannel(realPath, fis.channel, 0, 0, 0) {
                    isPanorama = true
                }
            }
        } catch (ignored: Exception) {
        } catch (ignored: OutOfMemoryError) {
        }

        if (isPanorama) {
            Intent(
                applicationContext,
                PanoramaVideoActivity::class.java
            ).apply {
                putExtra(PATH, realPath)
                startActivity(this)
            }
        } else {
            val mimeType = getUriMimeType(mUri.toString(), newUri)
            Intent(
                applicationContext,
                VideoPlayerActivity::class.java
            ).apply {
                setDataAndType(newUri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                if (intent.extras != null) {
                    putExtras(intent.extras!!)
                }

                startActivity(this)
            }
        }
        finish()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        initBottomActionsLayout()
    }

    private fun sendViewPagerIntent(path: String) {
        Intent(this, ViewPagerActivity::class.java).apply {
            putExtra(IS_VIEW_INTENT, true)
            putExtra(IS_FROM_GALLERY, mIsFromGallery)
            putExtra(PATH, path)
            startActivity(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_photo_video, menu)
        val visibleBottomActions = if (config.bottomActions) config.visibleBottomActions else 0

        menu.apply {
            findItem(R.id.menuSetAs).isVisible =
                mMedium?.isImage() == true && visibleBottomActions and BOTTOM_ACTION_SET_AS == 0
            findItem(R.id.menuEdit).isVisible =
                mMedium?.isImage() == true && mUri?.scheme == "file" && visibleBottomActions and BOTTOM_ACTION_EDIT == 0
            findItem(R.id.menuProperties).isVisible =
                mUri?.scheme == "file" && visibleBottomActions and BOTTOM_ACTION_PROPERTIES == 0
            findItem(R.id.menuShare).isVisible = visibleBottomActions and BOTTOM_ACTION_SHARE == 0
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (mMedium == null || mUri == null) {
            return true
        }

        when (item.itemId) {
            R.id.menuSetAs -> setAs(mUri!!.toString())
            R.id.menuOpenWith -> openPath(mUri!!.toString(), true)
            R.id.menuShare -> sharePath(mUri!!.toString())
            R.id.menuEdit -> openEditor(mUri!!.toString())
            R.id.menuProperties -> showProperties()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showProperties() {
        PropertiesDialog(this, mUri!!.path ?: "")
    }

    private fun initBottomActions() {
        initBottomActionsLayout()
        initBottomActionButtons()
    }

    private fun initBottomActionsLayout() {
        bottom_actions.layoutParams.height =
            resources.getDimension(R.dimen.bottom_actions_height).toInt() + navigationBarHeight
        if (config.bottomActions) {
            bottom_actions.beVisible()
        } else {
            bottom_actions.beGone()
        }
    }

    private fun initBottomActionButtons() {
        arrayListOf(
            bottomFavorite,
            bottomDelete,
            bottomRotate,
            bottomProperties,
            bottomChangeOrientation,
            bottomSlideshow,
            bottomShowOnMap,
            bottomToggleFileVisibility,
            bottomRename,
            bottomCopy,
            bottomMove
        ).forEach {
            it.beGone()
        }

        val visibleBottomActions = if (config.bottomActions) config.visibleBottomActions else 0
        bottomEdit.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_EDIT != 0 && mMedium?.isImage() == true)
        bottomEdit.setOnClickListener {
            if (mUri != null && bottom_actions.alpha == 1f) {
                openEditor(mUri!!.toString())
            }
        }

        bottomShare.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_SHARE != 0)
        bottomShare.setOnClickListener {
            if (mUri != null && bottom_actions.alpha == 1f) {
                sharePath(mUri!!.toString())
            }
        }

        bottomSetAs.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_SET_AS != 0 && mMedium?.isImage() == true)
        bottomSetAs.setOnClickListener {
            setAs(mUri!!.toString())
        }
    }

    override fun fragmentClicked() {
        mIsFullScreen = !mIsFullScreen
        if (mIsFullScreen) {
            hideSystemUI(true)
        } else {
            showSystemUI(true)
        }

        val newAlpha = if (mIsFullScreen) 0f else 1f
        topShadow.animate().alpha(newAlpha).start()
        if (!bottom_actions.isGone()) {
            bottom_actions.animate().alpha(newAlpha).start()
        }
    }

    override fun videoEnded() = false

    override fun goToPrevItem() {}

    override fun goToNextItem() {}

    override fun launchViewVideoIntent(path: String) {}
}
