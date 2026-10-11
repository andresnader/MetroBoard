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

            // WP8.1 Metro style: Dark container with 2dp Lumia Accent border
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1C"))
                setStroke((2 * density).toInt(), KeyboardColors.accent)
                cornerRadius = 0f // Pure rectangular Metro tile aesthetic
            }
        }

        values.forEach { value ->
            val btn = TextView(context).apply {
                text = value
                textSize = if (value.length > 2) 16f else 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#262626"))
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
        }

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

    fun dismiss() {
        try {
            popup?.dismiss()
        } catch (_: Exception) {}
        popup = null
    }
}
