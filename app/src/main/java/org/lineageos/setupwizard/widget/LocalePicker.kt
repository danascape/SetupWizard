/*
 * SPDX-FileCopyrightText: 2024-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet

import androidx.appcompat.content.res.AppCompatResources

import android.widget.NumberPicker

import org.lineageos.setupwizard.R

class LocalePicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : NumberPicker(context, attrs) {

    init {
        setBackgroundColor(Color.TRANSPARENT)
        dividerDrawable = AppCompatResources.getDrawable(context, R.drawable.divider)
    }
}
