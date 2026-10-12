/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ime

import android.app.KeyguardManager
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.metroboard.ime.R
import com.metroboard.ime.clipboard.ClipboardHistories
import com.metroboard.ime.clipboard.ClipboardHistory
import com.metroboard.ime.dictionary.LanguageDictionaryRegistry
import com.metroboard.ime.dictionary.LocalPersonalDictionary
import com.metroboard.ime.haptics.HapticFeedback
import com.metroboard.ime.keyboard.*
import com.metroboard.ime.prediction.LocalSuggestionEngine
import com.metroboard.ime.settings.KeyboardPreferences
import com.metroboard.ime.settings.SettingsActivity
import com.metroboard.ime.sound.KeySound
import com.metroboard.ime.ui.KeyboardView
import com.metroboard.ime.util.KeyboardColors

/** `ClipDescription.EXTRA_IS_SENSITIVE` (API 33), spelled out so it also reads on the older versions we support. */
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

class WordFlowImeService : InputMethodService(), KeyboardView.Listener {
    private lateinit var preferences: KeyboardPreferences
    private lateinit var sound: KeySound
    private lateinit var personalDict: LocalPersonalDictionary
    private val haptics = HapticFeedback()
    private val editor = InputConnectionEditor { currentInputConnection }
    private lateinit var suggestionEngine: LocalSuggestionEngine
    private lateinit var controller: EditorController
    private var keyboard: KeyboardView? = null
    private var page = KeyboardPage.LETTERS
    private var policy = EditorPolicy.from(EditorInfo(), false)
    private lateinit var clipboardHistory: ClipboardHistory
    private val primaryClipListener = ClipboardManager.OnPrimaryClipChangedListener { recordPrimaryClip() }

    override fun onCreate() {
        super.onCreate()
        preferences = KeyboardPreferences(this)
        sound = KeySound(this)
        personalDict = LocalPersonalDictionary.shared(this)
        suggestionEngine = LocalSuggestionEngine(personalDict, this, preferences.language)
        controller = EditorController(editor, suggestionEngine)
        // Dictionaries load on a background thread; refresh the suggestion bar when one is ready.
        val ui = android.os.Handler(android.os.Looper.getMainLooper())
        LanguageDictionaryRegistry.onDictionaryLoaded = { ui.post { if (::controller.isInitialized) render() } }
        LanguageDictionaryRegistry.preload(preferences.language, this)
        clipboardHistory = ClipboardHistories.shared(this)
        getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(primaryClipListener)
        recordPrimaryClip()
    }

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        // Complete legal Edge-to-edge IME window setup
        if (Build.VERSION.SDK_INT >= 30) {
            win.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            win.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            win.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= 29) {
            win.isNavigationBarContrastEnforced = false
        }
        win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        win.decorView.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onCreateInputView(): View = KeyboardView(this, this).also {
        keyboard = it
        it.setPersonalDictionary(personalDict)
        KeyboardColors.accent = preferences.accentColor
        applyKeyboardLayout(it)
        render()
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        KeyboardColors.accent = preferences.accentColor
        policy = EditorPolicy.from(attribute, preferences)
        logEditorForDiagnosis(attribute)
        editor.start(attribute, policy)
        controller.start(policy.editing)
        page = KeyboardPage.LETTERS
        suggestionEngine.activeLanguage = preferences.language
        LanguageDictionaryRegistry.preload(preferences.language, this)
        keyboard?.closePanel() // a panel opened in one field must not follow the user into the next
        keyboard?.let { applyKeyboardLayout(it) }
        render()
    }

    /**
     * Debug builds only: which flags a host app sends decides whether suggestions appear in it. Logs the
     * package and the flag words, never anything typed. Read with `adb logcat -s MB_EDITOR`.
     */
    private fun logEditorForDiagnosis(info: EditorInfo) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Log.d(
            "MB_EDITOR",
            "pkg=${info.packageName} inputType=0x${Integer.toHexString(info.inputType)} " +
                "imeOptions=0x${Integer.toHexString(info.imeOptions)} suggestions=${policy.editing.suggestions} " +
                "autoCorrect=${policy.editing.autoCorrect} learn=${policy.editing.learnWords} kind=${policy.kind}",
        )
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        KeyboardColors.accent = preferences.accentColor
        policy = EditorPolicy.from(info, preferences)
        controller.start(policy.editing)
        suggestionEngine.activeLanguage = preferences.language
        keyboard?.let { applyKeyboardLayout(it) }
        render()
    }

    /**
     * Android draws the IME's hide / switch-keyboard buttons itself, tinted by the IME window's
     * navigation-bar appearance. With a light keyboard they must be dark, or the globe is white on white.
     */
    private fun syncSystemBarAppearance() {
        val win = window?.window ?: return
        val light = !KeyboardColors.isDarkTheme
        if (Build.VERSION.SDK_INT >= 30) {
            win.insetsController?.setSystemBarsAppearance(
                if (light) android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            @Suppress("DEPRECATION")
            win.decorView.systemUiVisibility = win.decorView.systemUiVisibility.let {
                if (light) it or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else it and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        }
    }

    private fun applyKeyboardLayout(kb: KeyboardView) {
        val layoutType = KeyboardLayout.resolve(preferences.layoutOverride, preferences.language)
        kb.setLayout(
            page = page,
            policy = policy,
            numberRow = preferences.numberRow,
            heightScale = preferences.heightScale,
            language = preferences.language,
            wordFlow = preferences.wordFlow,
            tintKeys = preferences.tintKeys,
            layoutOverride = layoutType,
            theme = preferences.theme,
            oneHandedMode = preferences.oneHandedMode,
            spacebarCursor = preferences.spacebarCursor,
            longPressSpace = preferences.longPressSpace,
            keyPopups = preferences.keyPopup,
        )
        syncSystemBarAppearance()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
        newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (editor.selectionChanged(newSelStart, newSelEnd)) {
            keyboard?.dismissPopups()
            controller.cursorMoved()
        }
        render()
    }

    override fun onKey(key: KeySpec) {
        val now = SystemClock.uptimeMillis()
        keyboard?.let { haptics.play(it, preferences.vibration, preferences.hapticStrength) }
        sound.play(preferences.sound, key.action, preferences.soundVolume)

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
            KeyAction.CLIPBOARD -> onClipboardPasteRequested()
        }
        render()
    }

    private fun changePage(next: KeyboardPage) {
        controller.cancelSequence()
        page = next
        keyboard?.let { applyKeyboardLayout(it) }
    }

    override fun onSuggestion(word: String) {
        controller.chooseSuggestion(word)
        keyboard?.let { haptics.play(it, preferences.vibration, preferences.hapticStrength) }
        sound.play(preferences.sound, KeyAction.TEXT, preferences.soundVolume)

        render()
    }

    override fun onWordFlow(word: String, candidates: List<String>) {
        keyboard?.let { haptics.play(it, preferences.vibration, preferences.hapticStrength) }
        sound.play(preferences.sound, KeyAction.TEXT, preferences.soundVolume)
        controller.commitWordFlow(word, candidates)
        render()
    }

    override fun onLanguageToggled() {
        onSpacebarLongPressed()
    }

    override fun onSpacebarCursorMoved(step: Int) {
        val ic = currentInputConnection ?: return
        keyboard?.let { haptics.play(it, preferences.vibration, "LIGHT") }
        sound.play(preferences.sound, KeyAction.SPACE, preferences.soundVolume * 0.6f)

        // Try moving cursor by DPAD key event or selection
        val keyCode = if (step > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        val count = kotlin.math.abs(step)
        for (i in 0 until count) {
            sendDownUpKeyEvents(keyCode)
        }
    }

    override fun onSpacebarLongPressed() {
        keyboard?.dismissPopups()
        keyboard?.showLanguagePanel(preferences.language)
    }

    override fun onLanguageSelected(code: String) {
        keyboard?.closePanel()
        switchLanguage(code)
    }

    private fun switchLanguage(newLang: String) {
        preferences.language = newLang
        suggestionEngine.activeLanguage = newLang
        LanguageDictionaryRegistry.preload(newLang, this)
        keyboard?.let { applyKeyboardLayout(it) }
        render()
        Toast.makeText(this, newLang.uppercase(), Toast.LENGTH_SHORT).show()
    }

    /** Adds whatever was just copied to the history, unless the source flagged it as sensitive. */
    private fun recordPrimaryClip() {
        if (!preferences.clipboardHistory) return
        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip ?: return
        if (clip.itemCount == 0 || clip.description?.hasMimeType("text/*") != true) return
        val sensitive = clip.description.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true
        val text = clip.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        clipboardHistory.record(text, System.currentTimeMillis(), sensitive)
    }

    /**
     * True while the screen is locked. A keyboard can be shown over apps that appear on the lock screen, where
     * whoever holds the phone is not necessarily its owner: the clipboard, the clip history and Settings (which
     * lists learned words) stay out of reach until the device is unlocked.
     */
    private fun deviceLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    override fun onClipboardPanelRequested() {
        if (deviceLocked()) return
        // In a password field the history is never shown over the field (anyone looking could read it);
        // the button pastes what is on the clipboard now, as before.
        if (!preferences.clipboardHistory || policy.sensitive) {
            onClipboardPasteRequested()
            return
        }
        recordPrimaryClip()
        keyboard?.showClipboardPanel(clipboardHistory)
    }

    override fun onClipInsert(text: String) {
        if (deviceLocked()) return
        keyboard?.let { haptics.play(it, preferences.vibration, preferences.hapticStrength) }
        controller.paste(text)
        keyboard?.closePanel()
        render()
    }

    override fun onSettingsRequested() {
        if (deviceLocked()) return
        keyboard?.dismissPopups()
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        requestHideSelf(0)
    }

    override fun onHeightModeChanged(mode: String) {
        preferences.heightMode = mode
        keyboard?.let { applyKeyboardLayout(it) }
    }

    override fun onClipboardPasteRequested() {
        if (deviceLocked()) return
        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0)?.coerceToText(this)?.toString()
            if (!text.isNullOrEmpty()) {
                controller.paste(text)
                render()
            }
        }
    }

    override fun onSwitchKeyboard() {
        controller.cancelSequence()
        keyboard?.dismissPopups()
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    override fun onAlternativesOpened() {
        controller.cancelSequence()
    }

    override fun onSwipeToDelete(wordsCount: Int) {
        keyboard?.let { haptics.play(it, preferences.vibration, "MEDIUM") }
        sound.play(preferences.sound, KeyAction.DELETE, preferences.soundVolume)
        controller.deleteWords(wordsCount)
        render()
    }

    override fun onOneHandedToggled(newMode: String) {
        preferences.oneHandedMode = newMode
        keyboard?.let { applyKeyboardLayout(it) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            return super.onKeyDown(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                controller.backspace()
                render()
                return true
            }
            KeyEvent.KEYCODE_SPACE -> {
                controller.space(SystemClock.uptimeMillis())
                render()
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                controller.enter()
                render()
                return true
            }
            else -> {
                val unicode = event.unicodeChar
                if (unicode > 0 && !Character.isISOControl(unicode)) {
                    controller.text(unicode.toChar().toString())
                    render()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

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
        getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(primaryClipListener)
        keyboard?.dismissPopups()
        keyboard = null
        sound.release()
        LanguageDictionaryRegistry.onDictionaryLoaded = null
        super.onDestroy()
    }

    private fun render() {
        keyboard?.render(
            controller.shift.state,
            policy.editing.forceUppercase,
            controller.suggestions(),
            enterLabel()
        )
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
