package com.eagle.commons.extensions

import android.graphics.Point
import kotlin.math.roundToLong

fun Point.formatAsResolution() = "$x x $y ${getMPx()}"

fun Point.getMPx(): String {
    val px = x * y / 1000000.toFloat()
    val rounded = (px * 10).roundToLong() / 10.toFloat()
    return "(${rounded}MP)"
}
