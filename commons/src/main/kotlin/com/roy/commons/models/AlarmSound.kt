package com.roy.commons.models

import androidx.annotation.Keep

@Keep
data class AlarmSound(val id: Int, var title: String, var uri: String)
