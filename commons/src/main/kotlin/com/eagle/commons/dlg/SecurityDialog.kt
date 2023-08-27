package com.eagle.commons.dlg

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.eagle.commons.R
import com.eagle.commons.adt.PasswordTypesAdapter
import com.eagle.commons.ext.*
import com.eagle.commons.helpers.PROTECTION_FINGERPRINT
import com.eagle.commons.helpers.PROTECTION_PATTERN
import com.eagle.commons.helpers.PROTECTION_PIN
import com.eagle.commons.helpers.SHOW_ALL_TABS
import com.eagle.commons.itf.HashListener
import com.eagle.commons.views.MyDialogViewPager
import kotlinx.android.synthetic.main.dlg_security.view.*

class SecurityDialog(
    val activity: Activity,
    val requiredHash: String,
    val showTabIndex: Int,
    val callback: (hash: String, type: Int, success: Boolean) -> Unit,
) : HashListener {
    var dialog: AlertDialog? = null
    val view: View = LayoutInflater.from(activity).inflate(R.layout.dlg_security, null)
    private var tabsAdapter: PasswordTypesAdapter
    private var viewPager: MyDialogViewPager

    init {
        view.apply {
            viewPager = findViewById(R.id.dialogTabViewPager)
            viewPager.offscreenPageLimit = 2
            tabsAdapter =
                PasswordTypesAdapter(
                    context = context,
                    requiredHash = requiredHash,
                    hashListener = this@SecurityDialog,
                    scrollView = dialogScrollView
                )
            viewPager.adapter = tabsAdapter
            viewPager.onPageChangeListener {
                dialogTabLayout.getTabAt(it)!!.select()
            }

            viewPager.onGlobalLayout {
                updateTabVisibility()
            }

            if (showTabIndex == SHOW_ALL_TABS) {
                val textColor = context.baseConfig.textColor

                if (!activity.isFingerPrintSensorAvailable())
                    dialogTabLayout.removeTabAt(PROTECTION_FINGERPRINT)

                dialogTabLayout.setTabTextColors(textColor, textColor)
                dialogTabLayout.setSelectedTabIndicatorColor(context.baseConfig.primaryColor)
                dialogTabLayout.onTabSelectionChanged(tabSelectedAction = {
                    viewPager.currentItem = when {
                        it.text.toString().equals(
                            resources.getString(R.string.pattern),
                            true
                        ) -> PROTECTION_PATTERN

                        it.text.toString()
                            .equals(resources.getString(R.string.pin), true) -> PROTECTION_PIN

                        else -> PROTECTION_FINGERPRINT
                    }
                    updateTabVisibility()
                })
            } else {
                dialogTabLayout.beGone()
                viewPager.currentItem = showTabIndex
                viewPager.allowSwiping = false
            }
        }

        dialog = AlertDialog.Builder(activity)
            .setOnCancelListener { onCancelFail() }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancelFail() }
            .create().apply {
                activity.setupDialogStuff(view = view, dialog = this)
            }
    }

    private fun onCancelFail() {
        callback("", 0, false)
        dialog?.dismiss()
    }

    override fun receivedHash(hash: String, type: Int) {
        callback(hash, type, true)
        dialog?.dismiss()
    }

    private fun updateTabVisibility() {
        for (i in 0..2) {
            tabsAdapter.isTabVisible(i, viewPager.currentItem == i)
        }
    }
}
