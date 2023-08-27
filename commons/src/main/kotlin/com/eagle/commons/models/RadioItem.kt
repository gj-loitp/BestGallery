package com.eagle.commons.models

import androidx.annotation.Keep

@Keep
data class RadioItem(val id: Int, val title: String, val value: Any = id)
