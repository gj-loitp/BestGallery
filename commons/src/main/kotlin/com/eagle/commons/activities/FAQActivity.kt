package com.eagle.commons.activities

import android.os.Bundle
import android.view.LayoutInflater
import com.eagle.commons.R
import com.eagle.commons.ext.baseConfig
import com.eagle.commons.ext.getAdjustedPrimaryColor
import com.eagle.commons.ext.underlineText
import com.eagle.commons.helpers.APP_FAQ
import com.eagle.commons.helpers.APP_ICON_IDS
import com.eagle.commons.helpers.APP_LAUNCHER_NAME
import com.eagle.commons.models.FAQItem
import kotlinx.android.synthetic.main.a_faq.*
import kotlinx.android.synthetic.main.v_license_faq_item.view.*
import java.util.*

class FAQActivity : BaseSimpleActivity() {
    override fun getAppIconIDs() = intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList()

    override fun getAppLauncherName() = intent.getStringExtra(APP_LAUNCHER_NAME) ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.a_faq)

        val titleColor = getAdjustedPrimaryColor()
        val textColor = baseConfig.textColor

        val inflater = LayoutInflater.from(this)
        val faqItems = intent.getSerializableExtra(APP_FAQ) as ArrayList<FAQItem>
        faqItems.forEach {
            val faqItem = it
            inflater.inflate(R.layout.v_license_faq_item, null).apply {
                licenseFaqTitle.apply {
                    text =
                        if (faqItem.title is Int) getString(faqItem.title) else faqItem.title as String
                    underlineText()
                    setTextColor(titleColor)
                }

                licenseFaqText.apply {
                    text =
                        if (faqItem.text is Int) getString(faqItem.text) else faqItem.text as String
                    setTextColor(textColor)
                }
                faqHolder.addView(this)
            }
        }
    }
}
