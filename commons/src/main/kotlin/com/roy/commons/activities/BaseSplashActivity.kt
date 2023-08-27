package com.roy.commons.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.roy.commons.R
import com.roy.commons.ext.baseConfig
import com.roy.commons.ext.getSharedTheme
import com.roy.commons.ext.isThankYouInstalled
import com.roy.commons.ext.showSideloadingDialog
import com.roy.commons.helpers.SIDELOADING_FALSE
import com.roy.commons.helpers.SIDELOADING_TRUE
import com.roy.commons.helpers.SIDELOADING_UNCHECKED

abstract class BaseSplashActivity : AppCompatActivity() {
    abstract fun initActivity()

    abstract fun getAppPackageName(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (baseConfig.appSideloadingStatus == SIDELOADING_UNCHECKED) {
            val isSideloaded = isAppSideloaded()
            baseConfig.appSideloadingStatus =
                if (isSideloaded) SIDELOADING_TRUE else SIDELOADING_FALSE
            if (isSideloaded) {
                baseConfig.appId = getAppPackageName()
                showSideloadingDialog()
                return
            }
        } else if (baseConfig.appSideloadingStatus == SIDELOADING_TRUE) {
            showSideloadingDialog()
            return
        }

        if (isThankYouInstalled() && baseConfig.appRunCount == 0) {
            getSharedTheme {
                if (it != null) {
                    baseConfig.apply {
                        wasSharedThemeForced = true
                        isUsingSharedTheme = true
                        wasSharedThemeEverActivated = true

                        textColor = it.textColor
                        backgroundColor = it.backgroundColor
                        primaryColor = it.primaryColor
                        appIconColor = it.appIconColor
                    }
                }
                initActivity()
            }
        } else {
            initActivity()
        }
    }

    private fun isAppSideloaded(): Boolean {
        return try {
//            getDrawable(R.drawable.ic_camera)
            ContextCompat.getDrawable(this, R.drawable.ic_camera)
            false
        } catch (e: Exception) {
            true
        }
    }
}
