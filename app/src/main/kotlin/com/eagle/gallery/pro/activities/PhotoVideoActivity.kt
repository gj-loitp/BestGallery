package com.eagle.gallery.pro.activities

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.eagle.commons.dlg.PropertiesDialog
import com.eagle.commons.ext.beGone
import com.eagle.commons.ext.beVisible
import com.eagle.commons.ext.beVisibleIf
import com.eagle.commons.ext.getFilenameFromPath
import com.eagle.commons.ext.getFilenameFromUri
import com.eagle.commons.ext.getFinalUriFromPath
import com.eagle.commons.ext.getParentPath
import com.eagle.commons.ext.getRealPathFromURI
import com.eagle.commons.ext.getUriMimeType
import com.eagle.commons.ext.isGif
import com.eagle.commons.ext.isGone
import com.eagle.commons.ext.isRawFast
import com.eagle.commons.ext.isSvg
import com.eagle.commons.ext.isVideoFast
import com.eagle.commons.ext.scanPathRecursively
import com.eagle.commons.ext.showSideloadingDialog
import com.eagle.commons.ext.toast
import com.eagle.commons.helpers.IS_FROM_GALLERY
import com.eagle.commons.helpers.PERMISSION_WRITE_STORAGE
import com.eagle.commons.helpers.REAL_FILE_PATH
import com.eagle.commons.helpers.SIDELOADING_TRUE
import com.eagle.gallery.pro.BuildConfig
import com.eagle.gallery.pro.R
import com.eagle.gallery.pro.extensions.config
import com.eagle.gallery.pro.extensions.hideSystemUI
import com.eagle.gallery.pro.extensions.navigationBarHeight
import com.eagle.gallery.pro.extensions.openEditor
import com.eagle.gallery.pro.extensions.openPath
import com.eagle.gallery.pro.extensions.parseFileChannel
import com.eagle.gallery.pro.extensions.setAs
import com.eagle.gallery.pro.extensions.sharePath
import com.eagle.gallery.pro.extensions.showSystemUI
import com.eagle.gallery.pro.fragments.PhotoFragment
import com.eagle.gallery.pro.fragments.VideoFragment
import com.eagle.gallery.pro.fragments.ViewPagerFragment
import com.eagle.gallery.pro.helpers.BOTTOM_ACTION_EDIT
import com.eagle.gallery.pro.helpers.BOTTOM_ACTION_PROPERTIES
import com.eagle.gallery.pro.helpers.BOTTOM_ACTION_SET_AS
import com.eagle.gallery.pro.helpers.BOTTOM_ACTION_SHARE
import com.eagle.gallery.pro.helpers.IS_VIEW_INTENT
import com.eagle.gallery.pro.helpers.MEDIUM
import com.eagle.gallery.pro.helpers.PATH
import com.eagle.gallery.pro.helpers.TYPE_GIFS
import com.eagle.gallery.pro.helpers.TYPE_IMAGES
import com.eagle.gallery.pro.helpers.TYPE_RAWS
import com.eagle.gallery.pro.helpers.TYPE_SVGS
import com.eagle.gallery.pro.helpers.TYPE_VIDEOS
import com.eagle.gallery.pro.models.Medium
import kotlinx.android.synthetic.main.bottom_actions.bottom_change_orientation
import kotlinx.android.synthetic.main.bottom_actions.bottom_copy
import kotlinx.android.synthetic.main.bottom_actions.bottom_delete
import kotlinx.android.synthetic.main.bottom_actions.bottom_edit
import kotlinx.android.synthetic.main.bottom_actions.bottom_favorite
import kotlinx.android.synthetic.main.bottom_actions.bottom_move
import kotlinx.android.synthetic.main.bottom_actions.bottom_properties
import kotlinx.android.synthetic.main.bottom_actions.bottom_rename
import kotlinx.android.synthetic.main.bottom_actions.bottom_rotate
import kotlinx.android.synthetic.main.bottom_actions.bottom_set_as
import kotlinx.android.synthetic.main.bottom_actions.bottom_share
import kotlinx.android.synthetic.main.bottom_actions.bottom_show_on_map
import kotlinx.android.synthetic.main.bottom_actions.bottom_slideshow
import kotlinx.android.synthetic.main.bottom_actions.bottom_toggle_file_visibility
import kotlinx.android.synthetic.main.fragment_holder.bottom_actions
import kotlinx.android.synthetic.main.fragment_holder.fragment_holder
import kotlinx.android.synthetic.main.fragment_holder.top_shadow
import java.io.File
import java.io.FileInputStream

open class PhotoVideoActivity : com.eagle.gallery.pro.activities.SimpleActivity(),
    ViewPagerFragment.FragmentListener {
    private var mMedium: Medium? = null
    private var mIsFullScreen = false
    private var mIsFromGallery = false
    private var mFragment: ViewPagerFragment? = null
    private var mUri: Uri? = null

    var mIsVideo = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_holder)

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
                .replace(R.id.fragment_placeholder, mFragment!!).commit()
        }

        if (config.blackBackground) {
            fragment_holder.background = ColorDrawable(Color.BLACK)
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
                com.eagle.gallery.pro.activities.PanoramaVideoActivity::class.java
            ).apply {
                putExtra(PATH, realPath)
                startActivity(this)
            }
        } else {
            val mimeType = getUriMimeType(mUri.toString(), newUri)
            Intent(
                applicationContext,
                com.eagle.gallery.pro.activities.VideoPlayerActivity::class.java
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
        Intent(this, com.eagle.gallery.pro.activities.ViewPagerActivity::class.java).apply {
            putExtra(IS_VIEW_INTENT, true)
            putExtra(IS_FROM_GALLERY, mIsFromGallery)
            putExtra(PATH, path)
            startActivity(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.photo_video_menu, menu)
        val visibleBottomActions = if (config.bottomActions) config.visibleBottomActions else 0

        menu.apply {
            findItem(R.id.menu_set_as).isVisible =
                mMedium?.isImage() == true && visibleBottomActions and BOTTOM_ACTION_SET_AS == 0
            findItem(R.id.menu_edit).isVisible =
                mMedium?.isImage() == true && mUri?.scheme == "file" && visibleBottomActions and BOTTOM_ACTION_EDIT == 0
            findItem(R.id.menu_properties).isVisible =
                mUri?.scheme == "file" && visibleBottomActions and BOTTOM_ACTION_PROPERTIES == 0
            findItem(R.id.menu_share).isVisible = visibleBottomActions and BOTTOM_ACTION_SHARE == 0
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (mMedium == null || mUri == null) {
            return true
        }

        when (item.itemId) {
            R.id.menu_set_as -> setAs(mUri!!.toString())
            R.id.menu_open_with -> openPath(mUri!!.toString(), true)
            R.id.menu_share -> sharePath(mUri!!.toString())
            R.id.menu_edit -> openEditor(mUri!!.toString())
            R.id.menu_properties -> showProperties()
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
            bottom_favorite,
            bottom_delete,
            bottom_rotate,
            bottom_properties,
            bottom_change_orientation,
            bottom_slideshow,
            bottom_show_on_map,
            bottom_toggle_file_visibility,
            bottom_rename,
            bottom_copy,
            bottom_move
        ).forEach {
            it.beGone()
        }

        val visibleBottomActions = if (config.bottomActions) config.visibleBottomActions else 0
        bottom_edit.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_EDIT != 0 && mMedium?.isImage() == true)
        bottom_edit.setOnClickListener {
            if (mUri != null && bottom_actions.alpha == 1f) {
                openEditor(mUri!!.toString())
            }
        }

        bottom_share.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_SHARE != 0)
        bottom_share.setOnClickListener {
            if (mUri != null && bottom_actions.alpha == 1f) {
                sharePath(mUri!!.toString())
            }
        }

        bottom_set_as.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_SET_AS != 0 && mMedium?.isImage() == true)
        bottom_set_as.setOnClickListener {
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
        top_shadow.animate().alpha(newAlpha).start()
        if (!bottom_actions.isGone()) {
            bottom_actions.animate().alpha(newAlpha).start()
        }
    }

    override fun videoEnded() = false

    override fun goToPrevItem() {}

    override fun goToNextItem() {}

    override fun launchViewVideoIntent(path: String) {}
}
