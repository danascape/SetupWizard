/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.util

import androidx.annotation.DrawableRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * Puts [iconResId] on the checked button and takes it off the others, the way a Material connected
 * button group marks its selection. The icon is not left in place on the unchecked buttons because
 * it would keep reserving its width there and push their labels off center.
 */
fun MaterialButtonToggleGroup.updateCheckedIcons(@DrawableRes iconResId: Int) {
    for (index in 0 until childCount) {
        val button = getChildAt(index) as? MaterialButton ?: continue
        if (button.isChecked) {
            button.setIconResource(iconResId)
        } else {
            button.icon = null
        }
    }
}
