package com.roy.gallery.pro.models

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "widgets", indices = [(Index(value = ["widget_id"], unique = true))])
data class Widget(
        @PrimaryKey(autoGenerate = true) var id: Int?,
        @ColumnInfo(name = "widget_id") var widgetId: Int,
        @ColumnInfo(name = "folder_path") var folderPath: String,
)
