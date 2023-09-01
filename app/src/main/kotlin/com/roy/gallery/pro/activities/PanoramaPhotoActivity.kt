package com.roy.gallery.pro.activities

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.google.vr.sdk.widgets.pano.VrPanoramaEventListener
import com.google.vr.sdk.widgets.pano.VrPanoramaView
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.*
import com.roy.gallery.pro.helpers.PATH
import com.roy.commons.ext.beVisible
import com.roy.commons.ext.onGlobalLayout
import com.roy.commons.ext.showErrorToast
import com.roy.commons.ext.toast
import com.roy.commons.helpers.PERMISSION_WRITE_STORAGE
import kotlinx.android.synthetic.main.a_panorama_photo.*

open class PanoramaPhotoActivity : SimpleActivity() {
    private val cardboardDisplayMode = 3

    private var isFullscreen = false
    private var isExploreEnabled = true
    private var isRendering = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.a_panorama_photo)
        supportActionBar?.hide()

        checkNotchSupport()
        setupButtonMargins()

        cardboard.setOnClickListener {
            panoramaView.displayMode = cardboardDisplayMode
        }

        explore.setOnClickListener {
            isExploreEnabled = !isExploreEnabled
            panoramaView.setPureTouchTracking(isExploreEnabled)
            explore.setImageResource(if (isExploreEnabled) R.drawable.ic_explore else R.drawable.ic_explore_off)
        }

        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                checkIntent()
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        panoramaView.resumeRendering()
        isRendering = true
        if (config.blackBackground) {
            updateStatusbarColor(Color.BLACK)
        }

//        window.statusBarColor = resources.getColor(R.color.circle_black_background)
        window.statusBarColor = ContextCompat.getColor(this, R.color.circle_black_background)
    }

    override fun onPause() {
        super.onPause()
        panoramaView.pauseRendering()
        isRendering = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRendering) {
            panoramaView.shutdown()
        }
    }

    private fun checkIntent() {
        val path = intent.getStringExtra(PATH)
        if (path == null) {
            toast(R.string.invalid_image_path)
            finish()
            return
        }

        intent.removeExtra(PATH)

        try {
            val options = VrPanoramaView.Options()
            options.inputType = VrPanoramaView.Options.TYPE_MONO
            Thread {
                val bitmap = getBitmapToLoad(path)
                runOnUiThread {
                    panoramaView.apply {
                        beVisible()
                        loadImageFromBitmap(bitmap, options)
                        setFlingingEnabled(true)
                        setPureTouchTracking(true)

                        // add custom buttons so we can position them and toggle visibility as desired
                        setFullscreenButtonEnabled(false)
                        setInfoButtonEnabled(false)
                        setTransitionViewEnabled(false)
                        setStereoModeButtonEnabled(false)

                        setOnClickListener {
                            handleClick()
                        }

                        setEventListener(object : VrPanoramaEventListener() {
                            override fun onClick() {
                                handleClick()
                            }
                        })
                    }
                }
            }.start()
        } catch (e: Exception) {
            showErrorToast(e)
        }

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            isFullscreen = visibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
            toggleButtonVisibility()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupButtonMargins()
    }

    private fun getBitmapToLoad(path: String): Bitmap? {
        val options = BitmapFactory.Options()
        options.inSampleSize = 1
        var bitmap: Bitmap? = null

        for (i in 0..10) {
            try {
                bitmap = BitmapFactory.decodeFile(path, options)
                break
            } catch (e: OutOfMemoryError) {
                options.inSampleSize *= 2
            }
        }

        return bitmap
    }

    private fun setupButtonMargins() {
        val navBarHeight = navigationBarHeight
        (cardboard.layoutParams as RelativeLayout.LayoutParams).apply {
            bottomMargin = navBarHeight
            rightMargin = navigationBarWidth
        }

        (explore.layoutParams as RelativeLayout.LayoutParams).bottomMargin = navigationBarHeight

        cardboard.onGlobalLayout {
            panoramaGradientBackground.layoutParams.height = navBarHeight + cardboard.height
        }
    }

    private fun toggleButtonVisibility() {
        arrayOf(cardboard, explore, panoramaGradientBackground).forEach {
            it.animate().alpha(if (isFullscreen) 0f else 1f)
            it.isClickable = !isFullscreen
        }
    }

    private fun handleClick() {
        isFullscreen = !isFullscreen
        toggleButtonVisibility()
        if (isFullscreen) {
            hideSystemUI(false)
        } else {
            showSystemUI(false)
        }
    }
}
