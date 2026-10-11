/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.metroboard.ime.R
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp

/**
 * Quick keyboard size: key height and one-handed side, applied live so the change is visible behind the
 * panel as it is made. The same two settings live on the Layout page of Settings; this is the shortcut.
 */
class ResizePanelView(
    context: Context,
    heightMode: String,
    oneHandedMode: String,
    private val onHeight: (String) -> Unit,
    private val onOneHanded: (String) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(KeyboardColors.background)
        setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(2))

        addView(header(), LayoutParams(LayoutParams.MATCH_PARENT, context.dp(44)))
        addSection(
            context.getString(R.string.height_scale_title),
            listOf(
                "COMPACT" to context.getString(R.string.height_small),
                "NORMAL" to context.getString(R.string.height_normal),
                "TALL" to context.getString(R.string.height_tall),
            ),
            heightMode.uppercase(),
            onHeight,
        )
        addSection(
            context.getString(R.string.one_handed_title),
            listOf(
                "OFF" to context.getString(R.string.one_handed_off),
                "LEFT" to context.getString(R.string.one_handed_left),
                "RIGHT" to context.getString(R.string.one_handed_right),
            ),
            oneHandedMode.uppercase(),
            onOneHanded,
        )
    }

    private fun header(): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_back)
            contentDescription = context.getString(R.string.clipboard_back)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(KeyboardColors.foreground)
            setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
            isClickable = true
            setOnClickListener { onClose() }
        }, LayoutParams(context.dp(48), LayoutParams.MATCH_PARENT))
        addView(TextView(context).apply {
            text = context.getString(R.string.resize_title)
            textSize = 20f
            setTextColor(KeyboardColors.foreground)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(context.dp(4), 0, 0, 0)
        })
    }

    private fun addSection(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
        addView(TextView(context).apply {
            text = title
            textSize = 13f
            setTextColor(KeyboardColors.muted)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(context.dp(6), context.dp(6), 0, context.dp(2))
        })
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        val segments = mutableListOf<Pair<String, TextView>>()
        fun paint(current: String) = segments.forEach { (key, view) ->
            val on = key == current
            val fill = if (on) KeyboardColors.accent else KeyboardColors.key
            view.setBackgroundColor(fill)
            view.setTextColor(if (on) KeyboardColors.onColor(fill) else KeyboardColors.foreground)
            view.isSelected = on
        }
        options.forEach { (key, label) ->
            val view = TextView(context).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER
                maxLines = 1
                isClickable = true
                setOnClickListener {
                    paint(key)
                    onSelect(key)
                }
            }
            segments += key to view
            row.addView(view, LayoutParams(0, context.dp(42), 1f).apply { setMargins(context.dp(2), 0, context.dp(2), 0) })
        }
        paint(selected)
        addView(row)
    }
}
