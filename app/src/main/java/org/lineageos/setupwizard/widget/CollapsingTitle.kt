/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.widget

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.setupdesign.GlifLayout
import com.google.android.setupdesign.R as SudR
import org.lineageos.setupwizard.R

/**
 * Collapses the header title as the content scrolls, the way the collapsing toolbar in Settings
 * does.
 *
 * The expressive template keeps the header inside the scroller and draws the floating back button
 * as an overlay on top of it, so on its own the content slides underneath the button. Instead, put
 * an opaque bar behind the button for the content to scroll under, fade the header out, and fade a
 * single line copy of it in beside the button.
 *
 * Screens without a back button have nowhere to put the collapsed title, so they keep the header as
 * it is.
 */
fun GlifLayout.installCollapsingTitle() {
    val container = findViewById<FrameLayout>(SudR.id.sud_layout_container) ?: return
    val backButton = findViewById<View>(SudR.id.sud_layout_floating_back_button_container) ?: return
    val headerText = headerTextView ?: return
    // Either template content holds exactly one of these.
    val scroller =
        findViewById<View>(SudR.id.sud_scroll_view)
            ?: findViewById<View>(SudR.id.sud_recycler_view)
            ?: return

    // Both go above the scroller so they occlude it, and below the back button so it stays on top.
    val scrollerIndex = container.indexOfChild(scroller)
    val bar =
        View(context).apply {
            setBackgroundColor(resolveColor(android.R.attr.colorBackground))
            alpha = 0f
        }
    container.addView(
        bar,
        scrollerIndex + 1,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP),
    )
    val collapsedTitle =
        TextView(context, null, 0, R.style.CollapsedHeaderTitle).apply {
            alpha = 0f
            // The header is what the screen reader should announce, this is only a copy of it.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    container.addView(
        collapsedTitle,
        scrollerIndex + 2,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START,
        ),
    )

    val gap = resources.getDimensionPixelSize(R.dimen.collapsed_header_title_margin_start)
    val marginEnd = resources.getDimensionPixelSize(R.dimen.collapsed_header_title_margin_end)
    val distance = resources.getDimensionPixelSize(R.dimen.collapsed_header_title_distance)

    // Reused across scroll callbacks, which run on every frame of a fling.
    val headerLocation = IntArray(2)
    val containerLocation = IntArray(2)

    /**
     * The bar's height, which is also what the header scrolls past, or 0 before the first layout.
     */
    fun barHeight(): Int {
        if (!backButton.isVisible || backButton.height == 0) {
            return 0
        }
        val params = backButton.layoutParams as FrameLayout.LayoutParams
        return params.topMargin * 2 + backButton.height
    }

    fun update() {
        val height = barHeight()
        if (height == 0) {
            return
        }
        headerText.getLocationInWindow(headerLocation)
        container.getLocationInWindow(containerLocation)
        val headerBottom = headerLocation[1] + headerText.height - containerLocation[1]
        val fraction = ((height - headerBottom).toFloat() / distance).coerceIn(0f, 1f)
        headerText.alpha = 1f - fraction
        collapsedTitle.alpha = fraction
        bar.alpha = fraction
    }

    fun layoutOverlays() {
        val height = barHeight()
        if (height == 0) {
            return
        }
        collapsedTitle.text = headerText.text

        // Line the collapsed title up with the back button, past its end.
        val buttonParams = backButton.layoutParams as FrameLayout.LayoutParams
        val titleParams = collapsedTitle.layoutParams as FrameLayout.LayoutParams
        val start = buttonParams.marginStart + backButton.width + gap
        if (
            titleParams.marginStart == start &&
                titleParams.topMargin == buttonParams.topMargin &&
                titleParams.height == backButton.height &&
                bar.layoutParams.height == height
        ) {
            return
        }
        titleParams.marginStart = start
        titleParams.marginEnd = marginEnd
        titleParams.topMargin = buttonParams.topMargin
        titleParams.height = backButton.height
        collapsedTitle.layoutParams = titleParams
        bar.layoutParams = bar.layoutParams.apply { this.height = height }
    }

    container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        layoutOverlays()
        update()
    }
    // RecyclerView reports its scrolls through onScrollChanged too, so this covers both templates.
    scroller.setOnScrollChangeListener { _, _, _, _, _ -> update() }
}

private fun View.resolveColor(attr: Int): Int {
    val value = TypedValue()
    context.theme.resolveAttribute(attr, value, true)
    return if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
}
