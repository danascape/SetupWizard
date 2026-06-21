/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.base

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import com.android.settingslib.Utils
import com.google.android.setupcompat.template.FooterBarMixin
import com.google.android.setupcompat.template.FooterButton
import com.google.android.setupcompat.util.ResultCodes.RESULT_SKIP
import com.google.android.setupcompat.util.WizardManagerHelper
import com.google.android.setupdesign.GlifLayout
import com.google.android.setupdesign.transition.TransitionHelper
import com.google.android.setupdesign.util.ThemeHelper
import org.lineageos.setupwizard.R
import org.lineageos.setupwizard.SetupWizardApp.Companion.LOGV
import org.lineageos.setupwizard.util.SetupWizardUtils

abstract class BaseSetupWizardActivity : AppCompatActivity() {

    private var footerBarMixin: FooterBarMixin? = null
    private var nextButton: FooterButton? = null
    private lateinit var nextIntentResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        if (LOGV) {
            logActivityState("onCreate savedInstanceState=$savedInstanceState")
        }
        super.onCreate(savedInstanceState)
        nextIntentResultLauncher =
            registerForActivityResult(StartDecoratedActivityForResult(), this::onNextIntentResult)
        initLayout()
        if (installFooterBar) {
            setupFooterBar()
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (LOGV) {
                        Log.v(TAG, "handleOnBackPressed()")
                    }
                    finishAction(RESULT_CANCELED, Intent().putExtra("onBackPressed", true))
                }
            },
        )
        // Apply default transition, to take effect whenever leaving this activity.
        applyForwardTransition()
    }

    override fun onStart() {
        if (LOGV) logActivityState("onStart")
        super.onStart()
    }

    override fun onRestart() {
        if (LOGV) logActivityState("onRestart")
        super.onRestart()
    }

    override fun onResume() {
        if (LOGV) logActivityState("onResume")
        super.onResume()
    }

    override fun onPause() {
        if (LOGV) logActivityState("onPause")
        super.onPause()
    }

    override fun onStop() {
        if (LOGV) logActivityState("onStop")
        super.onStop()
    }

    override fun onDestroy() {
        if (LOGV) logActivityState("onDestroy")
        super.onDestroy()
        nextIntentResultLauncher.unregister()
    }

    override fun onAttachedToWindow() {
        if (LOGV) logActivityState("onAttachedToWindow")
        super.onAttachedToWindow()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        if (LOGV) {
            Log.v(TAG, "onRestoreInstanceState($savedInstanceState)")
        }
        super.onRestoreInstanceState(savedInstanceState)
        val currentId = savedInstanceState.getInt("currentFocus", -1)
        if (currentId != -1) {
            findViewById<View>(currentId)?.requestFocus()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentFocus", currentFocus?.id ?: -1)
        if (LOGV) {
            Log.v(TAG, "onSaveInstanceState($outState)")
        }
    }

    /**
     * Sets up the footer bar buttons via the [GlifLayout]'s [FooterBarMixin], matching the standard
     * Setup Wizard footer. The "next" (primary) button is always present; the "skip" (secondary)
     * button is only added when [showSkipButton] is true.
     */
    private fun setupFooterBar() {
        val mixin = getGlifLayout().getMixin(FooterBarMixin::class.java)
        footerBarMixin = mixin

        nextButton =
            FooterButton.Builder(this)
                .setText(R.string.next)
                .setListener { onNextPressed() }
                .setButtonType(FooterButton.ButtonType.NEXT)
                .build()
                .also { mixin.setPrimaryButton(it) }

        applyPrimaryButtonColors()

        if (showSkipButton) {
            mixin.setSecondaryButton(
                FooterButton.Builder(this)
                    .setText(R.string.skip)
                    .setListener { onSkipPressed() }
                    .setButtonType(FooterButton.ButtonType.SKIP)
                    .build()
            )
        }
    }

    /**
     * Pin the filled primary (next) button to the framework dynamic primary / on-primary color pair
     * directly on the inflated view. FooterBarMixin's own color pipeline
     * (partner/dynamic/expressive) is not wired up on this build, so without this the label can
     * fail to contrast with the fill. This must be re-applied whenever the button's enabled state
     * or text changes, because FooterBarMixin re-applies its own text color on those events.
     */
    private fun applyPrimaryButtonColors() {
        footerBarMixin?.primaryButtonView?.apply {
            backgroundTintList =
                ColorStateList.valueOf(getColor(R.color.setup_footer_primary_button_background))
            setTextColor(getColor(R.color.setup_footer_primary_button_text))
        }
    }

    fun setNextAllowed(allowed: Boolean) {
        nextButton?.isEnabled = allowed
        applyPrimaryButtonColors()
    }

    protected open fun onNextPressed() {
        nextAction(RESULT_OK)
    }

    protected open fun onSkipPressed() {
        nextAction(RESULT_SKIP)
    }

    protected fun setNextText(resId: Int) {
        nextButton?.setText(this, resId)
        applyPrimaryButtonColors()
    }

    fun getNextButton(): Button = footerBarMixin!!.primaryButtonView

    protected fun onSetupStart() {
        if (SetupWizardUtils.isOwner()) {
            tryEnablingWifi()
        }
    }

    override fun finish() {
        if (LOGV) {
            Log.v(TAG, "finish")
        }
        super.finish()
    }

    protected fun finishAction(resultCode: Int, data: Intent? = null) {
        if (resultCode != RESULT_CANCELED) {
            nextAction(resultCode, data)
            finish()
        } else {
            setResult(resultCode, data)
            finish()
            applyBackwardTransition()
        }
    }

    fun nextAction(resultCode: Int, data: Intent? = null) {
        if (LOGV) {
            Log.v(TAG, "nextAction resultCode=$resultCode data=$data this=$this")
        }
        require(resultCode != RESULT_CANCELED) { "Cannot call nextAction with RESULT_CANCELED" }
        setResult(resultCode, data)
        val nextIntent = WizardManagerHelper.getNextIntent(intent, resultCode, data)
        nextIntentResultLauncher.launch(nextIntent)
    }

    /** Adorn the Intent with Setup Wizard-related extras. */
    protected open fun decorateIntent(intent: Intent): Intent =
        intent
            .putExtra(WizardManagerHelper.EXTRA_IS_FIRST_RUN, isFirstRun())
            .putExtra(WizardManagerHelper.EXTRA_IS_SETUP_FLOW, true)
            .putExtra(WizardManagerHelper.EXTRA_THEME, ThemeHelper.THEME_GLIF_EXPRESSIVE)

    override fun startActivity(intent: Intent) {
        super.startActivity(decorateIntent(intent))
    }

    protected open fun onNextIntentResult(activityResult: ActivityResult) {
        val resultCode = activityResult.resultCode
        val data = activityResult.data
        if (LOGV) {
            Log.v(TAG, "onNextIntentResult($resultCode, ${data?.extras})")
        }
    }

    protected fun tryEnablingWifi(): Boolean =
        getSystemService(WifiManager::class.java)?.setWifiEnabled(true) ?: false

    private fun isFirstRun(): Boolean = true

    protected fun logActivityState(prefix: String) {
        Log.v(TAG, "$prefix isResumed=$isResumed isFinishing=$isFinishing isDestroyed=$isDestroyed")
    }

    private fun initLayout() {
        if (layoutResId != -1) {
            setContentView(layoutResId)
        }
        if (titleResId != -1) {
            val headerText = TextUtils.expandTemplate(getText(titleResId))
            getGlifLayout().setHeaderText(headerText)
        }
        if (iconResId != -1) {
            val layout = getGlifLayout()
            val icon: Drawable = getDrawable(iconResId)!!.mutate()
            icon.setTintList(Utils.getColorAccent(layout.context))
            layout.setIcon(icon)
        }
    }

    protected fun getGlifLayout(): GlifLayout = requireViewById(R.id.setup_wizard_layout)

    protected open val layoutResId: Int = -1

    protected open val titleResId: Int = -1

    protected open val iconResId: Int = -1

    /** Whether to install the [FooterBarMixin] footer buttons on this screen's [GlifLayout]. */
    protected open val installFooterBar: Boolean = true

    /** Whether the footer bar should show a secondary "skip" button. */
    protected open val showSkipButton: Boolean = false

    protected open fun applyForwardTransition() {
        TransitionHelper.applyForwardTransition(this, DEFAULT_TRANSITION, true)
    }

    protected open fun applyBackwardTransition() {
        TransitionHelper.applyBackwardTransition(this, DEFAULT_TRANSITION, true)
    }

    protected inner class StartDecoratedActivityForResult :
        ActivityResultContract<Intent, ActivityResult>() {

        private val wrappedContract = StartActivityForResult()

        override fun createIntent(context: Context, input: Intent): Intent =
            decorateIntent(wrappedContract.createIntent(context, input))

        override fun parseResult(resultCode: Int, intent: Intent?): ActivityResult =
            wrappedContract.parseResult(resultCode, intent)
    }

    companion object {
        val TAG: String = BaseSetupWizardActivity::class.java.simpleName
        const val DEFAULT_TRANSITION = TransitionHelper.TRANSITION_FADE_THROUGH
    }
}
