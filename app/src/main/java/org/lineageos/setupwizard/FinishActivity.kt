/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.google.android.setupcompat.template.FooterButton
import com.google.android.setupcompat.util.SystemBarHelper
import org.lineageos.setupwizard.base.BaseSetupWizardActivity
import org.lineageos.setupwizard.util.SetupWizardUtils

class FinishActivity : BaseSetupWizardActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getGlifLayout()
            .setDescriptionText(getString(R.string.setup_complete_summary, getString(R.string.os_name)))

        installPrimaryButton(R.string.done, FooterButton.ButtonType.DONE) {
            SetupWizardUtils.finishSetupWizard(this)
        }

        // This is the terminal screen: there's nothing to go back to.
        SystemBarHelper.setBackButtonVisible(window, false)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // no-op
                }
            },
        )
    }

    override val layoutResId: Int = R.layout.finish_activity

    override val titleResId: Int = R.string.setup_complete_title

    override val installFooterBar: Boolean = false
}
