package com.roy.labs.subscaleview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.Paint.Style
import android.net.Uri
import android.os.AsyncTask
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageView
import java.io.File
import java.io.UnsupportedEncodingException
import java.lang.ref.WeakReference
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// rotation inspired by https://github.com/IndoorAtlas/subsampling-scale-image-view/tree/feature_rotation
open class SubsamplingScaleImageView @JvmOverloads constructor(
    context: Context,
    attr: AttributeSet? = null,
) : ImageView(context, attr) {
    companion object {
        const val FILE_SCHEME = "file://"
        const val ASSET_PREFIX = "$FILE_SCHEME/android_asset/"

        private val TAG = SubsamplingScaleImageView::class.java.simpleName

        private const val ORIENTATION_USE_EXIF = -1
        private const val ORIENTATION_0 = 0
        private const val ORIENTATION_90 = 90
        private const val ORIENTATION_180 = 180
        private const val ORIENTATION_270 = 270

        private const val EASE_OUT_QUAD = 1
        private const val EASE_IN_OUT_QUAD = 2

        private const val TILE_SIZE_AUTO = Integer.MAX_VALUE
        private const val ANIMATION_DURATION = 300L
        private val FIFTEEN_DEGREES = Math.toRadians(15.0)
    }

    var maxScale = 2f
    var isOneToOneZoomEnabled = false
    var rotationEnabled = true
    var eagerLoadingEnabled = false
    var debug = false
    var onImageEventListener: OnImageEventListener? = null
    var doubleTapZoomScale = 1f
    var bitmapDecoderFactory: DecoderFactory<out ImageDecoder> =
        CompatDecoderFactory(SkiaImageDecoder::class.java)
    var regionDecoderFactory: DecoderFactory<out ImageRegionDecoder> =
        CompatDecoderFactory(SkiaImageRegionDecoder::class.java)
    var scale = 0f
    var sWidth = 0
    var sHeight = 0
    var orientation = ORIENTATION_0

    private var bitmap: Bitmap? = null
    private var uri: Uri? = null
    private var fullImageSampleSize = 0
    private var tileMap: MutableMap<Int, List<Tile>>? = null
    private var minimumTileDpi = -1
    private var maxTileWidth = TILE_SIZE_AUTO
    private var maxTileHeight = TILE_SIZE_AUTO
    private var scaleStart = 0f

    private var imageRotation = 0f
    private var cos = Math.cos(0.0)
    private var sin = Math.sin(0.0)

    private var vTranslate: PointF? = null
    private var vTranslateStart: PointF? = null
    private var vTranslateBefore: PointF? = null

    private var pendingScale: Float? = null
    private var sPendingCenter: PointF? = null

    private var sOrientation = 0

    private var isZooming = false
    private var isPanning = false
    private var isQuickScaling = false
    private var maxTouchCount = 0
    private var didZoomInGesture = false
    private var prevDegrees = 0

    private var detector: GestureDetector? = null
    private var singleDetector: GestureDetector? = null

    private var decoder: ImageRegionDecoder? = null
    private val decoderLock = ReentrantReadWriteLock(true)

    private var sCenterStart: PointF? = null
    private var vCenterStart: PointF? = null
    private var vCenterStartNow: PointF? = null
    private var vDistStart = 0f
    private var lastAngle = 0f

    private val quickScaleThreshold: Float
    private var quickScaleLastDistance = 0f
    private var quickScaleMoved = false
    private var quickScaleVLastPoint: PointF? = null
    private var quickScaleSCenter: PointF? = null
    private var quickScaleVStart: PointF? = null

    private var anim: Anim? = null
    private var isReady = false
    private var isImageLoaded = false

    private var bitmapPaint: Paint? = null
    private var debugTextPaint: Paint? = null
    private var debugLinePaint: Paint? = null

    private var satTemp: ScaleTranslateRotate? = null
    private var objectMatrix: Matrix? = null
    private val srcArray = FloatArray(8)
    private val dstArray = FloatArray(8)

    private val density = resources.displayMetrics.density

    init {
        setMinimumDpi(160)
        setDoubleTapZoomDpi(160)
        setMinimumTileDpi(320)
        setGestureDetector(context)
        quickScaleThreshold = TypedValue.applyDimension(
            /* unit = */ TypedValue.COMPLEX_UNIT_DIP,
            /* value = */ 20f,
            /* metrics = */ context.resources.displayMetrics
        )
    }

    private fun getIsBaseLayerReady(): Boolean {
        if (bitmap != null) {
            return true
        } else if (tileMap != null) {
            var baseLayerReady = true
            tileMap?.let {
                for ((key, value) in it) {
                    if (key == fullImageSampleSize) {
                        for (tile in value) {
                            if (tile.loading || tile.bitmap == null) {
                                baseLayerReady = false
                            }
                        }
                    }
                }
            }
            return baseLayerReady
        }
        return false
    }

    private fun getRequiredRotation() =
        if (orientation == ORIENTATION_USE_EXIF) sOrientation else orientation

    private fun getCenter(): PointF? {
        val centerX = width / 2
        val centerY = height / 2
        return viewToSourceCoord(vx = centerX.toFloat(), vy = centerY.toFloat())
    }

    fun setImage(path: String) {
        reset(true)

        var newPath = path
        if (!newPath.contains("://")) {
            if (newPath.startsWith("/")) {
                newPath = path.substring(1)
            }
            newPath = "$FILE_SCHEME/$newPath"
        }

        if (newPath.startsWith(FILE_SCHEME)) {
            val uriFile = File(newPath.substring(FILE_SCHEME.length))
            if (!uriFile.exists()) {
                try {
                    newPath = URLDecoder.decode(/* s = */ newPath, /* enc = */ "UTF-8")
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
            }
        }

        uri = Uri.parse(newPath)
        uri?.let {
            val task = TilesInitTask(
                view = this,
                context = context,
                decoderFactory = regionDecoderFactory,
                source = it
            )
            execute(task)
        }
    }

    private fun reset(newImage: Boolean) {
        scale = 0f
        scaleStart = 0f
        imageRotation = 0f
        vTranslate = null
        vTranslateStart = null
        vTranslateBefore = null
        pendingScale = null
        sPendingCenter = null
        isZooming = false
        isPanning = false
        isQuickScaling = false
        maxTouchCount = 0
        fullImageSampleSize = 0
        sCenterStart = null
        vCenterStart = null
        vCenterStartNow = null
        vDistStart = 0f
        lastAngle = 0f
        quickScaleLastDistance = 0f
        quickScaleMoved = false
        quickScaleSCenter = null
        quickScaleVLastPoint = null
        quickScaleVStart = null
        anim = null
        satTemp = null
        objectMatrix = null

        if (newImage) {
            uri = null
            decoderLock.writeLock().lock()
            try {
                decoder?.recycle()
                decoder = null
            } finally {
                decoderLock.writeLock().unlock()
            }

            bitmap?.recycle()

            prevDegrees = 0
            sWidth = 0
            sHeight = 0
            sOrientation = 0
            isReady = false
            isImageLoaded = false
            bitmap = null
            cos = cos(0.0)
            sin = sin(0.0)
        }

        tileMap?.values?.forEach {
            for (tile in it) {
                tile.visible = false
                tile.bitmap?.recycle()
                tile.bitmap = null
            }
        }
        tileMap = null
        setGestureDetector(context)
    }

    private fun setGestureDetector(context: Context) {
        detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (isReady && vTranslate != null && (abs(e1.x - e2.x) > 50 || abs(
                        e1.y - e2.y
                    ) > 50) && (abs(velocityX) > 500 || abs(velocityY) > 500) && !isZooming
                ) {
                    val vX = (velocityX * cos - velocityY * -sin).toFloat()
                    val vY = (velocityX * -sin + velocityY * cos).toFloat()

                    val vTranslateEnd =
                        PointF(vTranslate!!.x + vX * 0.25f, vTranslate!!.y + vY * 0.25f)
                    val sCenterXEnd = (width / 2 - vTranslateEnd.x) / scale
                    val sCenterYEnd = (height / 2 - vTranslateEnd.y) / scale
                    AnimationBuilder(
                        PointF(
                            /* x = */ sCenterXEnd,
                            /* y = */ sCenterYEnd
                        )
                    ).apply {
                        interruptible = true
                        easing = EASE_OUT_QUAD
                        start()
                    }
                    return true
                }
                return super.onFling(e1, e2, velocityX, velocityY)
            }

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                performClick()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (isReady && vTranslate != null) {
                    setGestureDetector(context)
                    vCenterStart = PointF(event.x, event.y)
                    vTranslateStart = PointF(
                        /* x = */ vTranslate!!.x,
                        /* y = */ vTranslate!!.y
                    )
                    scaleStart = scale
                    isQuickScaling = true
                    isZooming = true
                    quickScaleLastDistance = -1f
                    quickScaleSCenter = viewToSourceCoord(vCenterStart!!)
                    quickScaleVStart = PointF(event.x, event.y)
                    quickScaleVLastPoint = PointF(
                        /* x = */ quickScaleSCenter!!.x,
                        /* y = */ quickScaleSCenter!!.y
                    )
                    quickScaleMoved = false
                    return false
                }
                return super.onDoubleTapEvent(event)
            }
        })

        singleDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                    performClick()
                    return true
                }
            })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val sCenter = getCenter()
        if (isReady && sCenter != null) {
            anim = null
            pendingScale = scale
            sPendingCenter = sCenter
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpecMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSpecMode = MeasureSpec.getMode(heightMeasureSpec)
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val resizeWidth = widthSpecMode != MeasureSpec.EXACTLY
        val resizeHeight = heightSpecMode != MeasureSpec.EXACTLY
        var width = parentWidth
        var height = parentHeight
        if (sWidth > 0 && sHeight > 0) {
            if (resizeWidth && resizeHeight) {
                width = sWidth()
                height = sHeight()
            } else if (resizeHeight) {
                height = (sHeight().toDouble() / sWidth().toDouble() * width).toInt()
            } else if (resizeWidth) {
                width = (sWidth().toDouble() / sHeight().toDouble() * height).toInt()
            }
        }
        width = max(width, suggestedMinimumWidth)
        height = max(height, suggestedMinimumHeight)
        setMeasuredDimension(
            /* measuredWidth = */ width,
            /* measuredHeight = */ height
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (anim?.interruptible == false) {
            parent?.requestDisallowInterceptTouchEvent(true)
            return false
        } else {
            anim = null
        }

        if (vTranslate == null) {
            singleDetector?.onTouchEvent(event)
            return true
        }

        detector?.onTouchEvent(event)
        if (vTranslateStart == null) {
            vTranslateStart = PointF(0f, 0f)
        }

        if (vTranslateBefore == null) {
            vTranslateBefore = PointF(0f, 0f)
        }

        if (sCenterStart == null) {
            sCenterStart = PointF(0f, 0f)
        }

        if (vCenterStart == null) {
            vCenterStart = PointF(0f, 0f)
        }

        if (vCenterStartNow == null) {
            vCenterStartNow = PointF(0f, 0f)
        }

        vTranslate?.let {
            vTranslateBefore?.set(it)
        }
        return onTouchEventInternal(event) || super.onTouchEvent(event)
    }

    private fun onTouchEventInternal(event: MotionEvent): Boolean {
        val touchCount = event.pointerCount
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_1_DOWN, MotionEvent.ACTION_POINTER_2_DOWN -> {
                anim = null
                parent?.requestDisallowInterceptTouchEvent(true)
                maxTouchCount = max(maxTouchCount, touchCount)
                if (touchCount >= 2) {
                    scaleStart = scale
                    vDistStart =
                        distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                    vTranslateStart?.set(vTranslate!!.x, vTranslate!!.y)
                    vCenterStart?.set(
                        (event.getX(0) + event.getX(1)) / 2,
                        (event.getY(0) + event.getY(1)) / 2
                    )
                    viewToSourceCoord(vCenterStart!!, sCenterStart!!)

                    if (rotationEnabled) {
                        lastAngle = atan2(
                            (event.getY(0) - event.getY(1)).toDouble(),
                            (event.getX(0) - event.getX(1)).toDouble()
                        ).toFloat()
                    }
                } else if (!isQuickScaling) {
                    vTranslateStart?.set(vTranslate!!.x, vTranslate!!.y)
                    vCenterStart?.set(event.x, event.y)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                var consumed = false
                if (maxTouchCount > 0) {
                    if (touchCount >= 2) {
                        if (rotationEnabled) {
                            var angle = Math.atan2(
                                (event.getY(0) - event.getY(1)).toDouble(),
                                (event.getX(0) - event.getX(1)).toDouble()
                            ).toFloat()
                            if (Math.abs(lastAngle - angle.toDouble()) > FIFTEEN_DEGREES) {
                                if (lastAngle - angle > 0) {
                                    angle += FIFTEEN_DEGREES.toFloat()
                                } else {
                                    angle -= FIFTEEN_DEGREES.toFloat()
                                }
                                setRotationInternal(imageRotation + angle - lastAngle)
                                lastAngle = angle
                                consumed = true
                            }
                        }

                        val vDistEnd =
                            distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                        val vCenterEndX = (event.getX(0) + event.getX(1)) / 2
                        val vCenterEndY = (event.getY(0) + event.getY(1)) / 2
                        if (distance(
                                x0 = vCenterStart!!.x,
                                x1 = vCenterEndX,
                                y0 = vCenterStart!!.y,
                                y1 = vCenterEndY
                            ) > 5 || abs(vDistEnd - vDistStart) > 5 || isPanning
                        ) {
                            didZoomInGesture = true
                            isZooming = true
                            isPanning = true
                            consumed = true

                            val previousScale = scale.toDouble()
                            scale = min(maxScale, vDistEnd / vDistStart * scaleStart)

                            sourceToViewCoord(sCenterStart!!, vCenterStartNow!!)

                            val dx = vCenterEndX - vCenterStartNow!!.x
                            val dy = vCenterEndY - vCenterStartNow!!.y

                            val dxR = (dx * cos - dy * -sin).toFloat()
                            val dyR = (dx * -sin + dy * cos).toFloat()

                            vTranslate!!.x += dxR
                            vTranslate!!.y += dyR

                            if (previousScale * sHeight() < height && scale * sHeight() >= height || previousScale * sWidth() < width && scale * sWidth() >= width) {
                                vCenterStart?.set(vCenterEndX, vCenterEndY)
                                vTranslate?.let {
                                    vTranslateStart?.set(it)
                                }
                                scaleStart = scale
                                vDistStart = vDistEnd
                            }

                            refreshRequiredTiles(eagerLoadingEnabled)
                        }
                    } else if (isQuickScaling) {
                        var dist =
                            abs(quickScaleVStart!!.y - event.y) * 2 + quickScaleThreshold

                        if (quickScaleLastDistance == -1f) {
                            quickScaleLastDistance = dist
                        }

                        val isUpwards = event.y > quickScaleVLastPoint!!.y
                        quickScaleVLastPoint!!.set(0f, event.y)

                        val spanDiff = abs(1 - dist / quickScaleLastDistance) * 0.5f
                        if (spanDiff > 0.03f || quickScaleMoved) {
                            quickScaleMoved = true

                            var multiplier = 1f
                            if (quickScaleLastDistance > 0) {
                                multiplier = if (isUpwards) 1 + spanDiff else 1 - spanDiff
                            }

                            val previousScale = scale.toDouble()
                            scale = min(maxScale, scale * multiplier)

                            val vLeftStart = vCenterStart!!.x - vTranslateStart!!.x
                            val vTopStart = vCenterStart!!.y - vTranslateStart!!.y
                            val vLeftNow = vLeftStart * (scale / scaleStart)
                            val vTopNow = vTopStart * (scale / scaleStart)
                            vTranslate?.x = vCenterStart!!.x - vLeftNow
                            vTranslate?.y = vCenterStart!!.y - vTopNow
                            if (previousScale * sHeight() < height && scale * sHeight() >= height || previousScale * sWidth() < width && scale * sWidth() >= width) {


                                quickScaleSCenter?.let { p ->
                                    sourceToViewCoord(p)?.let {
                                        vCenterStart?.set(it)
                                    }
                                }

                                vTranslate?.let {
                                    vTranslateStart?.set(it)
                                }
                                scaleStart = scale
                                dist = 0f
                            }
                        }

                        quickScaleLastDistance = dist

                        refreshRequiredTiles(eagerLoadingEnabled)
                        consumed = true
                    } else if (!isZooming) {
                        val dx = event.x - vCenterStart!!.x
                        val dy = event.y - vCenterStart!!.y
                        val dxA = abs(dx)
                        val dyA = abs(dy)

                        val offset = density * 5
                        if (dxA > offset || dyA > offset || isPanning) {
                            consumed = true
                            val dxR = (dx * cos - dy * -sin).toFloat()
                            val dyR = (dx * -sin + dy * cos).toFloat()

                            vTranslate?.x = vTranslateStart!!.x + dxR
                            vTranslate?.y = vTranslateStart!!.y + dyR

                            val lastX = vTranslate!!.x
                            val lastY = vTranslate!!.y
                            if (!didZoomInGesture && scale >= getFullScale()) {
                                fitToBounds()
                            }

                            val degrees = Math.toDegrees(imageRotation.toDouble())
                            val rightAngle = getClosestRightAngle(degrees)
                            val atXEdge =
                                if (rightAngle == 90.0 || rightAngle == 270.0) lastY != vTranslate!!.y else lastX != vTranslate!!.x
                            val atYEdge =
                                if (rightAngle == 90.0 || rightAngle == 270.0) lastX != vTranslate!!.x else lastY != vTranslate!!.y
                            val edgeXSwipe = atXEdge && dxA > dyA && !isPanning
                            val edgeYSwipe = atYEdge && dyA > dxA && !isPanning
                            if (!edgeXSwipe && !edgeYSwipe && (!atXEdge || !atYEdge || isPanning)) {
                                isPanning = true
                            } else if ((dxA > offset && atXEdge && dxA > dyA) || (dyA > offset && atYEdge && dyA > dxA)) {
                                maxTouchCount = 0
                                parent?.requestDisallowInterceptTouchEvent(false)
                            }

                            refreshRequiredTiles(eagerLoadingEnabled)
                        }
                    }
                }

                if (consumed) {
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_POINTER_2_UP -> {
                if (isQuickScaling) {
                    isQuickScaling = false
                    if (quickScaleMoved) {
                        animateToBounds()
                    } else {
                        doubleTapZoom(quickScaleSCenter)
                    }
                }

                if (touchCount == 1) {
                    if (didZoomInGesture) {
                        animateToBounds()
                    }
                }

                didZoomInGesture = false

                if (maxTouchCount > 0 && (isZooming || isPanning)) {
                    if (touchCount == 2) {
                        animateToBounds()
                    }

                    if (touchCount < 3) {
                        isZooming = false
                    }

                    if (touchCount < 2) {
                        maxTouchCount = 0
                    }

                    isPanning = false
                    refreshRequiredTiles(true)
                    return true
                }

                if (touchCount == 1) {
                    isZooming = false
                    isPanning = false
                    maxTouchCount = 0
                }
                return true
            }
        }
        return false
    }

    private fun getClosestRightAngle(degrees: Double) = (degrees / 90f).roundToInt() * 90.0

    private fun doubleTapZoom(sCenter: PointF?) {
        val doubleTapZoomScale = min(maxScale, doubleTapZoomScale)
        val zoomIn = scale <= doubleTapZoomScale * 0.9 || isZoomedOut()
        if (sWidth == sHeight || !isOneToOneZoomEnabled) {
            val targetScale = if (zoomIn) doubleTapZoomScale else getFullScale()
            AnimationBuilder(sCenter = sCenter!!, scale = targetScale).start()
        } else {
            val targetScale = if (zoomIn && scale != 1f) doubleTapZoomScale else getFullScale()
            if (scale != 1f) {
                if (zoomIn) {
                    AnimationBuilder(sCenter = sCenter!!, scale = targetScale).start()
                } else {
                    AnimationBuilder(sCenter = sCenter!!, scale = 1f).start()
                }
            } else {
                AnimationBuilder(sCenter = sCenter!!, scale = targetScale).start()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        createPaints()

        if (sWidth == 0 || sHeight == 0 || width == 0 || height == 0) {
            return
        }

        if (tileMap == null && decoder != null) {
            initialiseBaseLayer(getMaxBitmapDimensions(canvas))
        }

        if (!checkReady()) {
            return
        }

        if (anim != null && anim!!.vFocusStart != null) {
            if (vTranslateBefore == null) {
                vTranslateBefore = PointF(0f, 0f)
            }
            vTranslate?.let {
                vTranslateBefore?.set(it)
            }

            var scaleElapsed = System.currentTimeMillis() - anim!!.time
            val finished = scaleElapsed > anim!!.duration
            scaleElapsed = min(scaleElapsed, anim!!.duration)
            scale = ease(
                type = anim!!.easing,
                time = scaleElapsed,
                from = anim!!.scaleStart,
                change = anim!!.scaleEnd - anim!!.scaleStart,
                duration = anim!!.duration,
                finalValue = anim!!.scaleEnd
            )

            val vFocusNowX = ease(
                type = anim!!.easing,
                time = scaleElapsed,
                from = anim!!.vFocusStart!!.x,
                change = anim!!.vFocusEnd!!.x - anim!!.vFocusStart!!.x,
                duration = anim!!.duration,
                finalValue = anim!!.vFocusEnd!!.x
            )
            val vFocusNowY = ease(
                type = anim!!.easing,
                time = scaleElapsed,
                from = anim!!.vFocusStart!!.y,
                change = anim!!.vFocusEnd!!.y - anim!!.vFocusStart!!.y,
                duration = anim!!.duration,
                finalValue = anim!!.vFocusEnd!!.y
            )

            val easeValue = ease(
                type = anim!!.easing,
                time = scaleElapsed,
                from = anim!!.rotationStart,
                change = anim!!.rotationEnd - anim!!.rotationStart,
                duration = anim!!.duration,
                finalValue = anim!!.rotationEnd
            )
            setRotationInternal(easeValue)

            val animVCenterEnd = sourceToViewCoord(anim!!.sCenterEnd!!)
            val dX = animVCenterEnd!!.x - vFocusNowX
            val dY = animVCenterEnd.y - vFocusNowY
            vTranslate!!.x -= (dX * cos + dY * sin).toFloat()
            vTranslate!!.y -= (-dX * sin + dY * cos).toFloat()

            refreshRequiredTiles(finished)
            if (finished) {
                anim = null
                val degrees = Math.toDegrees(imageRotation.toDouble()).roundToInt()
                if (degrees != prevDegrees) {
                    var diff = degrees - prevDegrees
                    if (diff == 270) {
                        diff = -90
                    } else if (diff == -270) {
                        diff = 90
                    }
                    onImageEventListener?.onImageRotation(diff)
                    prevDegrees = degrees
                }
            }
            invalidate()
        }

        if (tileMap != null && getIsBaseLayerReady()) {
            val sampleSize = min(fullImageSampleSize, calculateInSampleSize(scale))
            var hasMissingTiles = false
            for ((key, value) in tileMap!!) {
                if (key == sampleSize) {
                    for (tile in value) {
                        if (tile.visible && (tile.loading || tile.bitmap == null)) {
                            hasMissingTiles = true
                        }
                    }
                }
            }

            for ((key, value) in tileMap!!) {
                if (key == sampleSize || hasMissingTiles) {
                    for (tile in value) {
                        sourceToViewRect(tile.sRect!!, tile.vRect!!)
                        if (!tile.loading && tile.bitmap != null) {
                            if (objectMatrix == null) {
                                objectMatrix = Matrix()
                            }

                            objectMatrix!!.reset()
                            setMatrixArray(
                                array = srcArray,
                                f0 = 0f,
                                f1 = 0f,
                                f2 = tile.bitmap!!.width.toFloat(),
                                f3 = 0f,
                                f4 = tile.bitmap!!.width.toFloat(),
                                f5 = tile.bitmap!!.height.toFloat(),
                                f6 = 0f,
                                f7 = tile.bitmap!!.height.toFloat()
                            )
                            when (getRequiredRotation()) {
                                ORIENTATION_0 -> setMatrixArray(
                                    dstArray,
                                    tile.vRect!!.left.toFloat(),
                                    tile.vRect!!.top.toFloat(),
                                    tile.vRect!!.right.toFloat(),
                                    tile.vRect!!.top.toFloat(),
                                    tile.vRect!!.right.toFloat(),
                                    tile.vRect!!.bottom.toFloat(),
                                    tile.vRect!!.left.toFloat(),
                                    tile.vRect!!.bottom.toFloat()
                                )

                                ORIENTATION_90 -> setMatrixArray(
                                    dstArray,
                                    tile.vRect!!.right.toFloat(),
                                    tile.vRect!!.top.toFloat(),
                                    tile.vRect!!.right.toFloat(),
                                    tile.vRect!!.bottom.toFloat(),
                                    tile.vRect!!.left.toFloat(),
                                    tile.vRect!!.bottom.toFloat(),
                                    tile.vRect!!.left.toFloat(),
                                    tile.vRect!!.top.toFloat()
                                )

                                ORIENTATION_180 -> setMatrixArray(
                                    dstArray,
                                    tile.vRect!!.right.toFloat(),
                                    tile.vRect!!.bottom.toFloat(),
                                    tile.vRect!!.left.toFloat(),
                                    tile.vRect!!.bottom.toFloat(),
                                    tile.vRect!!.left.toFloat(),
                                    tile.vRect!!.top.toFloat(),
                                    tile.vRect!!.right.toFloat(),
                                    tile.vRect!!.top.toFloat()
                                )

                                ORIENTATION_270 -> setMatrixArray(
                                    dstArray,
                                    tile.vRect!!.left.toFloat(),
                                    tile.vRect!!.bottom.toFloat(),
                                    tile.vRect!!.left.toFloat(),
                                    tile.vRect!!.top.toFloat(),
                                    tile.vRect!!.right.toFloat(),
                                    tile.vRect!!.top.toFloat(),
                                    tile.vRect!!.right.toFloat(),
                                    tile.vRect!!.bottom.toFloat()
                                )
                            }
                            objectMatrix?.setPolyToPoly(srcArray, 0, dstArray, 0, 4)
                            objectMatrix?.postRotate(
                                /* degrees = */ Math.toDegrees(imageRotation.toDouble()).toFloat(),
                                /* px = */ width / 2f,
                                /* py = */ height / 2f
                            )
                            canvas.drawBitmap(tile.bitmap!!, objectMatrix!!, bitmapPaint)
                            if (debug) {
                                canvas.drawRect(tile.vRect!!, debugLinePaint!!)
                            }
                        } else if (tile.loading && debug) {
                            canvas.drawText(
                                /* text = */ "LOADING",
                                /* x = */ (tile.vRect!!.left + px(5)).toFloat(),
                                /* y = */ (tile.vRect!!.top + px(35)).toFloat(),
                                /* paint = */ debugTextPaint!!
                            )
                        }
                        if (tile.visible && debug) {
                            canvas.drawText(
                                /* text = */ "ISS ${tile.sampleSize} RECT ${tile.sRect!!.top}, ${tile.sRect!!.left}, ${tile.sRect!!.bottom}, ${tile.sRect!!.right}",
                                /* x = */ (tile.vRect!!.left + px(5)).toFloat(),
                                /* y = */ (tile.vRect!!.top + px(15)).toFloat(),
                                /* paint = */ debugTextPaint!!
                            )
                        }
                    }
                }
            }
        } else if (bitmap?.isRecycled == false) {
            val xScale = scale
            val yScale = scale

            if (objectMatrix == null) {
                objectMatrix = Matrix()
            }

            objectMatrix?.apply {
                reset()
                postScale(xScale, yScale)
                postRotate(getRequiredRotation().toFloat())
                postTranslate(vTranslate!!.x, vTranslate!!.y)

                when (getRequiredRotation()) {
                    ORIENTATION_90 -> postTranslate(scale * sHeight, 0f)
                    ORIENTATION_180 -> postTranslate(scale * sWidth, scale * sHeight)
                    ORIENTATION_270 -> postTranslate(0f, scale * sWidth)
                }
                postRotate(
                    /* degrees = */ Math.toDegrees(imageRotation.toDouble()).toFloat(),
                    /* px = */ (width / 2).toFloat(),
                    /* py = */ (height / 2).toFloat()
                )
            }

            canvas.drawBitmap(
                /* bitmap = */ bitmap!!,
                /* matrix = */ objectMatrix!!,
                /* paint = */ bitmapPaint
            )
        }

        if (debug) {
            canvas.drawText(
                /* text = */ "Scale: ${
                    String.format(
                        Locale.ENGLISH,
                        "%.2f",
                        scale
                    )
                } (${
                    String.format(
                        Locale.ENGLISH,
                        "%.2f",
                        getFullScale()
                    )
                } - ${String.format(Locale.ENGLISH, "%.2f", maxScale)})",
                /* x = */ px(5).toFloat(),
                /* y = */ px(15).toFloat(),
                /* paint = */ debugTextPaint!!
            )
            canvas.drawText(
                /* text = */ "Translate: ${
                    String.format(
                        Locale.ENGLISH,
                        "%.2f",
                        vTranslate!!.x
                    )
                }:${String.format(Locale.ENGLISH, "%.2f", vTranslate!!.y)}",
                /* x = */ px(5).toFloat(),
                /* y = */ px(30).toFloat(),
                /* paint = */ debugTextPaint!!
            )
            val center = getCenter()

            canvas.drawText(
                /* text = */ "Source center: ${
                    String.format(
                        Locale.ENGLISH,
                        "%.2f",
                        center!!.x
                    )
                }:${String.format(Locale.ENGLISH, "%.2f", center.y)}",
                /* x = */ px(5).toFloat(),
                /* y = */ px(45).toFloat(),
                /* paint = */ debugTextPaint!!
            )
            if (anim != null) {
                val vCenterStart = sourceToViewCoord(anim!!.sCenterStart!!)
                val vCenterEndRequested = sourceToViewCoord(anim!!.sCenterEndRequested!!)
                val vCenterEnd = sourceToViewCoord(anim!!.sCenterEnd!!)

                canvas.drawCircle(
                    /* cx = */ vCenterStart!!.x,
                    /* cy = */ vCenterStart.y,
                    /* radius = */ px(10).toFloat(),
                    /* paint = */ debugLinePaint!!
                )
                debugLinePaint?.color = Color.RED

                canvas.drawCircle(
                    /* cx = */ vCenterEndRequested!!.x,
                    /* cy = */ vCenterEndRequested.y,
                    /* radius = */ px(20).toFloat(),
                    /* paint = */ debugLinePaint!!
                )
                debugLinePaint?.color = Color.BLUE

                canvas.drawCircle(vCenterEnd!!.x, vCenterEnd.y, px(25).toFloat(), debugLinePaint!!)
                debugLinePaint?.color = Color.CYAN
                canvas.drawCircle(
                    /* cx = */ (width / 2).toFloat(),
                    /* cy = */ (height / 2).toFloat(),
                    /* radius = */ px(30).toFloat(),
                    /* paint = */ debugLinePaint!!
                )
            }

            if (vCenterStart != null) {
                debugLinePaint!!.color = Color.RED
                canvas.drawCircle(
                    /* cx = */ vCenterStart!!.x,
                    /* cy = */ vCenterStart!!.y,
                    /* radius = */ px(20).toFloat(),
                    /* paint = */ debugLinePaint!!
                )
            }

            if (quickScaleSCenter != null) {
                debugLinePaint!!.color = Color.BLUE
                canvas.drawCircle(
                    /* cx = */ sourceToViewX(sx = quickScaleSCenter!!.x),
                    /* cy = */ sourceToViewY(quickScaleSCenter!!.y),
                    /* radius = */ px(35).toFloat(),
                    /* paint = */ debugLinePaint!!
                )
            }

            if (quickScaleVStart != null && isQuickScaling) {
                debugLinePaint?.color = Color.CYAN
                canvas.drawCircle(
                    /* cx = */ quickScaleVStart!!.x,
                    /* cy = */ quickScaleVStart!!.y,
                    /* radius = */ px(30).toFloat(),
                    /* paint = */ debugLinePaint!!
                )
            }

            debugLinePaint?.color = Color.MAGENTA
        }
    }

    private fun setMatrixArray(
        array: FloatArray,
        f0: Float,
        f1: Float,
        f2: Float,
        f3: Float,
        f4: Float,
        f5: Float,
        f6: Float,
        f7: Float,
    ) {
        array[0] = f0
        array[1] = f1
        array[2] = f2
        array[3] = f3
        array[4] = f4
        array[5] = f5
        array[6] = f6
        array[7] = f7
    }

    private fun checkReady(): Boolean {
        val ready =
            width > 0 && height > 0 && sWidth > 0 && sHeight > 0 && (bitmap != null || getIsBaseLayerReady())
        if (!isReady && ready) {
            preDraw()
            isReady = true
            onReady()
            onImageEventListener?.onReady()
        }
        return ready
    }

    private fun setRotationInternal(rot: Float) {
        imageRotation = rot % (Math.PI * 2).toFloat()
        if (imageRotation < 0) {
            imageRotation += (Math.PI * 2).toFloat()
        }

        cos = cos(rot.toDouble())
        sin = sin(rot.toDouble())
    }

    private fun checkImageLoaded(): Boolean {
        val imageLoaded = getIsBaseLayerReady()
        if (!isImageLoaded && imageLoaded) {
            preDraw()
            isImageLoaded = true
        }
        return imageLoaded
    }

    private fun createPaints() {
        if (bitmapPaint == null) {
            bitmapPaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }
        }

        if ((debugTextPaint == null || debugLinePaint == null) && debug) {
            debugTextPaint = Paint().apply {
                textSize = px(12).toFloat()
                color = Color.MAGENTA
                style = Style.FILL
            }

            debugLinePaint = Paint().apply {
                color = Color.MAGENTA
                style = Style.STROKE
                strokeWidth = px(1).toFloat()
            }
        }
    }

    @Synchronized
    private fun initialiseBaseLayer(maxTileDimensions: Point) {
        debug("initialiseBaseLayer maxTileDimensions=${maxTileDimensions.x}x${maxTileDimensions.y}")

        satTemp = ScaleTranslateRotate(scale = 0f, vTranslate = PointF(0f, 0f), rotate = 0f)
        fitToBounds(satTemp!!)

        fullImageSampleSize = calculateInSampleSize(satTemp!!.scale)
        if (fullImageSampleSize > 1) {
            fullImageSampleSize /= 2
        }

        if (uri == null) {
            return
        }

        if (fullImageSampleSize == 1 && sWidth() < maxTileDimensions.x && sHeight() < maxTileDimensions.y) {
            decoder?.recycle()
            decoder = null
            val task = BitmapLoadTask(
                view = this,
                context = context,
                decoderFactory = bitmapDecoderFactory,
                source = uri!!
            )
            execute(task)
        } else {
            initialiseTileMap(maxTileDimensions)

            val baseGrid = tileMap!![fullImageSampleSize]
            for (baseTile in baseGrid!!) {
                val task = TileLoadTask(view = this, decoder = decoder!!, tile = baseTile)
                execute(task)
            }
            refreshRequiredTiles(true)
        }
    }

    private fun refreshRequiredTiles(load: Boolean) {
        if (decoder == null || tileMap == null) {
            return
        }

        val sampleSize = min(a = fullImageSampleSize, b = calculateInSampleSize(scale))

        tileMap?.values?.forEach {
            for (tile in it) {
                if (tile.sampleSize < sampleSize || tile.sampleSize > sampleSize && tile.sampleSize != fullImageSampleSize) {
                    tile.visible = false
                    tile.bitmap?.recycle()
                    tile.bitmap = null
                }

                if (tile.sampleSize == sampleSize) {
                    if (tileVisible(tile)) {
                        tile.visible = true
                        if (!tile.loading && tile.bitmap == null && load) {
                            val task = TileLoadTask(view = this, decoder = decoder!!, tile = tile)
                            execute(task)
                        }
                    } else if (tile.sampleSize != fullImageSampleSize) {
                        tile.visible = false
                        tile.bitmap?.recycle()
                        tile.bitmap = null
                    }
                } else if (tile.sampleSize == fullImageSampleSize) {
                    tile.visible = true
                }
            }
        }
    }

    private fun tileVisible(tile: Tile): Boolean {
        if (this.imageRotation == 0f) {
            val sVisLeft = viewToSourceX(0f)
            val sVisRight = viewToSourceX(width.toFloat())
            val sVisTop = viewToSourceY(0f)
            val sVisBottom = viewToSourceY(height.toFloat())
            return !(sVisLeft > tile.sRect!!.right || tile.sRect!!.left > sVisRight || sVisTop > tile.sRect!!.bottom || tile.sRect!!.top > sVisBottom)
        }

        val corners = arrayOf(
            sourceToViewCoord(sx = tile.sRect!!.left.toFloat(), sy = tile.sRect!!.top.toFloat()),
            sourceToViewCoord(sx = tile.sRect!!.right.toFloat(), sy = tile.sRect!!.top.toFloat()),
            sourceToViewCoord(
                sx = tile.sRect!!.right.toFloat(),
                sy = tile.sRect!!.bottom.toFloat()
            ),
            sourceToViewCoord(sx = tile.sRect!!.left.toFloat(), sy = tile.sRect!!.bottom.toFloat())
        )

        for (pointF in corners) {
            if (pointF == null) {
                return false
            }
        }

        val rotation = this.imageRotation % (Math.PI * 2)

        return when {
            rotation < Math.PI / 2 -> !(corners[0]!!.y > height || corners[1]!!.x < 0 || corners[2]!!.y < 0 || corners[3]!!.x > width)
            rotation < Math.PI -> !(corners[3]!!.y > height || corners[0]!!.x < 0 || corners[1]!!.y < 0 || corners[2]!!.x > width)
            rotation < Math.PI * 3 / 2 -> !(corners[2]!!.y > height || corners[3]!!.x < 0 || corners[0]!!.y < 0 || corners[1]!!.x > width)
            else -> !(corners[1]!!.y > height || corners[2]!!.x < 0 || corners[3]!!.y < 0 || corners[0]!!.x > width)
        }
    }

    private fun preDraw() {
        if (width == 0 || height == 0 || sWidth <= 0 || sHeight <= 0) {
            return
        }

        if (sPendingCenter != null && pendingScale != null) {
            scale = pendingScale!!
            if (vTranslate == null) {
                vTranslate = PointF()
            }
            vTranslate?.x = width / 2 - scale * sPendingCenter!!.x
            vTranslate?.y = height / 2 - scale * sPendingCenter!!.y
            sPendingCenter = null
            pendingScale = null
            refreshRequiredTiles(true)
        }

        fitToBounds()
    }

    private fun calculateInSampleSize(scale: Float): Int {
        var newScale = scale
        if (minimumTileDpi > 0) {
            val metrics = resources.displayMetrics
            val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
            newScale *= minimumTileDpi / averageDpi
        }

        val reqWidth = (sWidth() * newScale).toInt()
        val reqHeight = (sHeight() * newScale).toInt()

        var inSampleSize = 1
        if (reqWidth == 0 || reqHeight == 0) {
            return 32
        }

        if (sHeight() > reqHeight || sWidth() > reqWidth) {
            val heightRatio = (sHeight().toFloat() / reqHeight.toFloat()).roundToInt()
            val widthRatio = (sWidth().toFloat() / reqWidth.toFloat()).roundToInt()
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }

        var power = 1
        while (power * 2 < inSampleSize) {
            power *= 2
        }

        if (!context.packageName.startsWith("com.eagle") && !context.packageName.startsWith("com.lisheng")) {
            power *= 4
        }

        return power
    }

    private fun fitToBounds(sat: ScaleTranslateRotate) {
        val vTranslate = sat.vTranslate
        val scale = limitedScale(sat.scale)
        val scaledWidth = scale * sWidth()
        val scaledHeight = scale * sHeight()
        val degrees = Math.toDegrees(imageRotation.toDouble())
        val rightAngle = getClosestRightAngle(degrees)

        // right, bottom
        if (rightAngle == 90.0 || rightAngle == 270.0) {
            vTranslate.x = max(vTranslate.x, width - scaledWidth + (height - width) / 2f)
            vTranslate.y = min(vTranslate.y, (height - width) / 2f)
        } else {
            vTranslate.x = max(vTranslate.x, width - scaledWidth)
            vTranslate.y = max(vTranslate.y, height - scaledHeight)
        }

        // left, top
        val maxTx: Float
        val maxTy: Float
        if (rightAngle == 90.0 || rightAngle == 270.0) {
            vTranslate.x = min(vTranslate.x, -(height - width) / 2f)
            vTranslate.y = max(vTranslate.y, (height - width) / 2f - scaledHeight + width)
        } else {
            maxTx = max(0f, (width - scaledWidth) / 2f)
            maxTy = max(0f, (height - scaledHeight) / 2f)

            vTranslate.x = min(vTranslate.x, maxTx)
            vTranslate.y = min(vTranslate.y, maxTy)
        }

        if (rightAngle == 90.0 || rightAngle == 270.0) {
            if ((scaledWidth >= width || scaledHeight >= width) && scaledWidth < height) {
                vTranslate.x = -(scaledWidth - width) / 2f
            }
        }

        sat.scale = scale
    }

    private fun fitToBounds() {
        var init = false
        if (vTranslate == null) {
            init = true
            vTranslate = PointF(0f, 0f)
        }

        if (satTemp == null) {
            satTemp = ScaleTranslateRotate(0f, PointF(0f, 0f), 0f)
        }

        satTemp?.scale = scale
        vTranslate?.let {
            satTemp?.vTranslate?.set(it)
        }
        satTemp?.rotate = imageRotation
        fitToBounds(satTemp!!)
        scale = satTemp!!.scale
        vTranslate?.set(satTemp!!.vTranslate)
        setRotationInternal(satTemp!!.rotate)

        if (init) {
            vTranslate?.set(
                vTranslateForSCenter(
                    sCenterX = (sWidth() / 2).toFloat(),
                    sCenterY = (sHeight() / 2).toFloat(),
                    scale = scale
                )
            )
        }
    }

    private fun animateToBounds() {
        isPanning = false
        val degrees = Math.toDegrees(imageRotation.toDouble())
        val rightAngle = getClosestRightAngle(degrees)
        val fullScale = getFullScale()

        if (scale >= fullScale) {
            val center = viewToSourceCoord(PointF(width / 2f, height / 2f))!!
            AnimationBuilder(center, rightAngle).start()
        } else {
            val center = PointF(sWidth / 2f, sHeight / 2f)
            AnimationBuilder(sCenter = center, scale = fullScale, degrees = rightAngle).start()
        }
    }

    private fun getFullScale(): Float {
        val degrees = Math.toDegrees(imageRotation.toDouble()) + orientation
        val rightAngle = getClosestRightAngle(degrees)
        return if (rightAngle % 360 == 0.0 || rightAngle == 180.0) {
            min(width / sWidth.toFloat(), height / sHeight.toFloat())
        } else {
            min(width / sHeight.toFloat(), height / sWidth.toFloat())
        }
    }

    private fun getRotatedFullScale(): Float {
        val degrees = Math.toDegrees(imageRotation.toDouble()) + orientation
        val rightAngle = getClosestRightAngle(degrees)
        return if (rightAngle % 360 == 0.0 || rightAngle == 180.0) {
            min(width / sHeight.toFloat(), height / sWidth.toFloat())
        } else {
            min(width / sWidth.toFloat(), height / sHeight.toFloat())
        }
    }

    private fun initialiseTileMap(maxTileDimensions: Point) {
        debug("initialiseTileMap maxTileDimensions=${maxTileDimensions.x}x${maxTileDimensions.y}")
        tileMap = LinkedHashMap()
        var sampleSize = fullImageSampleSize
        var xTiles = 1
        var yTiles = 1

        while (true) {
            var sTileWidth = sWidth() / xTiles
            var sTileHeight = sHeight() / yTiles
            var subTileWidth = sTileWidth / sampleSize
            var subTileHeight = sTileHeight / sampleSize
            while (subTileWidth + xTiles + 1 > maxTileDimensions.x || subTileWidth > width * 1.25 && sampleSize < fullImageSampleSize) {
                xTiles += 1
                sTileWidth = sWidth() / xTiles
                subTileWidth = sTileWidth / sampleSize
            }

            while (subTileHeight + yTiles + 1 > maxTileDimensions.y || subTileHeight > height * 1.25 && sampleSize < fullImageSampleSize) {
                yTiles += 1
                sTileHeight = sHeight() / yTiles
                subTileHeight = sTileHeight / sampleSize
            }

            val tileGrid = ArrayList<Tile>(xTiles * yTiles)
            for (x in 0 until xTiles) {
                for (y in 0 until yTiles) {
                    val tile = Tile()
                    tile.sampleSize = sampleSize
                    tile.visible = sampleSize == fullImageSampleSize
                    tile.sRect = Rect(
                        /* left = */ x * sTileWidth,
                        /* top = */ y * sTileHeight,
                        /* right = */ if (x == xTiles - 1) sWidth() else (x + 1) * sTileWidth,
                        /* bottom = */ if (y == yTiles - 1) sHeight() else (y + 1) * sTileHeight
                    )

                    tile.vRect = Rect(0, 0, 0, 0)
                    tile.fileSRect = Rect(tile.sRect)
                    tileGrid.add(tile)
                }
            }
            tileMap!![sampleSize] = tileGrid
            if (sampleSize == 1) {
                break
            } else {
                sampleSize /= 2
            }
        }
    }

    private class TilesInitTask(
        view: SubsamplingScaleImageView,
        context: Context,
        decoderFactory: DecoderFactory<out ImageRegionDecoder>,
        private val source: Uri,
    ) : AsyncTask<Void, Void, IntArray>() {
        private val viewRef = WeakReference(view)
        private val contextRef = WeakReference(context)
        private val decoderFactoryRef = WeakReference(decoderFactory)
        private var decoder: ImageRegionDecoder? = null
        private var exception: Exception? = null

        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Void): IntArray? {
            try {
                val context = contextRef.get()
                val decoderFactory = decoderFactoryRef.get()
                val view = viewRef.get()
                if (context != null && decoderFactory != null && view != null) {
                    view.debug("TilesInitTask.doInBackground")
                    decoder = decoderFactory.make()
                    val dimensions = decoder!!.init(context, source)
                    val sWidth = dimensions.x
                    val sHeight = dimensions.y
                    val exifOrientation = view.orientation
                    return intArrayOf(sWidth, sHeight, exifOrientation)
                }
            } catch (e: Exception) {
                exception = e
            }

            return null
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(xyo: IntArray?) {
            val view = viewRef.get()
            if (view != null) {
                if (decoder != null && xyo != null && xyo.size == 3) {
                    view.onTilesInited(decoder!!, xyo[0], xyo[1], xyo[2])
                } else if (exception != null) {
                    view.onImageEventListener?.onImageLoadError(exception!!)
                }
            }
        }
    }

    @Synchronized
    private fun onTilesInited(
        decoder: ImageRegionDecoder,
        sWidth: Int,
        sHeight: Int,
        sOrientation: Int,
    ) {
        debug("onTilesInited sWidth=$sWidth, sHeight=$sHeight, sOrientation=$orientation")
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != sWidth || this.sHeight != sHeight)) {
            reset(false)
            bitmap?.recycle()
            bitmap = null
        }
        this.decoder = decoder
        this.sWidth = sWidth
        this.sHeight = sHeight
        this.sOrientation = sOrientation
        checkReady()
        if (!checkImageLoaded() && maxTileWidth > 0 && maxTileWidth != TILE_SIZE_AUTO && maxTileHeight > 0 && maxTileHeight != TILE_SIZE_AUTO && width > 0 && height > 0) {
            initialiseBaseLayer(Point(maxTileWidth, maxTileHeight))
        }

        invalidate()
        requestLayout()
    }

    private class TileLoadTask(
        view: SubsamplingScaleImageView,
        decoder: ImageRegionDecoder,
        tile: Tile,
    ) : AsyncTask<Void, Void, Bitmap>() {
        private val viewRef = WeakReference(view)
        private val decoderRef = WeakReference(decoder)
        private val tileRef = WeakReference(tile)
        private var exception: Exception? = null

        init {
            tile.loading = true
        }

        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Void): Bitmap? {
            try {
                val view = viewRef.get()
                val decoder = decoderRef.get()
                val tile = tileRef.get()
                if (decoder != null && tile != null && view != null && decoder.isReady() && tile.visible) {
                    view.debug("TileLoadTask.doInBackground, tile.sRect=${tile.sRect as Rect}, tile.sampleSize=${tile.sampleSize}")
                    view.decoderLock.readLock().lock()
                    try {
                        if (decoder.isReady()) {
                            view.fileSRect(sRect = tile.sRect, target = tile.fileSRect)
                            return decoder.decodeRegion(
                                sRect = tile.fileSRect!!,
                                sampleSize = tile.sampleSize
                            )
                        } else {
                            tile.loading = false
                        }
                    } finally {
                        view.decoderLock.readLock().unlock()
                    }
                } else {
                    tile?.loading = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode tile $e")
                exception = e
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Failed to decode tile - OutOfMemoryError $e")
                exception = RuntimeException(e)
            }

            return null
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(bitmap: Bitmap?) {
            val subsamplingScaleImageView = viewRef.get()
            val tile = tileRef.get()
            if (subsamplingScaleImageView != null && tile != null) {
                if (bitmap != null) {
                    tile.bitmap = bitmap
                    tile.loading = false
                    subsamplingScaleImageView.onTileLoaded()
                }
            }
        }
    }

    @Synchronized
    private fun onTileLoaded() {
        debug("onTileLoaded")
        checkReady()
        checkImageLoaded()
        if (getIsBaseLayerReady()) {
            bitmap?.recycle()
            bitmap = null
        }
        invalidate()
    }

    private class BitmapLoadTask(
        view: SubsamplingScaleImageView,
        context: Context,
        decoderFactory: DecoderFactory<out ImageDecoder>,
        private val source: Uri,
    ) : AsyncTask<Void, Void, Int>() {
        private val viewRef = WeakReference(view)
        private val contextRef = WeakReference(context)
        private val decoderFactoryRef = WeakReference(decoderFactory)
        private var bitmap: Bitmap? = null
        private var exception: Exception? = null

        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Void): Int? {
            try {
                val context = contextRef.get()
                val decoderFactory = decoderFactoryRef.get()
                val view = viewRef.get()

                if (context != null && decoderFactory != null && view != null) {
                    view.debug("BitmapLoadTask.doInBackground")
                    bitmap = decoderFactory.make().decode(context, source)
                    return view.orientation
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bitmap", e)
                exception = e
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Failed to load bitmap - OutOfMemoryError $e")
                exception = RuntimeException(e)
            }

            return null
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(orientation: Int?) {
            val subsamplingScaleImageView = viewRef.get()
            if (bitmap != null && orientation != null) {
                subsamplingScaleImageView?.onImageLoaded(bitmap, orientation)
            } else if (exception != null) {
                subsamplingScaleImageView?.onImageEventListener?.onImageLoadError(exception!!)
            }
        }
    }

    @Synchronized
    private fun onImageLoaded(bitmap: Bitmap?, sOrientation: Int) {
        debug("onImageLoaded")
        if (sWidth > 0 && sHeight > 0 && (sWidth != bitmap!!.width || sHeight != bitmap.height)) {
            reset(false)
        }

        this.bitmap?.recycle()
        this.bitmap = bitmap
        sWidth = bitmap!!.width
        sHeight = bitmap.height
        this.sOrientation = sOrientation
        val ready = checkReady()
        val imageLoaded = checkImageLoaded()
        if (ready || imageLoaded) {
            invalidate()
            requestLayout()
        }
    }

    private fun execute(asyncTask: AsyncTask<Void, Void, *>) {
        asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    fun setMaxTileSize(maxPixels: Int) {
        maxTileWidth = maxPixels
        maxTileHeight = maxPixels
    }

    fun setMaxTileSize(maxPixelsX: Int, maxPixelsY: Int) {
        maxTileWidth = maxPixelsX
        maxTileHeight = maxPixelsY
    }

    private fun getMaxBitmapDimensions(canvas: Canvas) = Point(
        min(canvas.maximumBitmapWidth, maxTileWidth),
        min(canvas.maximumBitmapHeight, maxTileHeight)
    )

    private fun sWidth(): Int {
        val rotation = getRequiredRotation()
        return if (rotation == 90 || rotation == 270) {
            sHeight
        } else {
            sWidth
        }
    }

    private fun sHeight(): Int {
        val rotation = getRequiredRotation()
        return if (rotation == 90 || rotation == 270) {
            sWidth
        } else {
            sHeight
        }
    }

    private fun fileSRect(sRect: Rect?, target: Rect?) {
        when (getRequiredRotation()) {
            0 -> {
                if (sRect != null) {
                    target?.set(sRect)
                }
            }

            90 -> target?.set(
                /* left = */ sRect!!.top,
                /* top = */ sHeight - sRect.right,
                /* right = */ sRect.bottom,
                /* bottom = */ sHeight - sRect.left
            )

            180 -> target?.set(
                /* left = */ sWidth - sRect!!.right,
                /* top = */ sHeight - sRect.bottom,
                /* right = */ sWidth - sRect.left,
                /* bottom = */ sHeight - sRect.top
            )

            else -> target?.set(
                /* left = */ sWidth - sRect!!.bottom,
                /* top = */ sRect.left,
                /* right = */ sWidth - sRect.top,
                /* bottom = */ sRect.right
            )
        }
    }

    private fun distance(x0: Float, x1: Float, y0: Float, y1: Float): Float {
        val x = x0 - x1
        val y = y0 - y1
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    fun recycle() {
        reset(true)
        bitmapPaint = null
        debugTextPaint = null
        debugLinePaint = null
    }

    private fun viewToSourceX(vx: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            (vx - vTranslate!!.x) / scale
        }
    }

    private fun viewToSourceY(vy: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            (vy - vTranslate!!.y) / scale
        }
    }

    private fun viewToSourceCoord(vxy: PointF, sTarget: PointF) = viewToSourceCoord(
        vx = vxy.x,
        vy = vxy.y,
        sTarget = sTarget
    )

    fun viewToSourceCoord(vxy: PointF) = viewToSourceCoord(vxy.x, vxy.y, PointF())

    private fun viewToSourceCoord(vx: Float, vy: Float, sTarget: PointF = PointF()): PointF? {
        if (vTranslate == null) {
            return null
        }

        var sXPreRotate = viewToSourceX(vx)
        var sYPreRotate = viewToSourceY(vy)

        if (imageRotation == 0f) {
            sTarget.set(/* x = */ sXPreRotate, /* y = */ sYPreRotate)
        } else {
            val sourceVCenterX = viewToSourceX((width / 2).toFloat())
            val sourceVCenterY = viewToSourceY((height / 2).toFloat())
            sXPreRotate -= sourceVCenterX
            sYPreRotate -= sourceVCenterY
            sTarget.x = (sXPreRotate * cos + sYPreRotate * sin).toFloat() + sourceVCenterX
            sTarget.y = (-sXPreRotate * sin + sYPreRotate * cos).toFloat() + sourceVCenterY
        }

        return sTarget
    }

    private fun sourceToViewX(sx: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            sx * scale + vTranslate!!.x
        }
    }

    private fun sourceToViewY(sy: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            sy * scale + vTranslate!!.y
        }
    }

    private fun sourceToViewCoord(sxy: PointF, vTarget: PointF) = sourceToViewCoord(
        sx = sxy.x,
        sy = sxy.y,
        vTarget = vTarget
    )

    fun sourceToViewCoord(sxy: PointF) = sourceToViewCoord(sxy.x, sxy.y, PointF())

    private fun sourceToViewCoord(sx: Float, sy: Float, vTarget: PointF = PointF()): PointF? {
        if (vTranslate == null) {
            return null
        }

        var xPreRotate = sourceToViewX(sx)
        var yPreRotate = sourceToViewY(sy)

        if (imageRotation == 0f) {
            vTarget.set(xPreRotate, yPreRotate)
        } else {
            val vCenterX = (width / 2).toFloat()
            val vCenterY = (height / 2).toFloat()
            xPreRotate -= vCenterX
            yPreRotate -= vCenterY
            vTarget.x = (xPreRotate * cos - yPreRotate * sin).toFloat() + vCenterX
            vTarget.y = (xPreRotate * sin + yPreRotate * cos).toFloat() + vCenterY
        }

        return vTarget
    }

    private fun sourceToViewRect(sRect: Rect, vTarget: Rect) {
        vTarget.set(
            /* left = */ sourceToViewX(sx = sRect.left.toFloat()).toInt(),
            /* top = */ sourceToViewY(sRect.top.toFloat()).toInt(),
            /* right = */ sourceToViewX(sRect.right.toFloat()).toInt(),
            /* bottom = */ sourceToViewY(sRect.bottom.toFloat()).toInt()
        )
    }

    private fun vTranslateForSCenter(sCenterX: Float, sCenterY: Float, scale: Float): PointF {
        val vxCenter = width / 2
        val vyCenter = height / 2
        if (satTemp == null) {
            satTemp = ScaleTranslateRotate(scale = 0f, vTranslate = PointF(0f, 0f), rotate = 0f)
        }

        satTemp?.scale = scale
        satTemp?.vTranslate?.set(vxCenter - sCenterX * scale, vyCenter - sCenterY * scale)
        fitToBounds(satTemp!!)
        return satTemp!!.vTranslate
    }

    private fun limitedSCenter(
        sCenterX: Float,
        sCenterY: Float,
        scale: Float,
        sTarget: PointF,
    ): PointF {
        val vTranslate = vTranslateForSCenter(
            sCenterX = sCenterX,
            sCenterY = sCenterY,
            scale = scale
        )
        val vxCenter = width / 2
        val vyCenter = height / 2
        val sx = (vxCenter - vTranslate.x) / scale
        val sy = (vyCenter - vTranslate.y) / scale
        sTarget.set(sx, sy)
        return sTarget
    }

    private fun limitedScale(targetScale: Float): Float {
        var newTargetScale = targetScale
        newTargetScale = max(getFullScale(), newTargetScale)
        newTargetScale = min(maxScale, newTargetScale)
        return newTargetScale
    }

    private fun ease(
        type: Int,
        time: Long,
        from: Float,
        change: Float,
        duration: Long,
        finalValue: Float,
    ): Float {
        return if (time == duration) {
            finalValue
        } else {
            when (type) {
                EASE_OUT_QUAD -> easeOutQuad(
                    time = time,
                    from = from,
                    change = change,
                    duration = duration
                )

                else -> easeInOutQuad(
                    time = time,
                    from = from,
                    change = change,
                    duration = duration
                )
            }
        }
    }

    private fun easeOutQuad(time: Long, from: Float, change: Float, duration: Long): Float {
        val progress = time.toFloat() / duration.toFloat()
        return -change * progress * (progress - 2) + from
    }

    private fun easeInOutQuad(time: Long, from: Float, change: Float, duration: Long): Float {
        var timeF = time / (duration / 2f)
        return if (timeF < 1) {
            change / 2f * timeF * timeF + from
        } else {
            timeF--
            -change / 2f * (timeF * (timeF - 2) - 1) + from
        }
    }

    private fun debug(message: String) {
        if (debug) {
            Log.d(TAG, message)
        }
    }

    private fun px(px: Int) = (density * px).toInt()

    private fun setMinimumDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        maxScale = averageDpi / dpi
    }

    fun setMinimumTileDpi(minimumTileDpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        this.minimumTileDpi = min(averageDpi, minimumTileDpi.toFloat()).toInt()
        if (isReady) {
            reset(false)
            invalidate()
        }
    }

    private fun onReady() {}

    private fun setDoubleTapZoomDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        doubleTapZoomScale = averageDpi / dpi
    }

    fun isZoomedOut() = scale == getFullScale()

    fun rotateBy(degrees: Int) {
        if (anim != null) {
            return
        }

        val oldDegrees = Math.toDegrees(imageRotation.toDouble())
        val rightAngle = getClosestRightAngle(oldDegrees)
        val newDegrees = ((rightAngle + degrees).toInt())
        val center = PointF(sWidth() / 2f, sHeight() / 2f)
        val scale =
            if (degrees == -90 || degrees == 90 || degrees == 270) getRotatedFullScale() else scale
        AnimationBuilder(sCenter = center, scale = scale, degrees = newDegrees.toDouble()).start(
            true
        )
    }

    inner class AnimationBuilder {
        private val targetScale: Float
        private var targetSCenter: PointF?
        private var targetRotation = imageRotation
        private val duration = ANIMATION_DURATION
        var easing = EASE_IN_OUT_QUAD
        var interruptible = false

        constructor(sCenter: PointF) {
            targetScale = scale
            targetSCenter = sCenter
        }

        constructor(sCenter: PointF, scale: Float) {
            targetScale = scale
            targetSCenter = sCenter
        }

        constructor(sCenter: PointF, degrees: Double) {
            targetScale = scale
            targetSCenter = sCenter
            targetRotation = Math.toRadians(degrees).toFloat()
        }

        constructor(sCenter: PointF, scale: Float, degrees: Double) {
            targetScale = scale
            targetSCenter = sCenter
            targetRotation = Math.toRadians(degrees).toFloat()
        }

        fun start(skipCenterLimiting: Boolean = false) {
            val vxCenter = width / 2
            val vyCenter = height / 2

            if (!skipCenterLimiting) {
                targetSCenter =
                    limitedSCenter(targetSCenter!!.x, targetSCenter!!.y, targetScale, PointF())
            }

            anim = Anim().apply {
                scaleStart = scale
                scaleEnd = targetScale
                rotationStart = imageRotation
                rotationEnd = targetRotation
                time = System.currentTimeMillis()
                sCenterEndRequested = targetSCenter
                sCenterStart = getCenter()
                sCenterEnd = targetSCenter
                vFocusStart = sourceToViewCoord(targetSCenter!!)
                vFocusEnd = PointF(
                    /* x = */ vxCenter.toFloat(),
                    /* y = */ vyCenter.toFloat()
                )
                time = System.currentTimeMillis()
            }

            anim?.duration = duration
            anim?.interruptible = interruptible
            anim?.easing = easing

            try {
                invalidate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    data class ScaleTranslateRotate(var scale: Float, var vTranslate: PointF, var rotate: Float)

    class Tile {
        var sRect: Rect? = null
        var sampleSize = 0
        var bitmap: Bitmap? = null
        var loading = false
        var visible = false
        var vRect: Rect? = null
        var fileSRect: Rect? = null
    }

    class Anim {
        var scaleStart = 0f
        var scaleEnd = 0f
        var rotationStart = 0f
        var rotationEnd = 0f
        var sCenterStart: PointF? = null
        var sCenterEnd: PointF? = null
        var sCenterEndRequested: PointF? = null
        var vFocusStart: PointF? = null
        var vFocusEnd: PointF? = null
        var duration = ANIMATION_DURATION
        var interruptible = true
        var easing = EASE_IN_OUT_QUAD
        var time = System.currentTimeMillis()
    }

    interface OnImageEventListener {
        fun onReady()
        fun onImageLoadError(e: Exception)
        fun onImageRotation(degrees: Int)
    }
}
