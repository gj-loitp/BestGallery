package com.roy.gallery.pro.activities

import android.os.Bundle

class VideoActivity : com.roy.gallery.pro.activities.PhotoVideoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        mIsVideo = true
        super.onCreate(savedInstanceState)
    }
}
