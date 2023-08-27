package com.roy.gallery.pro.activities

import android.os.Bundle

class PhotoActivity : com.roy.gallery.pro.activities.PhotoVideoActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        mIsVideo = false
        super.onCreate(savedInstanceState)
    }
}
