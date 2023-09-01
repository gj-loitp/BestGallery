package com.roy.gallery.pro.jobs

import android.annotation.TargetApi
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.roy.gallery.pro.extensions.addPathToDB
import com.roy.commons.ext.getStringValue

// based on https://developer.android.com/reference/android/app/job/JobInfo.Builder.html#addTriggerContentUri(android.app.job.JobInfo.TriggerContentUri)
@TargetApi(Build.VERSION_CODES.N)
class NewPhotoFetcher : JobService() {
    companion object {
        const val PHOTO_VIDEO_CONTENT_JOB = 1
        private val MEDIA_URI = Uri.parse("content://${MediaStore.AUTHORITY}/")
        private val PHOTO_PATH_SEGMENTS = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.pathSegments
        private val VIDEO_PATH_SEGMENTS = MediaStore.Video.Media.EXTERNAL_CONTENT_URI.pathSegments
    }

    private val mHandler = Handler(Looper.getMainLooper())
    private val mWorker = Runnable {
        try {
            scheduleJob(this@NewPhotoFetcher)
            jobFinished(mRunningParams, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var mRunningParams: JobParameters? = null

    fun scheduleJob(context: Context) {
        val componentName = ComponentName(context, NewPhotoFetcher::class.java)
        val photoUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        JobInfo.Builder(PHOTO_VIDEO_CONTENT_JOB, componentName).apply {
            addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    /* uri = */ photoUri,
                    /* flags = */ JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                )
            )
            addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    /* uri = */ videoUri,
                    /* flags = */ JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                )
            )
            addTriggerContentUri(JobInfo.TriggerContentUri(MEDIA_URI, 0))
            try {
                context.getSystemService(JobScheduler::class.java).schedule(build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isScheduled(context: Context): Boolean {
        val jobScheduler = context.getSystemService(JobScheduler::class.java)
        val jobs = jobScheduler.allPendingJobs
        return jobs.any { it.id == PHOTO_VIDEO_CONTENT_JOB }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        mRunningParams = params

        if (params.triggeredContentAuthorities != null && params.triggeredContentUris != null) {
            val ids = arrayListOf<String>()
            for (uri in params.triggeredContentUris!!) {
                val path = uri.pathSegments
                if (path != null && (path.size == PHOTO_PATH_SEGMENTS.size + 1 || path.size == VIDEO_PATH_SEGMENTS.size + 1)) {
                    ids.add(path[path.size - 1])
                }
            }

            if (ids.isNotEmpty()) {
                val selection = StringBuilder()
                for (id in ids) {
                    if (selection.isNotEmpty()) {
                        selection.append(" OR ")
                    }
                    selection.append("${MediaStore.Images.ImageColumns._ID} = '$id'")
                }

                var cursor: Cursor? = null
                try {
                    val projection = arrayOf(MediaStore.Images.ImageColumns.DATA)
                    val uris = arrayListOf(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    )
                    uris.forEach {
                        cursor = contentResolver.query(
                            /* uri = */ it,
                            /* projection = */ projection,
                            /* selection = */ selection.toString(),
                            /* selectionArgs = */ null,
                            /* sortOrder = */ null
                        )
                        while (cursor!!.moveToNext()) {
                            val path = cursor!!.getStringValue(MediaStore.Images.ImageColumns.DATA)
                            addPathToDB(path)
                        }
                    }
                } catch (ignored: Exception) {
                    ignored.printStackTrace()
                } finally {
                    cursor?.close()
                }
            }
        }

        mHandler.post(mWorker)
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        mHandler.removeCallbacks(mWorker)
        return false
    }
}
