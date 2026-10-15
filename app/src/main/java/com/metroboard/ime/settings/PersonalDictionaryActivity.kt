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
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.metroboard.ime.R
import com.metroboard.ime.dictionary.LearnedWord
import com.metroboard.ime.dictionary.LocalPersonalDictionary
import com.metroboard.ime.language.LanguagePacks
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp
import java.text.Normalizer
import java.util.Locale

/**
 * The personal dictionary as the user sees it: every word the keyboard knows for one language, with search,
 * add, edit and delete. It edits the same shared instance the keyboard service uses, so a change is live at once.
 */
class PersonalDictionaryActivity : Activity() {
    private lateinit var preferences: KeyboardPreferences
    private lateinit var dictionary: LocalPersonalDictionary
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var language = "es"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = KeyboardPreferences(this)
        dictionary = LocalPersonalDictionary.shared(this)
        language = savedInstanceState?.getString(STATE_LANGUAGE) ?: preferences.language
        buildUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_LANGUAGE, language)
    }

    private fun buildUi() {
        val root = metroScaffold(preferences)

        root.addView(metroBackHeader(getString(R.string.personal_dictionary_title), getString(R.string.dict_back)) { finish() })
        root.addView(metroSegmented(
            LanguagePacks.all.map { it.code to languageName(it.code) },
            language,
        ) { code ->
            language = code
            refresh()
        })
        root.addView(addRow())
        search = metroTextField(getString(R.string.dict_search_hint)).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = refresh()
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list)
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        refresh()
    }

    /** The field and its button. The field never learns or suggests from what is typed into it. */
    private fun addRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val field = metroTextField(getString(R.string.dict_add_hint)).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setSingleLine()
        }
        fun add() {
            if (dictionary.addWord(field.text.toString(), language)) {
                field.setText("")
                refresh()
            } else {
                Toast.makeText(this@PersonalDictionaryActivity, R.string.dict_invalid_word, Toast.LENGTH_SHORT).show()
            }
        }
        field.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) add() }
        }
        addView(field, LinearLayout.LayoutParams(0, -2, 1f))
        addView(metroButton(getString(R.string.dict_add)) { add() }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
    }

    private fun refresh() {
        list.removeAllViews()
        val query = fold(if (::search.isInitialized) search.text.toString() else "")
        val words = dictionary.getAllWords(language)
            .filter { query.isEmpty() || fold(it.word).contains(query) }
            .sortedWith { a, b -> a.word.compareTo(b.word, ignoreCase = true) }
        if (words.isEmpty()) {
            list.addView(metroLabel(getString(R.string.no_learned_words), MetroType.BODY, KeyboardColors.muted))
            return
        }
        words.forEach { list.addView(row(it)) }
    }

    private fun row(item: LearnedWord): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(56)
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            contentDescription = "${item.word}. ${getString(R.string.dict_edit_hint)}"
            setOnClickListener { edit(item) }
            addView(TextView(context).apply {
                this.text = item.word
                textSize = MetroType.BODY
                setTextColor(KeyboardColors.foreground)
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            })
            addView(TextView(context).apply {
                this.text = if (item.frequency >= LocalPersonalDictionary.MIN_USES_TO_SUGGEST) "×${item.frequency}" else getString(R.string.dict_pending)
                textSize = MetroType.HELP
                setTextColor(KeyboardColors.muted)
            })
        }
        addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(context).apply {
            this.text = "✕"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(KeyboardColors.muted)
            contentDescription = getString(R.string.delete_word)
            isClickable = true
            setOnClickListener { confirmDelete(item) }
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
    }

    private fun edit(item: LearnedWord) {
        val field = EditText(this).apply {
            setText(item.word)
            setSelection(text.length)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        dialog().setTitle(R.string.dict_edit_title).setView(field)
            .setPositiveButton(R.string.dict_save) { _, _ ->
                if (dictionary.renameWord(item.word, field.text.toString(), language)) refresh()
                else Toast.makeText(this, R.string.dict_invalid_word, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(item: LearnedWord) {
        dialog().setTitle(R.string.delete_word_title)
            .setMessage(getString(R.string.delete_word_message, item.word))
            .setPositiveButton(R.string.delete_word) { _, _ ->
                dictionary.remove(item.word, language)
                Toast.makeText(this, R.string.word_deleted, Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Dialogs follow the keyboard theme, not the system one, so they match the screen they sit on. */
    private fun dialog() = AlertDialog.Builder(
        this,
        if (KeyboardColors.isDarkTheme) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert,
    )

    private fun languageName(code: String) = getString(
        when (code) {
            "en" -> R.string.lang_en
            "fr" -> R.string.lang_fr
            "de" -> R.string.lang_de
            else -> R.string.lang_es
        },
    )

    /** Lower case without accents, so "cancion" finds "canción". */
    private fun fold(text: String): String =
        Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private companion object {
        const val STATE_LANGUAGE = "language"
    }
}
