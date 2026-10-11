package com.metroboard.ime.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.metroboard.ime.R
import com.metroboard.ime.haptics.HapticFeedback
import com.metroboard.ime.keyboard.*
import com.metroboard.ime.prediction.LocalSuggestionEngine
import com.metroboard.ime.settings.KeyboardPreferences
import com.metroboard.ime.sound.KeySound
import com.metroboard.ime.ui.KeyboardView
import com.metroboard.ime.util.KeyboardColors

class WordFlowImeService : InputMethodService(), KeyboardView.Listener {
    private lateinit var preferences: KeyboardPreferences
    private lateinit var sound: KeySound
    private val haptics = HapticFeedback()
    private val editor = InputConnectionEditor { currentInputConnection }
    private val controller = EditorController(editor, LocalSuggestionEngine())
    private var keyboard: KeyboardView? = null
    private var page = KeyboardPage.LETTERS
    private var policy = EditorPolicy.from(EditorInfo(), false)

    override fun onCreate() {
        super.onCreate()
        preferences = KeyboardPreferences(this)
        sound = KeySound(this)
    }

    override fun onCreateInputView(): View = KeyboardView(this, this).also {
        keyboard = it
        KeyboardColors.accent = preferences.accentColor
        it.setLayout(page, policy, preferences.numberRow, preferences.heightScale, preferences.language, preferences.wordFlow)
        render()
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        KeyboardColors.accent = preferences.accentColor
        policy = EditorPolicy.from(attribute, preferences)
        editor.start(attribute, policy)
        controller.start(policy.editing)
        page = KeyboardPage.LETTERS
        keyboard?.setLayout(page, policy, preferences.numberRow, preferences.heightScale, preferences.language, preferences.wordFlow)
        render()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        KeyboardColors.accent = preferences.accentColor
        policy = EditorPolicy.from(info, preferences)
        controller.start(policy.editing)
        keyboard?.setLayout(page, policy, preferences.numberRow, preferences.heightScale, preferences.language, preferences.wordFlow)
        render()
    }

    // Keep the host editor visible in landscape instead of a full-screen extract UI.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
        newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (editor.selectionChanged(newSelStart, newSelEnd)) {
            keyboard?.dismissPopups()
            controller.cursorMoved()
        }
        render()
    }

    override fun onKey(key: KeySpec) {
        val now = SystemClock.uptimeMillis()
        keyboard?.let { haptics.play(it, preferences.vibration) }
        sound.play(preferences.sound, key.action)
        when (key.action) {
            KeyAction.TEXT -> if (key.label.isNotEmpty()) controller.text(key.label)
            KeyAction.SHIFT -> controller.tapShift(now)
            KeyAction.DELETE -> controller.backspace()
            KeyAction.SPACE -> controller.space(now)
            KeyAction.ENTER -> controller.enter()
            KeyAction.SYMBOLS -> changePage(KeyboardPage.SYMBOLS)
            KeyAction.MORE_SYMBOLS -> changePage(KeyboardPage.MORE_SYMBOLS)
            KeyAction.LETTERS -> changePage(KeyboardPage.LETTERS)
            KeyAction.EMOJI -> keyboard?.showEmoticons()
        }
        render()
    }

    private fun changePage(next: KeyboardPage) {
        controller.cancelSequence()
        page = next
        keyboard?.setLayout(page, policy, preferences.numberRow, preferences.heightScale, preferences.language, preferences.wordFlow)
    }

    override fun onSuggestion(word: String) {
        controller.chooseSuggestion(word)
        keyboard?.let { haptics.play(it, preferences.vibration) }
        sound.play(preferences.sound)
        render()
    }

    override fun onWordFlow(word: String, candidates: List<String>) {
        keyboard?.let { haptics.play(it, preferences.vibration) }
        sound.play(preferences.sound, KeyAction.TEXT)
        controller.commitWordFlow(word)
        render()
    }

    override fun onLanguageToggled() {
        val nextLang = if (preferences.language == "es") "en" else "es"
        preferences.language = nextLang
        keyboard?.let { haptics.play(it, preferences.vibration) }
        keyboard?.setLayout(page, policy, preferences.numberRow, preferences.heightScale, preferences.language, preferences.wordFlow)
        render()
    }

    override fun onSwitchKeyboard() {
        controller.cancelSequence()
        keyboard?.dismissPopups()
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    override fun onAlternativesOpened() { controller.cancelSequence() }

    override fun onFinishInputView(finishingInput: Boolean) {
        keyboard?.dismissPopups()
        keyboard?.render(ShiftState.LOWERCASE, false, emptyList(), "")
        controller.finish()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        controller.finish()
        editor.finish()
        keyboard?.dismissPopups()
        keyboard?.render(ShiftState.LOWERCASE, false, emptyList(), "")
        super.onFinishInput()
    }

    override fun onDestroy() {
        keyboard?.dismissPopups()
        keyboard = null
        super.onDestroy()
    }

    private fun render() {
        keyboard?.render(controller.shift.state, policy.editing.forceUppercase,
            controller.suggestions(), enterLabel())
    }

    private fun enterLabel(): String {
        val info = currentInputEditorInfo ?: return ""
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return ""
        if (info.actionId != 0 && info.actionLabel != null) return info.actionLabel.toString()
        val label = when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_DONE -> R.string.action_done
            EditorInfo.IME_ACTION_NEXT -> R.string.action_next
            EditorInfo.IME_ACTION_PREVIOUS -> R.string.action_previous
            EditorInfo.IME_ACTION_SEARCH -> R.string.action_search
            EditorInfo.IME_ACTION_GO -> R.string.action_go
            EditorInfo.IME_ACTION_SEND -> R.string.action_send
            else -> return ""
        }
        return getString(label)
    }
}
