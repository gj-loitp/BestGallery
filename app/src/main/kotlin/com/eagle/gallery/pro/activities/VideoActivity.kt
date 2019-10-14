package com.eagle.gallery.pro.activities

import android.os.Bundle

class VideoActivity : com.eagle.gallery.pro.activities.PhotoVideoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        mIsVideo = true
        super.onCreate(savedInstanceState)
    }
}
