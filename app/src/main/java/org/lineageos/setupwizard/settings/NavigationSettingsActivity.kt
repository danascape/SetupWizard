/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.settings

import android.os.Bundle
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON_OVERLAY
import android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import lineageos.providers.LineageSettings
import org.lineageos.internal.util.DeviceKeysConstants.KEY_MASK_APP_SWITCH
import org.lineageos.setupwizard.R
import org.lineageos.setupwizard.SetupWizardApp
import org.lineageos.setupwizard.SetupWizardApp.Companion.DISABLE_NAV_KEYS
import org.lineageos.setupwizard.SetupWizardApp.Companion.NAVIGATION_OPTION_KEY
import org.lineageos.setupwizard.base.BaseSetupWizardActivity
import org.lineageos.setupwizard.util.SetupWizardUtils

class NavigationSettingsActivity : BaseSetupWizardActivity() {

    private var selection: String = NAV_BAR_MODE_GESTURAL_OVERLAY

    private lateinit var hideGesturalHint: MaterialSwitch
    private lateinit var hideGesturalHintCard: View
    private lateinit var navigationControls: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val navBarEnabled = SetupWizardApp.settingsBundle.getBoolean(DISABLE_NAV_KEYS, false)

        val deviceKeys =
            resources.getInteger(
                org.lineageos.platform.internal.R.integer.config_deviceHardwareKeys
            )
        val hasHomeKey = (deviceKeys and KEY_MASK_APP_SWITCH) != 0

        glifLayout.setDescriptionText(getString(R.string.navigation_summary))
        setNextText(R.string.next)

        val modeGroup = findViewById<MaterialButtonToggleGroup>(R.id.navigation_mode_group)
        val gestureButton = findViewById<MaterialButton>(R.id.mode_gesture)
        val swKeysButton = findViewById<MaterialButton>(R.id.mode_sw_keys)
        val navigationIllustration = findViewById<LottieAnimationView>(R.id.navigation_illustration)
        hideGesturalHint = findViewById(R.id.hide_navigation_hint)
        hideGesturalHintCard = findViewById(R.id.hide_navigation_hint_card)
        navigationControls = findViewById(R.id.navigation_controls)

        // Listen before anything below checks a button, so a forced selection is picked up too.
        modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }
            when (checkedId) {
                R.id.mode_gesture -> {
                    selection = NAV_BAR_MODE_GESTURAL_OVERLAY
                    navigationIllustration.setAnimation(R.raw.lottie_system_nav_fully_gestural)
                    setHintRevealed(revealed = true, animate = true)
                }

                R.id.mode_sw_keys -> {
                    selection = NAV_BAR_MODE_3BUTTON_OVERLAY
                    navigationIllustration.setAnimation(R.raw.lottie_system_nav_3_button)
                    setHintRevealed(revealed = false, animate = true)
                }
            }
            updateCheckedIcons(modeGroup)
            navigationIllustration.playAnimation()
        }

        var available = 3
        if (!SetupWizardUtils.isPackageInstalled(this, NAV_BAR_MODE_GESTURAL_OVERLAY)) {
            gestureButton.visibility = View.GONE
            modeGroup.check(R.id.mode_sw_keys)
            available--
        }

        if (!SetupWizardUtils.isPackageInstalled(this, NAV_BAR_MODE_3BUTTON_OVERLAY)) {
            swKeysButton.visibility = View.GONE
            available--
        }

        if (!navBarEnabled && hasHomeKey || available <= 1) {
            SetupWizardApp.settingsBundle.putString(
                NAVIGATION_OPTION_KEY,
                NAV_BAR_MODE_3BUTTON_OVERLAY,
            )
            finishAction(RESULT_OK)
        }

        updateCheckedIcons(modeGroup)

        // The offset is the card's height, so it can only be applied once laid out.
        navigationControls.post {
            setHintRevealed(selection == NAV_BAR_MODE_GESTURAL_OVERLAY, animate = false)
        }
    }

    /** Only the checked segment carries the check mark, as in a Material segmented button. */
    private fun updateCheckedIcons(modeGroup: MaterialButtonToggleGroup) {
        for (index in 0 until modeGroup.childCount) {
            val button = modeGroup.getChildAt(index) as? MaterialButton ?: continue
            if (button.isChecked) {
                button.setIconResource(R.drawable.ic_check)
            } else {
                button.icon = null
            }
        }
    }

    /**
     * The hint only applies to gestural navigation. Rather than animating the card itself, the
     * controls slide down over it by its own height, so that picking gestures looks like the
     * buttons moving up to make room for it.
     */
    private fun setHintRevealed(revealed: Boolean, animate: Boolean) {
        navigationControls.animate().cancel()

        val margin = (hideGesturalHintCard.layoutParams as MarginLayoutParams).topMargin
        val offset = if (revealed) 0f else (hideGesturalHintCard.height + margin).toFloat()

        if (revealed) {
            hideGesturalHintCard.visibility = View.VISIBLE
        }

        // Before the first layout the offset is not known yet, so settle into place instead.
        if (!animate || !hideGesturalHintCard.isLaidOut) {
            navigationControls.translationY = offset
            hideGesturalHintCard.visibility = if (revealed) View.VISIBLE else View.INVISIBLE
            return
        }

        navigationControls
            .animate()
            .translationY(offset)
            .setDuration(HINT_ANIM_DURATION_MS)
            .withEndAction {
                if (!revealed) {
                    hideGesturalHintCard.visibility = View.INVISIBLE
                }
            }
    }

    override fun onNextPressed() {
        SetupWizardApp.settingsBundle.putString(NAVIGATION_OPTION_KEY, selection)
        val hideHint = hideGesturalHint.isChecked
        LineageSettings.System.putIntForUser(
            contentResolver,
            LineageSettings.System.NAVIGATION_BAR_HINT,
            if (hideHint) 0 else 1,
            UserHandle.USER_CURRENT,
        )
        super.onNextPressed()
    }

    override val layoutResId: Int = R.layout.setup_navigation

    override val titleResId: Int = R.string.setup_navigation

    override val iconResId: Int = R.drawable.ic_navigation

    companion object {
        private const val HINT_ANIM_DURATION_MS = 200L
    }
}
