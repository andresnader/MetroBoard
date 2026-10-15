/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.settings

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.metroboard.ime.R
import com.metroboard.ime.clipboard.Clip
import com.metroboard.ime.clipboard.ClipboardHistories
import com.metroboard.ime.clipboard.ClipboardHistory
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp

/**
 * Manage what the clipboard history has saved: switch it on or off, edit a clip, pin it, delete it, clear it. It works
 * on the same in-memory history the keyboard uses (same process), so a change shows in the keyboard's panel at once.
 */
class ClipboardManagerActivity : Activity() {
    private lateinit var preferences: KeyboardPreferences
    private lateinit var history: ClipboardHistory
    private lateinit var list: LinearLayout
    private val onChange: () -> Unit = { runOnUiThread { if (::list.isInitialized) refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = KeyboardPreferences(this)
        history = ClipboardHistories.shared(this)
        buildUi()
        history.addListener(onChange)
        // Opened from a long press on a clip in the keyboard: go straight to editing that clip.
        val wanted = intent.getLongExtra(EXTRA_EDIT_ID, NO_CLIP)
        if (wanted != NO_CLIP && savedInstanceState == null) {
            history.clips(System.currentTimeMillis()).firstOrNull { it.id == wanted }?.let(::edit)
        }
    }

    override fun onDestroy() {
        history.removeListener(onChange)
        super.onDestroy()
    }

    private fun buildUi() {
        val root = metroScaffold(preferences)
        root.addView(metroBackHeader(getString(R.string.clipboard_manage), getString(R.string.dict_back)) { finish() })

        root.addView(metroToggle(getString(R.string.clipboard_history), preferences.clipboardHistory) {
            preferences.clipboardHistory = it
            // Off must mean nothing is stored: switching it off forgets what was kept, pinned clips included.
            if (!it) history.clearAll()
        })
        root.addView(metroLabel(getString(R.string.clipboard_manage_hint), MetroType.HELP, KeyboardColors.muted))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(metroButton(getString(R.string.clipboard_clear_unpinned)) {
            history.clearUnpinned()
            Toast.makeText(this, R.string.clipboard_cleared, Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        buttons.addView(metroButton(getString(R.string.clear_clipboard)) {
            history.clearAll()
            Toast.makeText(this, R.string.clipboard_cleared, Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(4) })
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list)
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val clips = history.clips(System.currentTimeMillis())
        if (clips.isEmpty()) {
            list.addView(metroLabel(getString(R.string.clipboard_empty), MetroType.BODY, KeyboardColors.muted))
            return
        }
        clips.forEach { list.addView(row(it), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }) }
    }

    private fun row(clip: Clip): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64)
        isClickable = true
        contentDescription = "${clip.text}. ${getString(R.string.clipboard_edit_title)}"
        setOnClickListener { edit(clip) }
        background = GradientDrawable().apply { setColor(KeyboardColors.key) }
        // A pinned clip carries an accent edge, like in the keyboard's panel.
        addView(View(context).apply {
            setBackgroundColor(if (clip.pinned) KeyboardColors.accent else 0)
        }, LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT))
        addView(TextView(context).apply {
            text = clip.text.trim()
            textSize = MetroType.LIST
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(KeyboardColors.foreground)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(dp(10), dp(8), dp(4), dp(8))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(icon(R.drawable.ic_pin, getString(if (clip.pinned) R.string.clipboard_unpin else R.string.clipboard_pin),
            if (clip.pinned) KeyboardColors.accent else KeyboardColors.muted) { history.setPinned(clip.id, !clip.pinned) },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(icon(R.drawable.ic_close, getString(R.string.clipboard_delete), KeyboardColors.muted) { history.delete(clip.id) },
            LinearLayout.LayoutParams(dp(48), dp(48)))
    }

    private fun icon(drawable: Int, description: String, tint: Int, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(drawable)
        contentDescription = description
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(tint)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun edit(clip: Clip) {
        val field = EditText(this).apply {
            setText(clip.text)
            setSelection(text.length)
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        AlertDialog.Builder(
            this,
            if (KeyboardColors.isDarkTheme) android.R.style.Theme_DeviceDefault_Dialog_Alert
            else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert,
        ).setTitle(R.string.clipboard_edit_title).setView(field)
            .setPositiveButton(R.string.dict_save) { _, _ ->
                if (!history.edit(clip.id, field.text.toString(), System.currentTimeMillis())) {
                    Toast.makeText(this, R.string.clipboard_invalid, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        /** The id of the clip to open for editing (sent when a clip is long-pressed in the keyboard). */
        const val EXTRA_EDIT_ID = "edit_clip_id"
        private const val NO_CLIP = -1L
    }
}
