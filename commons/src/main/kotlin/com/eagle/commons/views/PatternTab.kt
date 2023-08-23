package com.eagle.commons.views

import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RelativeLayout
import com.andrognito.patternlockview.PatternLockView
import com.andrognito.patternlockview.listener.PatternLockViewListener
import com.andrognito.patternlockview.utils.PatternLockUtils
import com.eagle.commons.R
import com.eagle.commons.extensions.baseConfig
import com.eagle.commons.extensions.toast
import com.eagle.commons.extensions.updateTextColors
import com.eagle.commons.helpers.PROTECTION_PATTERN
import com.eagle.commons.interfaces.HashListener
import com.eagle.commons.interfaces.SecurityTab
import kotlinx.android.synthetic.main.v_tab_pattern.view.patternLockHolder
import kotlinx.android.synthetic.main.v_tab_pattern.view.patternLockTitle
import kotlinx.android.synthetic.main.v_tab_pattern.view.patternLockView

class PatternTab(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs), SecurityTab {
    private var hash = ""
    private var requiredHash = ""
    private var scrollView: MyScrollView? = null
    lateinit var hashListener: HashListener

    override fun onFinishInflate() {
        super.onFinishInflate()
        val textColor = context.baseConfig.textColor
        context.updateTextColors(patternLockHolder)

        patternLockView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> scrollView?.isScrollable = false
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scrollView?.isScrollable = true
            }
            false
        }

        patternLockView.correctStateColor = context.baseConfig.primaryColor
        patternLockView.normalStateColor = textColor
        patternLockView.addPatternLockListener(object : PatternLockViewListener {
            override fun onComplete(pattern: MutableList<PatternLockView.Dot>?) {
                receivedHash(PatternLockUtils.patternToSha1(patternLockView, pattern))
            }

            override fun onCleared() {}

            override fun onStarted() {}

            override fun onProgress(progressPattern: MutableList<PatternLockView.Dot>?) {}
        })
    }

    override fun initTab(requiredHash: String, listener: HashListener, scrollView: MyScrollView) {
        this.requiredHash = requiredHash
        this.scrollView = scrollView
        hash = requiredHash
        hashListener = listener
    }

    private fun receivedHash(newHash: String) {
        when {
            hash.isEmpty() -> {
                hash = newHash
                patternLockView.clearPattern()
                patternLockTitle.setText(R.string.repeat_pattern)
            }
            hash == newHash -> {
                patternLockView.setViewMode(PatternLockView.PatternViewMode.CORRECT)
                Handler().postDelayed({
                    hashListener.receivedHash(hash, PROTECTION_PATTERN)
                }, 300)
            }
            else -> {
                patternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG)
                context.toast(R.string.wrong_pattern)
                Handler().postDelayed({
                    patternLockView.clearPattern()
                    if (requiredHash.isEmpty()) {
                        hash = ""
                        patternLockTitle.setText(R.string.insert_pattern)
                    }
                }, 1000)
            }
        }
    }

    override fun visibilityChanged(isVisible: Boolean) {}
}
