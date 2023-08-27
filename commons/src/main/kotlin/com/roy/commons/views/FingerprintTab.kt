package com.roy.commons.views

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.github.ajalt.reprint.core.AuthenticationFailureReason
import com.github.ajalt.reprint.core.AuthenticationListener
import com.github.ajalt.reprint.core.Reprint
import com.roy.commons.R
import com.roy.commons.ext.applyColorFilter
import com.roy.commons.ext.baseConfig
import com.roy.commons.ext.beGoneIf
import com.roy.commons.ext.toast
import com.roy.commons.ext.updateTextColors
import com.roy.commons.helpers.PROTECTION_FINGERPRINT
import com.roy.commons.itf.HashListener
import com.roy.commons.itf.SecurityTab
import kotlinx.android.synthetic.main.v_tab_fingerprint.view.*

class FingerprintTab(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs),
    SecurityTab {
    private val RECHECK_PERIOD = 3000L
    private val registerHandler = Handler(Looper.getMainLooper())

    lateinit var hashListener: HashListener

    override fun onFinishInflate() {
        super.onFinishInflate()
        val textColor = context.baseConfig.textColor
        context.updateTextColors(fingerprintLockHolder)
        fingerprintImage.applyColorFilter(textColor)

        fingerprintSettings.setOnClickListener {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun initTab(requiredHash: String, listener: HashListener, scrollView: MyScrollView) {
        hashListener = listener
    }

    override fun visibilityChanged(isVisible: Boolean) {
        if (isVisible) {
            checkRegisteredFingerprints()
        } else {
            Reprint.cancelAuthentication()
        }
    }

    private fun checkRegisteredFingerprints() {
        val hasFingerprints = Reprint.hasFingerprintRegistered()
        fingerprintSettings.beGoneIf(hasFingerprints)
        fingerprintLabel.text =
            context.getString(if (hasFingerprints) R.string.place_finger else R.string.no_fingerprints_registered)

        Reprint.authenticate(object : AuthenticationListener {
            override fun onSuccess(moduleTag: Int) {
                hashListener.receivedHash("", PROTECTION_FINGERPRINT)
            }

            override fun onFailure(
                failureReason: AuthenticationFailureReason,
                fatal: Boolean,
                errorMessage: CharSequence?,
                moduleTag: Int,
                errorCode: Int,
            ) {
                when (failureReason) {
                    AuthenticationFailureReason.AUTHENTICATION_FAILED -> context.toast(R.string.authentication_failed)
                    AuthenticationFailureReason.LOCKED_OUT -> context.toast(R.string.authentication_blocked)
                    else -> {}
                }
            }
        })

        registerHandler.postDelayed({
            checkRegisteredFingerprints()
        }, RECHECK_PERIOD)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        registerHandler.removeCallbacksAndMessages(null)
        Reprint.cancelAuthentication()
    }
}
