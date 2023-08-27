package com.eagle.commons.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import com.eagle.commons.R
import com.eagle.commons.ext.*
import com.eagle.commons.helpers.*
import com.eagle.commons.models.License
import kotlinx.android.synthetic.main.a_license.*
import kotlinx.android.synthetic.main.v_license_faq_item.view.*
import java.util.*

class LicenseActivity : BaseSimpleActivity() {
    override fun getAppIconIDs() = intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList()

    override fun getAppLauncherName() = intent.getStringExtra(APP_LAUNCHER_NAME) ?: ""

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.a_license)

        val linkColor = getAdjustedPrimaryColor()
        val textColor = baseConfig.textColor
        updateTextColors(licensesHolder)

        val inflater = LayoutInflater.from(this)
        val licenses = initLicenses()
        val licenseMask = intent.getIntExtra(APP_LICENSES, 0) or LICENSE_KOTLIN
        licenses.filter { licenseMask and it.id != 0 }.forEach {
            val license = it
            inflater.inflate(R.layout.v_license_faq_item, null).apply {
                licenseFaqTitle.apply {
                    text = getString(license.titleId)
                    underlineText()
                    setTextColor(linkColor)
                    setOnClickListener {
                        launchViewIntent(license.urlId)
                    }
                }

                licenseFaqText.text = getString(license.textId)
                licenseFaqText.setTextColor(textColor)
                licensesHolder.addView(this)
            }
        }
    }

    private fun initLicenses() = arrayOf(
        License(
            id = LICENSE_KOTLIN,
            titleId = R.string.kotlin_title,
            textId = R.string.kotlin_text,
            urlId = R.string.kotlin_url
        ),
        License(
            id = LICENSE_SUBSAMPLING,
            titleId = R.string.subsampling_title,
            textId = R.string.subsampling_text,
            urlId = R.string.subsampling_url
        ),
        License(
            id = LICENSE_GLIDE,
            titleId = R.string.glide_title,
            textId = R.string.glide_text,
            urlId = R.string.glide_url
        ),
        License(
            id = LICENSE_CROPPER,
            titleId = R.string.cropper_title,
            textId = R.string.cropper_text,
            urlId = R.string.cropper_url
        ),
        License(
            id = LICENSE_RTL,
            titleId = R.string.rtl_viewpager_title,
            textId = R.string.rtl_viewpager_text,
            urlId = R.string.rtl_viewpager_url
        ),
        License(
            id = LICENSE_JODA,
            titleId = R.string.joda_title,
            textId = R.string.joda_text,
            urlId = R.string.joda_url
        ),
        License(
            id = LICENSE_STETHO,
            titleId = R.string.stetho_title,
            textId = R.string.stetho_text,
            urlId = R.string.stetho_url
        ),
        License(
            id = LICENSE_OTTO,
            titleId = R.string.otto_title,
            textId = R.string.otto_text,
            urlId = R.string.otto_url
        ),
        License(
            id = LICENSE_PHOTOVIEW,
            titleId = R.string.photoview_title,
            textId = R.string.photoview_text,
            urlId = R.string.photoview_url
        ),
        License(
            id = LICENSE_PICASSO,
            titleId = R.string.picasso_title,
            textId = R.string.picasso_text,
            urlId = R.string.picasso_url
        ),
        License(
            id = LICENSE_PATTERN,
            titleId = R.string.pattern_title,
            textId = R.string.pattern_text,
            urlId = R.string.pattern_url
        ),
        License(
            id = LICENSE_REPRINT,
            titleId = R.string.reprint_title,
            textId = R.string.reprint_text,
            urlId = R.string.reprint_url
        ),
        License(
            id = LICENSE_GIF_DRAWABLE,
            titleId = R.string.gif_drawable_title,
            textId = R.string.gif_drawable_text,
            urlId = R.string.gif_drawable_url
        ),
        License(
            id = LICENSE_AUTOFITTEXTVIEW,
            titleId = R.string.autofittextview_title,
            textId = R.string.autofittextview_text,
            urlId = R.string.autofittextview_url
        ),
        License(
            id = LICENSE_ROBOLECTRIC,
            titleId = R.string.robolectric_title,
            textId = R.string.robolectric_text,
            urlId = R.string.robolectric_url
        ),
        License(
            id = LICENSE_ESPRESSO,
            titleId = R.string.espresso_title,
            textId = R.string.espresso_text,
            urlId = R.string.espresso_url
        ),
        License(
            id = LICENSE_GSON,
            titleId = R.string.gson_title,
            textId = R.string.gson_text,
            urlId = R.string.gson_url
        ),
        License(
            id = LICENSE_LEAK_CANARY,
            titleId = R.string.leak_canary_title,
            textId = R.string.leakcanary_text,
            urlId = R.string.leakcanary_url
        ),
        License(
            id = LICENSE_NUMBER_PICKER,
            titleId = R.string.number_picker_title,
            textId = R.string.number_picker_text,
            urlId = R.string.number_picker_url
        ),
        License(
            id = LICENSE_EXOPLAYER,
            titleId = R.string.exoplayer_title,
            textId = R.string.exoplayer_text,
            urlId = R.string.exoplayer_url
        ),
        License(
            id = LICENSE_PANORAMA_VIEW,
            titleId = R.string.panorama_view_title,
            textId = R.string.panorama_view_text,
            urlId = R.string.panorama_view_url
        ),
        License(
            id = LICENSE_SANSELAN,
            titleId = R.string.sanselan_title,
            textId = R.string.sanselan_text,
            urlId = R.string.sanselan_url
        ),
        License(
            id = LICENSE_FILTERS,
            titleId = R.string.filters_title,
            textId = R.string.filters_text,
            urlId = R.string.filters_url
        ),
        License(
            id = LICENSE_GESTURE_VIEWS,
            titleId = R.string.gesture_views_title,
            textId = R.string.gesture_views_text,
            urlId = R.string.gesture_views_url
        )
    )
}
