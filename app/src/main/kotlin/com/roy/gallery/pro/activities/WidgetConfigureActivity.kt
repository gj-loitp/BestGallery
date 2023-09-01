package com.roy.gallery.pro.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.RelativeLayout
import android.widget.RemoteViews
import com.roy.gallery.pro.R
import com.roy.gallery.pro.dialogs.PickDirectoryDialog
import com.roy.gallery.pro.extensions.*
import com.roy.gallery.pro.helpers.MyWidgetProvider
import com.roy.gallery.pro.models.Directory
import com.roy.gallery.pro.models.Widget
import com.roy.commons.dlg.ColorPickerDialog
import com.roy.commons.ext.adjustAlpha
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.onSeekBarChangeListener
import com.roy.commons.ext.setBackgroundColor
import com.roy.commons.ext.setFillWithStroke
import com.roy.commons.ext.updateTextColors
import kotlinx.android.synthetic.main.a_widget_config.*

class WidgetConfigureActivity : com.roy.gallery.pro.activities.SimpleActivity() {
    private var mBgAlpha = 0f
    private var mWidgetId = 0
    private var mBgColor = 0
    private var mBgColorWithoutTransparency = 0
    private var mTextColor = 0
    private var mFolderPath = ""
    private var mDirectories = ArrayList<Directory>()

    public override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.a_widget_config)
        initVariables()

        mWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (mWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
        }

        configSave.setOnClickListener { saveConfig() }
        configBgColor.setOnClickListener { pickBackgroundColor() }
        configTextColor.setOnClickListener { pickTextColor() }
        folderPickerValue.setOnClickListener { changeSelectedFolder() }
        configImageHolder.setOnClickListener { changeSelectedFolder() }
        folderPickerShowFolderName.isChecked = config.showWidgetFolderName
        handleFolderNameDisplay()
        folderPickerShowFolderNameHolder.setOnClickListener {
            folderPickerShowFolderName.toggle()
            handleFolderNameDisplay()
        }

        updateTextColors(folderPickerHolder)
        folderPickerHolder.background = ColorDrawable(config.backgroundColor)

        getCachedDirectories(false, false) {
            mDirectories = it
            val path = it.firstOrNull()?.path
            if (path != null) {
                updateFolderImage(path)
            }
        }
    }

    private fun initVariables() {
        mBgColor = config.widgetBgColor
        mBgAlpha = Color.alpha(mBgColor) / 255f

        mBgColorWithoutTransparency = Color.rgb(Color.red(mBgColor), Color.green(mBgColor), Color.blue(mBgColor))
        configBgSeekbar.apply {
            progress = (mBgAlpha * 100).toInt()

            onSeekBarChangeListener {
                mBgAlpha = it / 100f
                updateBackgroundColor()
            }
        }
        updateBackgroundColor()

        mTextColor = config.widgetTextColor
        updateTextColor()
    }

    private fun saveConfig() {
        val views = RemoteViews(packageName, R.layout.v_widget)
        views.setBackgroundColor(R.id.widgetHolder, mBgColor)
        AppWidgetManager.getInstance(this).updateAppWidget(mWidgetId, views)
        config.showWidgetFolderName = folderPickerShowFolderName.isChecked
        val widget = Widget(null, mWidgetId, mFolderPath)
        Thread {
            widgetsDB.insertOrUpdate(widget)
        }.start()

        storeWidgetColors()
        requestWidgetUpdate()

        Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    private fun storeWidgetColors() {
        config.apply {
            widgetBgColor = mBgColor
            widgetTextColor = mTextColor
        }
    }

    private fun requestWidgetUpdate() {
        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, MyWidgetProvider::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(mWidgetId))
            sendBroadcast(this)
        }
    }

    private fun updateBackgroundColor() {
        mBgColor = mBgColorWithoutTransparency.adjustAlpha(mBgAlpha)
        configSave.setBackgroundColor(mBgColor)
        configImageHolder.setBackgroundColor(mBgColor)
        configBgColor.setFillWithStroke(mBgColor, Color.BLACK)
    }

    private fun updateTextColor() {
        configSave.setTextColor(mTextColor)
        configFolderName.setTextColor(mTextColor)
        configTextColor.setFillWithStroke(mTextColor, Color.BLACK)
    }

    private fun pickBackgroundColor() {
        ColorPickerDialog(this, mBgColorWithoutTransparency) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                mBgColorWithoutTransparency = color
                updateBackgroundColor()
            }
        }
    }

    private fun pickTextColor() {
        ColorPickerDialog(this, mTextColor) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                mTextColor = color
                updateTextColor()
            }
        }
    }

    private fun changeSelectedFolder() {
        PickDirectoryDialog(this, "", false) {
            updateFolderImage(it)
        }
    }

    private fun updateFolderImage(folderPath: String) {
        mFolderPath = folderPath
        runOnUiThread {
            folderPickerValue.text = getFolderNameFromPath(folderPath)
            configFolderName.text = getFolderNameFromPath(folderPath)
        }

        Thread {
            val path = directoryDB.getDirectoryThumbnail(folderPath)
            if (path != null) {
                runOnUiThread {
                    loadJpg(path, configImage, config.cropThumbnails)
                }
            }
        }.start()
    }

    private fun handleFolderNameDisplay() {
        val showFolderName = folderPickerShowFolderName.isChecked
        configFolderName.beVisibleIf(showFolderName)
        (configImage.layoutParams as RelativeLayout.LayoutParams).bottomMargin = if (showFolderName) 0 else resources.getDimension(R.dimen.normal_margin).toInt()
    }
}
