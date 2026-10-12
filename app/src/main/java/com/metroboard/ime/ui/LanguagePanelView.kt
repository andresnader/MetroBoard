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
import com.metroboard.ime.language.LanguagePacks
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp

/**
 * Language chooser shown in place of the keys (long-press on Space, or the globe key).
 *
 * It is a panel inside the keyboard rather than an AlertDialog on purpose: an InputMethodService has no
 * activity window token, so `AlertDialog.Builder(service).show()` always throws BadTokenException.
 */
class LanguagePanelView(
    context: Context,
    current: String,
    private val onSelect: (String) -> Unit,
    private val onSystemKeyboard: () -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(KeyboardColors.background)
        setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(2))

        addView(header(), LayoutParams(LayoutParams.MATCH_PARENT, context.dp(44)))
        LanguagePacks.all.map { it.code }.chunked(COLUMNS).forEach { codes ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                codes.forEach { code -> addView(languageCell(code, code.equals(current, ignoreCase = true)), cellParams()) }
            })
        }
        addView(systemKeyboardRow(), LayoutParams(LayoutParams.MATCH_PARENT, context.dp(44)).apply {
            setMargins(context.dp(2), context.dp(4), context.dp(2), 0)
        })
    }

    private fun cellParams() = LayoutParams(0, context.dp(48), 1f).apply {
        setMargins(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
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
            text = context.getString(R.string.language_title)
            textSize = 20f
            setTextColor(KeyboardColors.foreground)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(context.dp(4), 0, 0, 0)
        })
    }

    private fun languageCell(code: String, selected: Boolean): TextView = TextView(context).apply {
        text = nameOf(code)
        textSize = 17f
        gravity = Gravity.CENTER
        maxLines = 1
        isClickable = true
        isSelected = selected
        val fill = if (selected) KeyboardColors.accent else KeyboardColors.key
        setBackgroundColor(fill)
        setTextColor(if (selected) KeyboardColors.onColor(fill) else KeyboardColors.foreground)
        setOnClickListener { onSelect(code) }
    }

    private fun systemKeyboardRow(): TextView = TextView(context).apply {
        text = context.getString(R.string.select_system_ime)
        textSize = 15f
        gravity = Gravity.CENTER
        maxLines = 1
        isClickable = true
        setBackgroundColor(KeyboardColors.key)
        setTextColor(KeyboardColors.muted)
        setOnClickListener { onSystemKeyboard() }
    }

    private fun nameOf(code: String): String = context.getString(
        when (code) {
            "en" -> R.string.lang_en
            "fr" -> R.string.lang_fr
            "de" -> R.string.lang_de
            else -> R.string.lang_es
        }
    )

    private companion object {
        const val COLUMNS = 2
    }
}
