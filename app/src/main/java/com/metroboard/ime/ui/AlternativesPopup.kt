/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.metroboard.ime.keyboard.KeyAction
import com.metroboard.ime.keyboard.KeySpec
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp

class AlternativesPopup {
    private var popup: PopupWindow? = null
    private var tiles: List<Pair<TextView, String>> = emptyList()
    private var picked: ((String) -> Unit)? = null
    private var highlighted = -1
    private var slackPx = 0

    val isShowing: Boolean
        get() = popup?.isShowing == true

    @SuppressLint("RtlHardcoded")
    fun show(
        anchor: View,
        values: List<String>,
        onSelected: (String) -> Unit
    ) {
        dismiss()
        if (values.isEmpty() || !anchor.isAttachedToWindow) return
        val context = anchor.context
        val density = context.resources.displayMetrics.density

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = context.dp(3)
            setPadding(p, p, p, p)
            layoutDirection = View.LAYOUT_DIRECTION_LTR

            // Metro style: a container in the keyboard's own background with a 2dp accent border. Read from
            // KeyboardColors every time the popup is built, so a theme or accent change shows at once.
            background = GradientDrawable().apply {
                setColor(KeyboardColors.background)
                setStroke((2 * density).toInt(), KeyboardColors.accent)
                cornerRadius = 0f // Pure rectangular Metro tile aesthetic
            }
        }

        val built = ArrayList<Pair<TextView, String>>(values.size)
        values.forEach { value ->
            val btn = TextView(context).apply {
                text = value
                textSize = if (value.length > 2) 16f else 22f
                // Keys' own colours; the pressed tile takes the accent, with text chosen for contrast on it.
                setTextColor(
                    android.content.res.ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                        intArrayOf(KeyboardColors.onColor(KeyboardColors.accent), KeyboardColors.foreground),
                    ),
                )
                gravity = Gravity.CENTER
                setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
                background = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(KeyboardColors.accent))
                    addState(intArrayOf(), ColorDrawable(KeyboardColors.key))
                }
                setOnClickListener {
                    dismiss()
                    onSelected(value)
                }
            }
            val itemWidth = context.dp(if (value.length > 2) 54 else 42)
            val itemHeight = context.dp(48)
            val params = LinearLayout.LayoutParams(itemWidth, itemHeight).apply {
                setMargins(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
            }
            row.addView(btn, params)
            built += btn to value
        }
        tiles = built
        picked = onSelected
        highlighted = -1
        slackPx = context.dp(14)

        val window = PopupWindow(
            row,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            elevation = context.dp(6).toFloat()
        }
        popup = window

        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val windowOrigin = IntArray(2)
        anchor.rootView.getLocationOnScreen(windowOrigin)

        val at = PopupPlacement.above(
            anchorLeft = location[0], anchorTop = location[1],
            anchorWidth = anchor.width, anchorHeight = anchor.height,
            popupWidth = row.measuredWidth, popupHeight = row.measuredHeight,
            screenWidth = context.resources.displayMetrics.widthPixels,
            screenHeight = context.resources.displayMetrics.heightPixels,
            gap = context.dp(4), margin = context.dp(4),
            windowLeft = windowOrigin[0], windowTop = windowOrigin[1],
        )

        try {
            window.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, at.x, at.y)
        } catch (_: Exception) {}
    }

    /** Index of the tile under a finger at this screen position, or -1. See [AlternativesSelection]. */
    private fun indexAt(screenX: Float, screenY: Float): Int {
        if (tiles.isEmpty()) return -1
        val lefts = IntArray(tiles.size)
        val rights = IntArray(tiles.size)
        var bottom = 0
        val at = IntArray(2)
        tiles.forEachIndexed { i, (tile, _) ->
            tile.getLocationOnScreen(at)
            lefts[i] = at[0]
            rights[i] = at[0] + tile.width
            bottom = at[1] + tile.height
        }
        return AlternativesSelection.indexAt(screenX, screenY, lefts, rights, bottom, slackPx)
    }

    /** The finger that opened the popup moved: shows which tile releasing here would type. */
    fun fingerMoved(screenX: Float, screenY: Float) {
        val index = indexAt(screenX, screenY)
        if (index == highlighted) return
        tiles.getOrNull(highlighted)?.first?.isPressed = false
        tiles.getOrNull(index)?.first?.isPressed = true
        highlighted = index
    }

    /** The finger lifted: types the tile under it, if any, and closes the popup either way. */
    fun fingerReleased(screenX: Float, screenY: Float) {
        val index = indexAt(screenX, screenY)
        val value = tiles.getOrNull(index)?.second
        val callback = picked
        dismiss()
        if (value != null) callback?.invoke(value)
    }

    fun dismiss() {
        try {
            popup?.dismiss()
        } catch (_: Exception) {}
        popup = null
        tiles = emptyList()
        picked = null
        highlighted = -1
    }
}
