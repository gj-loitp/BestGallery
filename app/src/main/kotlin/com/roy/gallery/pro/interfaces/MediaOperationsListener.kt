package com.roy.gallery.pro.interfaces

import com.roy.commons.models.FileDirItem

interface MediaOperationsListener {
    fun refreshItems()

    fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>)

    fun selectedPaths(paths: ArrayList<String>)
}
