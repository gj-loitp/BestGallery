package com.eagle.gallery.pro

import android.content.Context
import androidx.multidex.MultiDexApplication
import com.github.ajalt.reprint.core.Reprint
import com.eagle.commons.extensions.checkUseEnglish
import com.eagle.gallery.pro.activities.ActivityLifeCallbacks
import com.google.firebase.FirebaseApp

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

        Thread {
            FirebaseApp.initializeApp(this)
        }.start()


        checkUseEnglish()
        Reprint.initialize(this)
        registerActivityLifecycleCallbacks(ActivityLifeCallbacks())
    }

    companion object {
        lateinit var mContext: Context
    }
}
