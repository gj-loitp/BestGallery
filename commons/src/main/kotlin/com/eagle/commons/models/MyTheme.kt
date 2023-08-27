package com.eagle.commons.models

import androidx.annotation.Keep

@Keep
data class MyTheme(
    val nameId: Int,
    val textColorId: Int,
    val backgroundColorId: Int,
    val primaryColorId: Int,
    val appIconColorId: Int,
)
