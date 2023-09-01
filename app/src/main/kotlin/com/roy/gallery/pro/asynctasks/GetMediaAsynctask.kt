package com.roy.gallery.pro.asynctasks

import android.content.Context
import android.os.AsyncTask
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.extensions.getFavoritePaths
import com.roy.gallery.pro.helpers.*
import com.roy.gallery.pro.models.Medium
import com.roy.gallery.pro.models.ThumbnailItem
import com.roy.commons.helpers.SORT_BY_DATE_TAKEN
import com.roy.commons.helpers.SORT_BY_SIZE
import java.util.*

class GetMediaAsynctask(
    val context: Context,
    val mPath: String,
    val isPickImage: Boolean = false,
    val isPickVideo: Boolean = false,
    val showAll: Boolean,
    val callback: (media: ArrayList<ThumbnailItem>) -> Unit,
) :
    AsyncTask<Void, Void, ArrayList<ThumbnailItem>>() {
    private val mediaFetcher = MediaFetcher(context)

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg params: Void): ArrayList<ThumbnailItem> {
        val pathToUse = if (showAll) SHOW_ALL else mPath
        val getProperDateTaken =
            context.config.getFileSorting(pathToUse) and SORT_BY_DATE_TAKEN != 0 || context.config.getFolderGrouping(
                pathToUse
            ) and GROUP_BY_DATE_TAKEN != 0
        val getProperFileSize = context.config.getFileSorting(pathToUse) and SORT_BY_SIZE != 0
        val favoritePaths = context.getFavoritePaths()
        val getVideoDurations = context.config.showThumbnailVideoDuration
        val media = if (showAll) {
            val foldersToScan =
                mediaFetcher.getFoldersToScan().filter { it != RECYCLE_BIN && it != FAVORITES }
            val media = ArrayList<Medium>()
            foldersToScan.forEach {
                val newMedia = mediaFetcher.getFilesFrom(
                    curPath = it,
                    isPickImage = isPickImage,
                    isPickVideo = isPickVideo,
                    getProperDateTaken = getProperDateTaken,
                    getProperFileSize = getProperFileSize,
                    favoritePaths = favoritePaths,
                    getVideoDurations = getVideoDurations,
                    sortMedia = false
                )
                media.addAll(newMedia)
            }

            mediaFetcher.sortMedia(media, context.config.getFileSorting(SHOW_ALL))
            media
        } else {
            mediaFetcher.getFilesFrom(
                curPath = mPath,
                isPickImage = isPickImage,
                isPickVideo = isPickVideo,
                getProperDateTaken = getProperDateTaken,
                getProperFileSize = getProperFileSize,
                favoritePaths = favoritePaths,
                getVideoDurations = getVideoDurations
            )
        }
        return mediaFetcher.groupMedia(media, pathToUse)
    }

    @Deprecated("Deprecated in Java")
    override fun onPostExecute(media: ArrayList<ThumbnailItem>) {
        super.onPostExecute(media)
        callback(media)
    }

    fun stopFetching() {
        mediaFetcher.shouldStop = true
        cancel(true)
    }
}
