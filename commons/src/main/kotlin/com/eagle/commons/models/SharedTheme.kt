package com.eagle.commons.models

import androidx.annotation.Keep

@Keep
data class SharedTheme(
    val textColor: Int,
    val backgroundColor: Int,
    val primaryColor: Int,
    val appIconColor: Int,
    val lastUpdatedTS: Int = 0,
)
