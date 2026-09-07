/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.settings

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import lineageos.providers.LineageSettings
import org.lineageos.setupwizard.R
import org.lineageos.setupwizard.base.BaseSetupWizardActivity
import org.lineageos.setupwizard.util.slideToReveal
import org.lineageos.setupwizard.util.updateCheckedIcons

class ThemeSettingsActivity : BaseSetupWizardActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        glifLayout.setDescriptionText(getString(R.string.theme_summary))

        val uiModeManager = getSystemService(UiModeManager::class.java)
        val modeGroup = findViewById<MaterialButtonToggleGroup>(R.id.theme_mode_group)
        val themeControls = findViewById<View>(R.id.theme_controls)
        val blackThemeCard = findViewById<View>(R.id.black_theme_card)
        val blackTheme = findViewById<MaterialSwitch>(R.id.black_theme)
        val isNight =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        blackTheme.isChecked =
            LineageSettings.Secure.getInt(
                contentResolver,
                LineageSettings.Secure.BERRY_BLACK_THEME,
                0,
            ) != 0
        blackTheme.setOnCheckedChangeListener { _, checked ->
            LineageSettings.Secure.putInt(
                contentResolver,
                LineageSettings.Secure.BERRY_BLACK_THEME,
                if (checked) 1 else 0,
            )
        }

        // Check before listening, the night mode is already applied at this point.
        modeGroup.check(if (isNight) R.id.mode_dark else R.id.mode_light)
        modeGroup.updateCheckedIcons(R.drawable.ic_check)

        modeGroup.addOnButtonCheckedListener { _, checkedId, checked ->
            if (!checked) {
                return@addOnButtonCheckedListener
            }
            val night = checkedId == R.id.mode_dark
            modeGroup.updateCheckedIcons(R.drawable.ic_check)
            // A pure black background only means anything with the dark theme on.
            themeControls.slideToReveal(blackThemeCard, revealed = night, animate = true)
            uiModeManager.setNightModeActivated(night)
        }

        themeControls.post {
            themeControls.slideToReveal(blackThemeCard, revealed = isNight, animate = false)
        }
    }

    override val layoutResId: Int = R.layout.setup_theme

    override val titleResId: Int = R.string.setup_theme

    override val iconResId: Int = R.drawable.ic_theme
}
