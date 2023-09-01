package com.roy.gallery.pro.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.media.ExifInterface.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.roy.gestures.GestureController
import com.roy.gestures.State
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions.withCrossFade
import com.bumptech.glide.load.resource.bitmap.Rotate
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.bumptech.glide.request.target.Target
import com.roy.labs.subscaleview.DecoderFactory
import com.roy.labs.subscaleview.ImageDecoder
import com.roy.labs.subscaleview.ImageRegionDecoder
import com.roy.labs.subscaleview.SubsamplingScaleImageView
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.*
import com.roy.gallery.pro.helpers.MEDIUM
import com.roy.gallery.pro.helpers.PATH
import com.roy.gallery.pro.helpers.PicassoDecoder
import com.roy.gallery.pro.helpers.PicassoRegionDecoder
import com.roy.gallery.pro.models.Medium
import com.roy.gallery.pro.svg.SvgSoftwareLayerSetter
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.beGone
import com.roy.commons.ext.beInvisible
import com.roy.commons.ext.beVisible
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.getRealPathFromURI
import com.roy.commons.ext.isPathOnOTG
import com.roy.commons.ext.isVisible
import com.roy.commons.ext.onGlobalLayout
import com.roy.commons.ext.toast
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import it.sephiroth.android.library.exif2.ExifInterface
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.android.synthetic.main.v_pager_photo_item.view.*
import org.apache.sanselan.common.byteSources.ByteSourceInputStream
import org.apache.sanselan.formats.jpeg.JpegImageParser
import pl.droidsonroids.gif.InputSource
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.math.abs

class PhotoFragment : ViewPagerFragment() {
    private val defaultDoubleTapZoom = 2f
    private val zoomableViewLoadDelay = 150L
    private val sameAspectRatioThreshold = 0.01

    // devices with good displays, but the rest of the hardware not good enough for them
    private val weirdDevices = arrayListOf(
        "motorola xt1685", "google nexus 5x"
    )

    var mCurrentRotationDegrees = 0
    private var mIsFragmentVisible = false
    private var mIsFullscreen = false
    private var mWasInit = false
    private var mIsPanorama = false
    private var mIsSubsamplingVisible =
        false    // checking view.visibility is unreliable, use an extra variable for it
    private var mImageOrientation = -1
    private var mLoadZoomableViewHandler = Handler(Looper.getMainLooper())
    private var mScreenWidth = 0
    private var mScreenHeight = 0
    private var mCurrentGestureViewZoom = 1f

    private var mStoredShowExtendedDetails = false
    private var mStoredHideExtendedDetails = false
    private var mStoredAllowDeepZoomableImages = false
    private var mStoredShowHighestQuality = false
    private var mBlackBackground = false
    private var mStoredExtendedDetails = 0

    private lateinit var mView: ViewGroup
    private lateinit var mMedium: Medium

    @SuppressLint("ClickableViewAccessibility", "WrongThread")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        mView =
            (inflater.inflate(R.layout.v_pager_photo_item, container, false) as ViewGroup).apply {
                subsamplingView.setOnClickListener { photoClicked() }
                gesturesView.setOnClickListener { photoClicked() }
                gifView.setOnClickListener { photoClicked() }
                instantPrevItem.setOnClickListener { listener?.goToPrevItem() }
                instantNextItem.setOnClickListener { listener?.goToNextItem() }
                panoramaOutline.setOnClickListener { openPanorama() }

                instantPrevItem.parentView = container
                instantNextItem.parentView = container

                photoBrightnessController.initialize(
                    activity = requireActivity(),
                    slideInfoView = slideInfo,
                    isBrightness = true,
                    parentView = container
                ) { x, y ->
                    mView.apply {
                        if (subsamplingView.isVisible()) {
                            subsamplingView.sendFakeClick(x, y)
                        } else {
                            gesturesView.sendFakeClick(x, y)
                        }
                    }
                }

                if (context.config.allowDownGesture) {
                    gifView.setOnTouchListener { _, event ->
                        if (gifViewFrame.controller.state.zoom == 1f) {
                            handleEvent(event)
                        }
                        false
                    }

                    gesturesView.controller.addOnStateChangeListener(object :
                        GestureController.OnStateChangeListener {
                        override fun onStateChanged(state: State) {
                            mCurrentGestureViewZoom = state.zoom
                        }

                        override fun onStateReset(oldState: State?, newState: State?) {
                        }
                    })

                    gesturesView.setOnTouchListener { _, event ->
                        if (mCurrentGestureViewZoom == 1f) {
                            handleEvent(event)
                        }
                        false
                    }

                    subsamplingView.setOnTouchListener { _, event ->
                        if (subsamplingView.isZoomedOut()) {
                            handleEvent(event)
                        }
                        false
                    }
                }
            }

        checkScreenDimensions()
        storeStateVariables()
        if (!mIsFragmentVisible && activity is com.roy.gallery.pro.activities.PhotoActivity) {
            mIsFragmentVisible = true
        }

        mMedium = requireArguments().getSerializable(MEDIUM) as Medium
        if (mMedium.path.startsWith("content://") && !mMedium.path.startsWith("content://mms/")) {
            val originalPath = mMedium.path
            mMedium.path =
                requireContext().getRealPathFromURI(Uri.parse(originalPath)) ?: mMedium.path

            if (mMedium.path.isEmpty()) {
                var out: FileOutputStream? = null
                try {
                    var inputStream =
                        requireContext().contentResolver.openInputStream(Uri.parse(originalPath))
                    val exif = ExifInterface()
                    exif.readExif(inputStream, ExifInterface.Options.OPTION_ALL)
                    val tag = exif.getTag(ExifInterface.TAG_ORIENTATION)
                    val orientation = tag?.getValueAsInt(-1) ?: -1
                    inputStream =
                        requireContext().contentResolver.openInputStream(Uri.parse(originalPath))
                    val original = BitmapFactory.decodeStream(inputStream)
                    val rotated = rotateViaMatrix(original, orientation)
                    exif.setTagValue(ExifInterface.TAG_ORIENTATION, 1)
                    exif.removeCompressedThumbnail()

                    val file = File(
                        requireContext().externalCacheDir, Uri.parse(originalPath).lastPathSegment
                    )
                    out = FileOutputStream(file)
                    rotated.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    mMedium.path = file.absolutePath
                } catch (e: Exception) {
                    requireActivity().toast(R.string.unknown_error_occurred)
                    return mView
                } finally {
                    out?.close()
                }
            }
        }

        mIsFullscreen =
            requireActivity().window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN == View.SYSTEM_UI_FLAG_FULLSCREEN
        loadImage()
        initExtendedDetails()
        mWasInit = true
        checkIfPanorama()
        updateInstantSwitchWidths()

        return mView
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onResume() {
        super.onResume()
        val config = requireContext().config
        if (mWasInit && (config.showExtendedDetails != mStoredShowExtendedDetails || config.extendedDetails != mStoredExtendedDetails)) {
            initExtendedDetails()
        }

        if (mWasInit) {
            if (config.allowZoomingImages != mStoredAllowDeepZoomableImages || config.showHighestQuality != mStoredShowHighestQuality || config.blackBackground != mBlackBackground) {
                mIsSubsamplingVisible = false
                mView.subsamplingView.beGone()
                loadImage()
            }/*else if (mMedium.isGIF()) {
                loadGif()
            }*/
        }

        val allowPhotoGestures = config.allowPhotoGestures
        val allowInstantChange = config.allowInstantChange

        mView.apply {
            photoBrightnessController.beVisibleIf(allowPhotoGestures)
            instantPrevItem.beVisibleIf(allowInstantChange)
            instantNextItem.beVisibleIf(allowInstantChange)
        }

        storeStateVariables()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (activity?.isDestroyed == false) {
            mView.subsamplingView.recycle()
        }

        mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
        if (mCurrentRotationDegrees != 0) {
            Thread {
                val path = mMedium.path
                (activity as? BaseSimpleActivity)?.saveRotatedImageToFile(
                    oldPath = path,
                    newPath = path,
                    degrees = mCurrentRotationDegrees,
                    showToasts = false
                ) {}
            }.start()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // avoid GIFs being skewed, played in wrong aspect ratio
        if (mMedium.isGIF()) {
            mView.onGlobalLayout {
                measureScreen()
                Handler(Looper.getMainLooper()).postDelayed({
                    mView.gifViewFrame.controller.resetState()
                    loadGif()
                }, 50)
            }
        } else {
            hideZoomableView()
            loadImage()
        }

        initExtendedDetails()
        updateInstantSwitchWidths()
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        mIsFragmentVisible = menuVisible
        if (mWasInit) {
            if (!mMedium.isGIF()) {
                photoFragmentVisibilityChanged(menuVisible)
            }
        }
    }

    fun doVisible() {
        if (!mMedium.isGIF()) {
            mView.gesturesView?.beVisible()
        }
    }

    private fun storeStateVariables() {
        requireContext().config.apply {
            mStoredShowExtendedDetails = showExtendedDetails
            mStoredHideExtendedDetails = hideExtendedDetails
            mStoredAllowDeepZoomableImages = allowZoomingImages
            mStoredShowHighestQuality = showHighestQuality
            mStoredExtendedDetails = extendedDetails
            mBlackBackground = blackBackground
        }
    }

    private fun checkScreenDimensions() {
        if (mScreenWidth == 0 || mScreenHeight == 0) {
            measureScreen()
        }
    }

    private fun measureScreen() {
        val metrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getRealMetrics(metrics)
        mScreenWidth = metrics.widthPixels
        mScreenHeight = metrics.heightPixels
    }

    private fun photoFragmentVisibilityChanged(isVisible: Boolean) {
        if (isVisible) {
            scheduleZoomableView()
        } else {
            hideZoomableView()
        }
    }

    private fun degreesForRotation(orientation: Int) = when (orientation) {
        ORIENTATION_ROTATE_270 -> 270
        ORIENTATION_ROTATE_180 -> 180
        ORIENTATION_ROTATE_90 -> 90
        else -> 0
    }

    private fun rotateViaMatrix(original: Bitmap, orientation: Int): Bitmap {
        val degrees = degreesForRotation(orientation).toFloat()
        return if (degrees == 0f) {
            original
        } else {
            val matrix = Matrix()
            matrix.setRotate(degrees)
            Bitmap.createBitmap(/* source = */ original,/* x = */
                0,/* y = */
                0,/* width = */
                original.width,/* height = */
                original.height,/* m = */
                matrix,/* filter = */
                true
            )
        }
    }

    private fun loadImage() {
        var isBlur = false
        checkScreenDimensions()
        mImageOrientation = getImageOrientation()
        when {
            mMedium.isGIF() -> {
                loadGif()
                isBlur = true
            }

            mMedium.isSVG() -> {
                loadSVG()
            }

            else -> {
                loadBitmap()
                isBlur = true
            }
        }
        if (isBlur && !requireContext().config.blackBackground) {
            mView.imgvBg.beVisible()
            Glide.with(requireContext()).asBitmap().load(mMedium.path).transition(withCrossFade())
                .thumbnail(0.2f).apply(bitmapTransform(BlurTransformation(60, 3)))
                .into(mView.imgvBg)
        } else {
            mView.imgvBg.beGone()
        }
    }

    private fun loadGif() {
        try {
            val path = mMedium.path
            val source = if (path.startsWith("content://") || path.startsWith("file://")) {
                InputSource.UriSource(requireContext().contentResolver, Uri.parse(path))
            } else {
                InputSource.FileSource(path)
            }

            mView.apply {
                gesturesView.beGone()
                gifView.setInputSource(source)
                gifViewFrame.beVisible()
            }
        } catch (e: Exception) {
            loadBitmap()
        } catch (e: OutOfMemoryError) {
            loadBitmap()
        }
    }

    private fun loadSVG() {
        Glide.with(requireContext()).`as`(PictureDrawable::class.java)
            .listener(SvgSoftwareLayerSetter()).load(mMedium.path).into(mView.gesturesView)
    }

    @SuppressLint("CheckResult")
    private fun loadBitmap(addZoomableView: Boolean = true) {
        val options = RequestOptions().signature(mMedium.path.getFileSignature())
            .format(DecodeFormat.PREFER_ARGB_8888).diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .fitCenter()

        if (mCurrentRotationDegrees != 0) {
            options.transform(Rotate(mCurrentRotationDegrees))
            options.diskCacheStrategy(DiskCacheStrategy.NONE)
        }

        Glide.with(requireContext()).load(mMedium.path).apply(options)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean,
                ): Boolean {
                    if (activity != null && !activity!!.isDestroyed && !activity!!.isFinishing) {
                        tryLoadingWithPicasso(addZoomableView)
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean,
                ): Boolean {
                    mView.gesturesView.controller.settings.isZoomEnabled =
                        mCurrentRotationDegrees != 0 || context?.config?.allowZoomingImages == false
                    if (mIsFragmentVisible && addZoomableView) {
                        scheduleZoomableView()
                    }
                    return false
                }
            }).into(mView.gesturesView)
    }

    private fun tryLoadingWithPicasso(addZoomableView: Boolean) {
        var pathToLoad =
            if (mMedium.path.startsWith("content://")) mMedium.path else "file://${mMedium.path}"
        pathToLoad = pathToLoad.replace("%", "%25").replace("#", "%23")

        try {
            val picasso =
                Picasso.get().load(pathToLoad).centerInside().stableKey(mMedium.path.getFileKey())
                    .resize(mScreenWidth, mScreenHeight)

            if (mCurrentRotationDegrees != 0) {
                picasso.rotate(mCurrentRotationDegrees.toFloat())
            } else {
                degreesForRotation(mImageOrientation).toFloat()
            }

            picasso.into(mView.gesturesView, object : Callback {
                override fun onSuccess() {
                    mView.gesturesView.controller.settings.isZoomEnabled =
                        mCurrentRotationDegrees != 0 || context?.config?.allowZoomingImages == false
                    if (mIsFragmentVisible && addZoomableView) {
                        scheduleZoomableView()
                    }
                }

                override fun onError(e: Exception?) {
                    e?.printStackTrace()
                }
            })
        } catch (ignored: Exception) {
            ignored.printStackTrace()
        }
    }

    private fun openPanorama() {
        Intent(context, com.roy.gallery.pro.activities.PanoramaPhotoActivity::class.java).apply {
            putExtra(PATH, mMedium.path)
            startActivity(this)
        }
    }

    private fun scheduleZoomableView() {
        mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
        mLoadZoomableViewHandler.postDelayed({
            if (mIsFragmentVisible && context?.config?.allowZoomingImages == true && mMedium.isImage() && !mIsSubsamplingVisible) {
                addZoomableView()
            }
        }, zoomableViewLoadDelay)
    }

    private fun addZoomableView() {
        val rotation = degreesForRotation(mImageOrientation)
        mIsSubsamplingVisible = true
        val config = requireContext().config
        val showHighestQuality = config.showHighestQuality

        val bitmapDecoder = object : DecoderFactory<ImageDecoder> {
            override fun make() = PicassoDecoder(mMedium.path, Picasso.get(), rotation)
        }

        val regionDecoder = object : DecoderFactory<ImageRegionDecoder> {
            override fun make() = PicassoRegionDecoder(showHighestQuality)
        }

        var newOrientation = (rotation + mCurrentRotationDegrees) % 360
        if (newOrientation < 0) {
            newOrientation += 360
        }

        mView.subsamplingView.apply {
            setMaxTileSize(if (showHighestQuality) Integer.MAX_VALUE else 4096)
            setMinimumTileDpi(if (showHighestQuality) -1 else getMinTileDpi())
            background = ColorDrawable(Color.TRANSPARENT)
            bitmapDecoderFactory = bitmapDecoder
            regionDecoderFactory = regionDecoder
            maxScale = 10f
            beVisible()
            rotationEnabled = config.allowRotatingWithGestures
            isOneToOneZoomEnabled = config.allowOneToOneZoom
            orientation = newOrientation
            setImage(mMedium.path)
            onImageEventListener = object : SubsamplingScaleImageView.OnImageEventListener {
                override fun onReady() {/*background = ColorDrawable(if (config.blackBackground) Color.BLACK else config.backgroundColor)*/
                    val useWidth =
                        if (mImageOrientation == ORIENTATION_ROTATE_90 || mImageOrientation == ORIENTATION_ROTATE_270) sHeight else sWidth
                    val useHeight =
                        if (mImageOrientation == ORIENTATION_ROTATE_90 || mImageOrientation == ORIENTATION_ROTATE_270) sWidth else sHeight
                    doubleTapZoomScale = getDoubleTapZoomScale(useWidth, useHeight)
                    mView.gesturesView.beGone()
                }

                override fun onImageLoadError(e: Exception) {
                    mView.gesturesView.controller.settings.isZoomEnabled = true
                    background = ColorDrawable(Color.TRANSPARENT)
                    mIsSubsamplingVisible = false
                    beGone()
                }

                override fun onImageRotation(degrees: Int) {
                    val fullRotation = (rotation + degrees) % 360
                    val useWidth =
                        if (fullRotation == 90 || fullRotation == 270) sHeight else sWidth
                    val useHeight =
                        if (fullRotation == 90 || fullRotation == 270) sWidth else sHeight
                    doubleTapZoomScale = getDoubleTapZoomScale(useWidth, useHeight)
                    mCurrentRotationDegrees = (mCurrentRotationDegrees + degrees) % 360
                    loadBitmap(false)
                    activity?.invalidateOptionsMenu()
                }
            }
        }
    }

    private fun getMinTileDpi(): Int {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        val device = "${Build.BRAND} ${Build.MODEL}".lowercase(Locale.getDefault())
        return when {
            weirdDevices.contains(device) -> 240
            averageDpi > 400 -> 280
            averageDpi > 300 -> 220
            else -> 160
        }
    }

    private fun checkIfPanorama() {
        mIsPanorama = try {
            val inputStream =
                if (mMedium.path.startsWith("content:/")) requireContext().contentResolver.openInputStream(
                    Uri.parse(mMedium.path)
                ) else File(mMedium.path).inputStream()
            val imageParser = JpegImageParser().getXmpXml(
                ByteSourceInputStream(inputStream, mMedium.name), HashMap<String, Any>()
            )
            imageParser.contains(
                other = "GPano:UsePanoramaViewer=\"True\"", ignoreCase = true
            ) || imageParser.contains(
                other = "<GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>", ignoreCase = true
            )
        } catch (e: Exception) {
            false
        } catch (e: OutOfMemoryError) {
            false
        }

        mView.panoramaOutline.beVisibleIf(mIsPanorama)
        if (mIsFullscreen) {
            mView.panoramaOutline.alpha = 0f
        }
    }

    private fun getImageOrientation(): Int {
        val defaultOrientation = -1
        var orient = defaultOrientation

        try {
            val path = mMedium.path
            orient = if (path.startsWith("content:/")) {
                val inputStream = requireContext().contentResolver.openInputStream(Uri.parse(path))
                val exif = ExifInterface()
                exif.readExif(inputStream, ExifInterface.Options.OPTION_ALL)
                val tag = exif.getTag(ExifInterface.TAG_ORIENTATION)
                tag?.getValueAsInt(defaultOrientation) ?: defaultOrientation
            } else {
                val exif = android.media.ExifInterface(path)
                exif.getAttributeInt(TAG_ORIENTATION, defaultOrientation)
            }

            if (orient == defaultOrientation || requireContext().isPathOnOTG(mMedium.path)) {
                val uri =
                    if (path.startsWith("content:/")) Uri.parse(path) else Uri.fromFile(File(path))
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val exif2 = ExifInterface()
                exif2.readExif(inputStream, ExifInterface.Options.OPTION_ALL)
                orient =
                    exif2.getTag(ExifInterface.TAG_ORIENTATION)?.getValueAsInt(defaultOrientation)
                        ?: defaultOrientation
            }
        } catch (ignored: Exception) {
        } catch (ignored: OutOfMemoryError) {
        }
        return orient
    }

    private fun getDoubleTapZoomScale(width: Int, height: Int): Float {
        val bitmapAspectRatio = height / width.toFloat()
        val screenAspectRatio = mScreenHeight / mScreenWidth.toFloat()

        return if (context == null || abs(bitmapAspectRatio - screenAspectRatio) < sameAspectRatioThreshold) {
            defaultDoubleTapZoom
        } else if (requireContext().portrait && bitmapAspectRatio <= screenAspectRatio) {
            mScreenHeight / height.toFloat()
        } else if (requireContext().portrait && bitmapAspectRatio > screenAspectRatio) {
            mScreenWidth / width.toFloat()
        } else if (!requireContext().portrait && bitmapAspectRatio >= screenAspectRatio) {
            mScreenWidth / width.toFloat()
        } else if (!requireContext().portrait && bitmapAspectRatio < screenAspectRatio) {
            mScreenHeight / height.toFloat()
        } else {
            defaultDoubleTapZoom
        }
    }

    fun rotateImageViewBy(degrees: Int) {
        if (mIsSubsamplingVisible) {
            mView.subsamplingView.rotateBy(degrees)
        } else {
            mCurrentRotationDegrees = (mCurrentRotationDegrees + degrees) % 360
            mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
            mIsSubsamplingVisible = false
            loadBitmap()
        }
    }

    private fun initExtendedDetails() {
        if (requireContext().config.showExtendedDetails) {
            mView.photoDetails.apply {
                beInvisible()   // make it invisible so we can measure it, but not show yet
                text = getMediumExtendedDetails(mMedium)
                onGlobalLayout {
                    if (isAdded) {
                        val realY = getExtendedDetailsY(height)
                        if (realY > 0) {
                            y = realY
                            beVisibleIf(text.isNotEmpty())
                            alpha =
                                if (!requireContext().config.hideExtendedDetails || !mIsFullscreen) 1f else 0f
                        }
                    }
                }
            }
        } else {
            mView.photoDetails.beGone()
        }
    }

    private fun hideZoomableView() {
        if (context?.config?.allowZoomingImages == true) {
            mIsSubsamplingVisible = false
            mView.subsamplingView.recycle()
            mView.subsamplingView.beGone()
            mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun photoClicked() {
        listener?.fragmentClicked()
    }

    private fun updateInstantSwitchWidths() {
        val newWidth =
            resources.getDimension(R.dimen.instant_change_bar_width) + if (activity?.portrait == false) requireActivity().navigationBarWidth else 0
        mView.instantPrevItem.layoutParams.width = newWidth.toInt()
        mView.instantNextItem.layoutParams.width = newWidth.toInt()
    }

    override fun fullscreenToggled(isFullscreen: Boolean) {
        this.mIsFullscreen = isFullscreen
        mView.apply {
            photoDetails.apply {
                if (mStoredShowExtendedDetails && isVisible()) {
                    animate().y(getExtendedDetailsY(height))

                    if (mStoredHideExtendedDetails) {
                        animate().alpha(if (isFullscreen) 0f else 1f).start()
                    }
                }
            }

            if (mIsPanorama) {
                panoramaOutline.animate().alpha(if (isFullscreen) 0f else 1f).start()
            }
        }
    }

    private fun getExtendedDetailsY(height: Int): Float {
        val smallMargin = resources.getDimension(R.dimen.small_margin)
        val fullscreenOffset =
            smallMargin + if (mIsFullscreen) 0 else requireContext().navigationBarHeight
        val actionsHeight =
            if (requireContext().config.bottomActions && !mIsFullscreen) resources.getDimension(R.dimen.bottom_actions_height) else 0f
        return requireContext().realScreenSize.y - height - actionsHeight - fullscreenOffset
    }
}
