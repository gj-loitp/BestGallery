package com.roy.gallery.pro

import android.content.Context
import androidx.multidex.MultiDexApplication
import com.github.ajalt.reprint.core.Reprint
import com.roy.gallery.pro.activities.ActivityLifeCallbacks
import com.roy.commons.ext.checkUseEnglish

//TODO firebase analytic
//TODO ad applovin
//TODO con file a_setting.xml chua refactor

//done
//rename app
//ic_launcher
//proguard
//leak canary
//rate, more app, share app
//policy
//keystore

class App : MultiDexApplication() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
//        if (base != null) {
//            mContext = base
//        }
    }

    override fun onCreate() {
        super.onCreate()
//        mContext = applicationContext
        checkUseEnglish()
        Reprint.initialize(this)
        registerActivityLifecycleCallbacks(ActivityLifeCallbacks())
    }

//    companion object {
//        lateinit var mContext: Context
//    }
}
