package com.lvms.driver.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Pads this view by the system bars (and optionally the display cutout / IME)
 * on top of whatever padding it already has in XML. Needed because targetSdk
 * 37 forces edge-to-edge on Android 15+, and none of this app's layouts
 * account for that on their own.
 *
 * The original padding is captured once, before the listener is attached, so
 * repeat inset dispatches (e.g. IME show/hide) add onto the XML baseline
 * rather than accumulating onto whatever the previous dispatch already added.
 */
fun View.applyInsetPadding(
    top: Boolean = false,
    bottom: Boolean = false,
    horizontal: Boolean = true,
    includeIme: Boolean = false,
) {
    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        var typeMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        if (includeIme) {
            typeMask = typeMask or WindowInsetsCompat.Type.ime()
        }
        val bars = insets.getInsets(typeMask)

        view.updatePadding(
            left = if (horizontal) baseLeft + bars.left else baseLeft,
            top = if (top) baseTop + bars.top else baseTop,
            right = if (horizontal) baseRight + bars.right else baseRight,
            bottom = if (bottom) baseBottom + bars.bottom else baseBottom,
        )
        insets
    }
}