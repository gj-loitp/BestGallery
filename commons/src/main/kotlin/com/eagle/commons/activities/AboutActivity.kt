package com.eagle.commons.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import com.eagle.commons.R
import com.eagle.commons.dlg.ConfirmationDialog
import com.eagle.commons.ext.*
import com.eagle.commons.helpers.*
import com.eagle.commons.models.FAQItem
import kotlinx.android.synthetic.main.a_about.*
import java.util.*
import kotlin.collections.ArrayList

class AboutActivity : BaseSimpleActivity() {
    private var appName = ""
    private var linkColor = 0

    override fun getAppIconIDs() = intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList()

    override fun getAppLauncherName() = intent.getStringExtra(APP_LAUNCHER_NAME) ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.a_about)
        appName = intent.getStringExtra(APP_NAME) ?: ""
        linkColor = getAdjustedPrimaryColor()
    }

    override fun onResume() {
        super.onResume()
        updateTextColors(about_holder)

        setupWebsite()
        setupEmail()
        setupFAQ()
        setupUpgradeToPro()
        setupMoreApps()
        setupRateUs()
        setupInvite()
        setupLicense()
        setupFacebook()
        setupReddit()
        setupCopyright()
    }

    private fun setupWebsite() {
        val websiteText = String.format(getString(R.string.two_string_placeholder), getString(R.string.website_label), getString(R.string.my_website))
        aboutWebsite.text = websiteText
    }

    private fun setupEmail() {
        val label = getString(R.string.email_label)
        val email = getString(R.string.my_email)

        val appVersion = String.format(getString(R.string.app_version, intent.getStringExtra(APP_VERSION_NAME)))
        val deviceOS = String.format(getString(R.string.device_os), Build.VERSION.RELEASE)
        val newline = "%0D%0A"
        val separator = "------------------------------"
        val body = "$appVersion$newline$deviceOS$newline$separator$newline$newline$newline"
        val href = "$label<br><a href=\"mailto:$email?subject=$appName&body=$body\">$email</a>"
        aboutEmail.text = Html.fromHtml(href)

        if (intent.getBooleanExtra(SHOW_FAQ_BEFORE_MAIL, false) && !baseConfig.wasBeforeAskingShown) {
            aboutEmail.setOnClickListener {
                baseConfig.wasBeforeAskingShown = true
                aboutEmail.movementMethod = LinkMovementMethod.getInstance()
                aboutEmail.setOnClickListener(null)
                ConfirmationDialog(this, "", R.string.before_asking_question_read_faq, R.string.read_it, R.string.skip) {
                    aboutFaqLabel.performClick()
                }
            }
        } else {
            aboutEmail.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun setupFAQ() {
        var faqItems = /*intent.getSerializableExtra(APP_FAQ) as ArrayList<FAQItem>*/ ArrayList<FAQItem>()
        aboutFaqLabel.beVisibleIf(faqItems.isNotEmpty())
        aboutFaqLabel.setOnClickListener {
            openFAQ(faqItems)
        }

        aboutFaq.beVisibleIf(faqItems.isNotEmpty())
        aboutFaq.setOnClickListener {
            openFAQ(faqItems)
        }

        aboutFaq.setTextColor(linkColor)
        aboutFaq.underlineText()
    }

    private fun setupUpgradeToPro() {
        aboutUpgradeToPro.beVisibleIf(getCanAppBeUpgraded())
        aboutUpgradeToPro.setOnClickListener {
            launchUpgradeToProIntent()
        }

        aboutUpgradeToPro.setTextColor(linkColor)
        aboutUpgradeToPro.underlineText()
    }

    private fun openFAQ(faqItems: ArrayList<FAQItem>) {
        Intent(applicationContext, FAQActivity::class.java).apply {
            putExtra(APP_ICON_IDS, getAppIconIDs())
            putExtra(APP_LAUNCHER_NAME, getAppLauncherName())
            putExtra(APP_FAQ, faqItems)
            startActivity(this)
        }
    }

    private fun setupMoreApps() {
        aboutMoreApps.setOnClickListener {
            launchViewIntent("https://play.google.com/store/apps/dev?id=9070296388022589266")
        }
        aboutMoreApps.setTextColor(linkColor)
    }

    private fun setupInvite() {
        aboutInvite.setOnClickListener {
            val text = String.format(getString(R.string.share_text), appName, getStoreUrl())
            Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_SUBJECT, appName)
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
                startActivity(Intent.createChooser(this, getString(R.string.invite_via)))
            }
        }
        aboutInvite.setTextColor(linkColor)
    }

    private fun setupRateUs() {
        if (baseConfig.appRunCount < 10) {
            aboutRateUs.visibility = View.GONE
        } else {
            aboutRateUs.setOnClickListener {
                try {
                    launchViewIntent("market://details?id=${packageName.removeSuffix(".debug")}")
                } catch (ignored: ActivityNotFoundException) {
                    launchViewIntent(getStoreUrl())
                }
            }
        }
        aboutRateUs.setTextColor(linkColor)
    }

    private fun setupLicense() {
        aboutLicense.setOnClickListener {
            Intent(applicationContext, LicenseActivity::class.java).apply {
                putExtra(APP_ICON_IDS, getAppIconIDs())
                putExtra(APP_LAUNCHER_NAME, getAppLauncherName())
                putExtra(APP_LICENSES, intent.getIntExtra(APP_LICENSES, 0))
                startActivity(this)
            }
        }
        aboutLicense.setTextColor(linkColor)
    }

    private fun setupFacebook() {
        aboutFacebook.setOnClickListener {
            var link = "https://www.facebook.com/eagle"
            try {
                packageManager.getPackageInfo("com.facebook.katana", 0)
                link = "fb://page/150270895341774"
            } catch (ignored: Exception) {
            }

            launchViewIntent(link)
        }
    }

    private fun setupReddit() {
        aboutReddit.setOnClickListener {
            launchViewIntent("https://www.reddit.com/r/eagle")
        }
    }

    private fun setupCopyright() {
        val versionName = intent.getStringExtra(APP_VERSION_NAME) ?: ""
        val year = Calendar.getInstance().get(Calendar.YEAR)
        aboutCopyright.text = String.format(getString(R.string.copyright), versionName, year)
    }

    private fun getStoreUrl() = "https://play.google.com/store/apps/details?id=${packageName.removeSuffix(".debug")}"
}
