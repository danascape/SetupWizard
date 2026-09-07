/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.util

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
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

/**
 * Slides this container down by the height of [card], its own last child, so that the card ends up
 * outside of the content area, and back up to reveal it again. Moving the container rather than the
 * card itself reads as the controls making room for it.
 */
fun View.slideToReveal(card: View, revealed: Boolean, animate: Boolean, durationMs: Long = 200L) {
    animate().cancel()

    val margin = (card.layoutParams as MarginLayoutParams).topMargin
    val offset = if (revealed) 0f else (card.height + margin).toFloat()

    if (revealed) {
        card.visibility = View.VISIBLE
    }

    // Before the first layout the offset is not known yet, so settle into place instead.
    if (!animate || !card.isLaidOut) {
        translationY = offset
        card.visibility = if (revealed) View.VISIBLE else View.INVISIBLE
        return
    }

    animate().translationY(offset).setDuration(durationMs).withEndAction {
        if (!revealed) {
            card.visibility = View.INVISIBLE
        }
    }
}
