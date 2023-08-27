package com.eagle.commons.views

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.eagle.commons.R
import com.eagle.commons.extensions.*
import com.eagle.commons.helpers.PROTECTION_PIN
import com.eagle.commons.itf.HashListener
import com.eagle.commons.itf.SecurityTab
import kotlinx.android.synthetic.main.v_tab_pin.view.pin0
import kotlinx.android.synthetic.main.v_tab_pin.view.pin1
import kotlinx.android.synthetic.main.v_tab_pin.view.pin2
import kotlinx.android.synthetic.main.v_tab_pin.view.pin3
import kotlinx.android.synthetic.main.v_tab_pin.view.pin4
import kotlinx.android.synthetic.main.v_tab_pin.view.pin5
import kotlinx.android.synthetic.main.v_tab_pin.view.pin6
import kotlinx.android.synthetic.main.v_tab_pin.view.pin7
import kotlinx.android.synthetic.main.v_tab_pin.view.pin8
import kotlinx.android.synthetic.main.v_tab_pin.view.pin9
import kotlinx.android.synthetic.main.v_tab_pin.view.pinC
import kotlinx.android.synthetic.main.v_tab_pin.view.pinLockCurrentPin
import kotlinx.android.synthetic.main.v_tab_pin.view.pinLockHolder
import kotlinx.android.synthetic.main.v_tab_pin.view.pinLockTitle
import kotlinx.android.synthetic.main.v_tab_pin.view.pinOk
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*

class PinTab(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs), SecurityTab {
    private var hash = ""
    private var requiredHash = ""
    private var pin = ""
    private lateinit var hashListener: HashListener

    override fun onFinishInflate() {
        super.onFinishInflate()
        context.updateTextColors(pinLockHolder)

        pin0.setOnClickListener { addNumber("0") }
        pin1.setOnClickListener { addNumber("1") }
        pin2.setOnClickListener { addNumber("2") }
        pin3.setOnClickListener { addNumber("3") }
        pin4.setOnClickListener { addNumber("4") }
        pin5.setOnClickListener { addNumber("5") }
        pin6.setOnClickListener { addNumber("6") }
        pin7.setOnClickListener { addNumber("7") }
        pin8.setOnClickListener { addNumber("8") }
        pin9.setOnClickListener { addNumber("9") }
        pinC.setOnClickListener { clear() }
        pinOk.setOnClickListener { confirmPIN() }
        pinOk.applyColorFilter(context.baseConfig.textColor)
    }

    override fun initTab(requiredHash: String, listener: HashListener, scrollView: MyScrollView) {
        this.requiredHash = requiredHash
        hash = requiredHash
        hashListener = listener
    }

    private fun addNumber(number: String) {
        if (pin.length < 10) {
            pin += number
            updatePinCode()
        }
        performHapticFeedback()
    }

    private fun clear() {
        if (pin.isNotEmpty()) {
            pin = pin.substring(0, pin.length - 1)
            updatePinCode()
        }
        performHapticFeedback()
    }

    private fun confirmPIN() {
        val newHash = getHashedPin()
        if (pin.isEmpty()) {
            context.toast(R.string.please_enter_pin)
        } else if (hash.isEmpty()) {
            hash = newHash
            resetPin()
            pinLockTitle.setText(R.string.repeat_pin)
        } else if (hash == newHash) {
            hashListener.receivedHash(hash, PROTECTION_PIN)
        } else {
            resetPin()
            context.toast(R.string.wrong_pin)
            if (requiredHash.isEmpty()) {
                hash = ""
                pinLockTitle.setText(R.string.enter_pin)
            }
        }
        performHapticFeedback()
    }

    private fun resetPin() {
        pin = ""
        pinLockCurrentPin.text = ""
    }

    private fun updatePinCode() {
        pinLockCurrentPin.text = "*".repeat(pin.length)
        if (hash.isNotEmpty() && hash == getHashedPin()) {
            hashListener.receivedHash(hash, PROTECTION_PIN)
        }
    }

    private fun getHashedPin(): String {
        val messageDigest = MessageDigest.getInstance("SHA-1")
        messageDigest.update(pin.toByteArray(charset("UTF-8")))
        val digest = messageDigest.digest()
        val bigInteger = BigInteger(1, digest)
        return String.format(Locale.getDefault(), "%0${digest.size * 2}x", bigInteger)
            .lowercase(Locale.getDefault())
    }

    override fun visibilityChanged(isVisible: Boolean) {}
}
