package com.roy.gallery.pro.models

import android.graphics.Color
import androidx.annotation.Keep

@Keep
data class PaintOptions(var color: Int = Color.BLACK, var strokeWidth: Float = 5f)
