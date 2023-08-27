package com.roy.gallery.pro

import android.content.Context
import androidx.multidex.MultiDexApplication
import com.github.ajalt.reprint.core.Reprint
import com.roy.gallery.pro.activities.ActivityLifeCallbacks
import com.roy.commons.ext.checkUseEnglish

class App : MultiDexApplication() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (base != null) {
            mContext = base
        }
    }

    override fun onCreate() {
        super.onCreate()
        mContext = applicationContext

//        Thread {
//            FirebaseApp.initializeApp(this)
//        }.start()


        checkUseEnglish()
        Reprint.initialize(this)
        registerActivityLifecycleCallbacks(ActivityLifeCallbacks())
    }

    companion object {
        lateinit var mContext: Context
    }
}
