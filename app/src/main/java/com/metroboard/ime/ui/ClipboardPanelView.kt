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
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.metroboard.ime.R
import com.metroboard.ime.clipboard.Clip
import com.metroboard.ime.clipboard.ClipboardHistory
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp

/**
 * The live clipboard: the clips copied recently, as flat tiles in two columns. It follows the history
 * while it is open, so something copied in another app appears here straight away.
 *
 * Tap a tile to type it; pin keeps it (and keeps it across restarts), the cross deletes it.
 */
class ClipboardPanelView(
    context: Context,
    private val history: ClipboardHistory,
    private val clock: () -> Long,
    private val onInsert: (String) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {
    private val grid = GridLayout(context).apply { columnCount = 2 }
    private val scroll = ScrollView(context)
    private val empty = TextView(context)
    private val refreshOnChange: () -> Unit = { post { refresh() } }

    init {
        orientation = VERTICAL
        setBackgroundColor(KeyboardColors.background)
        setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(2))

        addView(header(), LayoutParams(LayoutParams.MATCH_PARENT, context.dp(44)))

        scroll.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(grid)
        }
        empty.apply {
            text = context.getString(R.string.clipboard_empty)
            textSize = 15f
            setTextColor(KeyboardColors.muted)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(context.dp(24), context.dp(16), context.dp(24), context.dp(16))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    private fun header(): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(R.drawable.ic_back, context.getString(R.string.clipboard_back)) { onClose() }, LayoutParams(context.dp(48), LayoutParams.MATCH_PARENT))
        addView(TextView(context).apply {
            text = context.getString(R.string.clipboard_title)
            textSize = 20f
            setTextColor(KeyboardColors.foreground)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(context.dp(4), 0, 0, 0)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = context.getString(R.string.clipboard_clear)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(KeyboardColors.foreground)
            setPadding(context.dp(12), 0, context.dp(12), 0)
            isClickable = true
            background = pressedAccent()
            setOnClickListener { history.clearUnpinned() }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    /** Rebuilds the tiles from the history; cheap enough (at most a couple of dozen) to do on every change. */
    fun refresh() {
        val clips = history.clips(clock())
        grid.removeAllViews()
        clips.forEach { clip ->
            grid.addView(tile(clip), GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
            ).apply {
                width = 0
                setMargins(context.dp(3), context.dp(3), context.dp(3), context.dp(3))
            })
        }
        scroll.visibility = if (clips.isEmpty()) GONE else VISIBLE
        empty.visibility = if (clips.isEmpty()) VISIBLE else GONE
    }

    private fun tile(clip: Clip): View {
        val body = TextView(context).apply {
            text = clip.text.trim()
            textSize = 14f
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(KeyboardColors.foreground)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(context.dp(10), context.dp(8), context.dp(4), context.dp(8))
        }
        val pin = iconButton(R.drawable.ic_pin, context.getString(if (clip.pinned) R.string.clipboard_unpin else R.string.clipboard_pin)) {
            history.setPinned(clip.id, !clip.pinned)
        }.apply { setColorFilter(if (clip.pinned) KeyboardColors.accent else KeyboardColors.muted) }
        val delete = iconButton(R.drawable.ic_close, context.getString(R.string.clipboard_delete)) { history.delete(clip.id) }
            .apply { setColorFilter(KeyboardColors.muted) }

        val actions = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(pin, LayoutParams(context.dp(36), context.dp(36)))
            addView(delete, LayoutParams(context.dp(36), context.dp(36)))
        }
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            minimumHeight = context.dp(72)
            isClickable = true
            contentDescription = clip.text
            // A pinned tile carries an accent edge; the rest are plain key-coloured tiles.
            background = tileBackground(clip.pinned)
            setOnClickListener { onInsert(clip.text) }
            addView(body, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            addView(actions, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun tileBackground(pinned: Boolean) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(KeyboardColors.accent))
        val base = GradientDrawable().apply { setColor(KeyboardColors.key) }
        if (!pinned) {
            addState(intArrayOf(), base)
        } else {
            val edge = GradientDrawable().apply { setColor(KeyboardColors.accent) }
            addState(intArrayOf(), LayerDrawable(arrayOf(edge, base)).apply {
                setLayerInset(1, context.dp(4), 0, 0, 0) // a 4 dp accent bar on the start edge
            })
        }
    }

    private fun pressedAccent() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(KeyboardColors.accent))
        addState(intArrayOf(), ColorDrawable(0))
    }

    private fun iconButton(icon: Int, description: String, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(icon)
        contentDescription = description
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(KeyboardColors.foreground)
        setPadding(context.dp(9), context.dp(9), context.dp(9), context.dp(9))
        isClickable = true
        setOnClickListener { onClick() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        history.addListener(refreshOnChange)
        refresh() // anything copied while the panel was closed, and anything that expired meanwhile
    }

    override fun onDetachedFromWindow() {
        history.removeListener(refreshOnChange)
        super.onDetachedFromWindow()
    }
}
