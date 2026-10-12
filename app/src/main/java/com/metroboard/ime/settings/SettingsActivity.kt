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
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.metroboard.ime.R
import com.metroboard.ime.clipboard.ClipboardHistories
import com.metroboard.ime.dictionary.BackupFormat
import com.metroboard.ime.dictionary.LocalPersonalDictionary
import com.metroboard.ime.ime.EditorPolicy
import com.metroboard.ime.keyboard.KeySpec
import com.metroboard.ime.keyboard.KeyboardLayout
import com.metroboard.ime.keyboard.KeyboardPage
import com.metroboard.ime.keyboard.ShiftState
import com.metroboard.ime.ui.KeyboardView
import com.metroboard.ime.util.Diagnostics
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp

class SettingsActivity : Activity() {
    private companion object {
        const val REQUEST_EXPORT = 4101
        const val REQUEST_IMPORT = 4102
        const val STATE_PAGE = "settings.page"
        const val STATE_SCROLLS = "settings.scrolls"
    }

    private lateinit var preferences: KeyboardPreferences
    private lateinit var personalDict: LocalPersonalDictionary
    private val pageScrolls = mutableListOf<ScrollView>()
    private lateinit var pivot: MetroPivot
    private var currentPageIndex = 0

    /** Pivot labels, in page order. Resources, so they follow the system language. */
    private val sectionTitles = listOf(
        R.string.tab_languages,
        R.string.tab_typing,
        R.string.tab_layout,
        R.string.tab_appearance,
        R.string.tab_sound,
        R.string.tab_gestures,
        R.string.tab_privacy,
        R.string.tab_about,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = KeyboardPreferences(this)
        personalDict = LocalPersonalDictionary.shared(this)
        currentPageIndex = (savedInstanceState?.getInt(STATE_PAGE) ?: 0).coerceIn(0, sectionTitles.size - 1)
        buildUi(savedInstanceState?.getIntArray(STATE_SCROLLS))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, if (::pivot.isInitialized) pivot.currentPage else currentPageIndex)
        outState.putIntArray(STATE_SCROLLS, pageScrolls.map { it.scrollY }.toIntArray())
    }

    /**
     * Re-reads the preferences and rebuilds every page in place. Used instead of [recreate] so a change that
     * restyles the whole screen (theme, accent, reset, import) keeps the user on the same pivot section and at
     * the same scroll position.
     */
    private fun refreshUi() {
        buildUi(pageScrolls.map { it.scrollY }.toIntArray())
    }

    private fun buildUi(scrolls: IntArray?) {
        KeyboardColors.accent = preferences.accentColor
        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        KeyboardColors.setTheme(preferences.theme, isSystemDark)

        // The AppTheme window is dark-only; without this the bars and the window behind the content
        // stay dark (and flash) while the page itself is light.
        window.setBackgroundDrawable(ColorDrawable(KeyboardColors.background))
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = KeyboardColors.background
            window.navigationBarColor = KeyboardColors.background
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(KeyboardColors.background)
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
                insets
            }
        }
        setContentView(root)
        syncSystemBarAppearance()

        // 1. App title and version; tapping the version goes to "what's new" on the last page
        root.addView(createAppHeader())

        // 2. The circular pivot: section labels on top, pages below
        pageScrolls.clear()
        root.addView(createPivot(), LinearLayout.LayoutParams(-1, 0, 1f))

        restorePosition(root, scrolls)
    }

    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

    /** The app's name with its version underneath, like the headers of other Metro apps. */
    private fun createAppHeader(): View {
        val whatsNew = getString(R.string.changelog_title)
        val line = "${getString(R.string.version_label, versionName())} · $whatsNew"
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), 0)
            isClickable = true
            contentDescription = "${getString(R.string.app_name)}, $line"
            setOnClickListener { pivot.goTo(sectionTitles.size - 1) }
            addView(TextView(this@SettingsActivity).apply {
                text = getString(R.string.app_name)
                textSize = MetroType.APP_TITLE
                setTextColor(KeyboardColors.foreground)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = android.text.SpannableString(line).apply {
                    setSpan(
                        android.text.style.ForegroundColorSpan(KeyboardColors.accent),
                        line.length - whatsNew.length, line.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                textSize = MetroType.HELP
                setTextColor(KeyboardColors.muted)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            })
        }
    }

    /** Puts the pivot and each page's vertical scroll back once the new views have been laid out. */
    private fun restorePosition(root: View, scrolls: IntArray?) {
        root.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (pivot.width == 0) return
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                scrolls?.forEachIndexed { index, y -> pageScrolls.getOrNull(index)?.scrollTo(0, y) }
            }
        })
    }

    private fun createPivot(): View {
        val pages = listOf(
            createLanguagesPage(),
            createTypingPage(),
            createLayoutPage(),
            createAppearancePage(),
            createSoundHapticsPage(),
            createGesturesPage(),
            createPrivacyPage(),
            createAboutPage(),
        )
        // Metro headers are lower case; the language's own rules (Turkish dotless i) decide how.
        val locale = resources.configuration.locales[0]
        val titles = sectionTitles.map { getString(it).lowercase(locale) }
        pivot = MetroPivot(this, titles, pages) { currentPageIndex = it }
        pivot.showPage(currentPageIndex)
        return pivot
    }

    // Pages ---------------------------------------------------------------------------------------------

    private fun page(build: LinearLayout.() -> Unit): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }
        content.build()
        return makePageScrollView(content)
    }

    private fun LinearLayout.title(text: CharSequence) = addView(metroLabel(text, MetroType.TITLE))
    private fun LinearLayout.help(text: CharSequence) = addView(metroLabel(text, MetroType.HELP, KeyboardColors.muted))
    private fun LinearLayout.toggle(titleRes: Int, checked: Boolean, onToggle: (Boolean) -> Unit) =
        addView(metroToggle(getString(titleRes), checked, onToggle))
    private fun LinearLayout.action(titleRes: Int, onClick: () -> Unit) =
        addView(
            metroButton(getString(titleRes), onClick),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) },
        )
    private fun LinearLayout.segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) =
        addView(metroSegmented(options, selected, onSelect))

    // 1. LANGUAGES
    private fun createLanguagesPage() = page {
        title(getString(R.string.language_title))
        segmented(
            listOf("es" to "Español", "en" to "English", "fr" to "Français", "de" to "Deutsch"),
            preferences.language,
        ) { preferences.language = it }

        title(getString(R.string.layout_title))
        segmented(
            listOf("AUTO" to getString(R.string.layout_auto_short), "QWERTY" to "QWERTY", "AZERTY" to "AZERTY", "QWERTZ" to "QWERTZ"),
            preferences.layoutOverride,
        ) { preferences.layoutOverride = it }

        title(getString(R.string.input_method_title))
        action(R.string.enable_keyboard) {
            try { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            catch (_: ActivityNotFoundException) {
                Toast.makeText(this@SettingsActivity, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
            }
        }
        action(R.string.select_keyboard) {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }

    // 2. TYPING
    private fun createTypingPage() = page {
        toggle(R.string.suggestions, preferences.suggestions) { preferences.suggestions = it }
        toggle(R.string.autocorrect, preferences.autoCorrect) { preferences.autoCorrect = it }
        toggle(R.string.learn_words, preferences.learnWords) { preferences.learnWords = it }
        toggle(R.string.autocap, preferences.autoCapitalization) { preferences.autoCapitalization = it }
        toggle(R.string.double_space_period, preferences.doubleSpacePeriod) { preferences.doubleSpacePeriod = it }
        toggle(R.string.shift_word_casing, preferences.shiftWordCasing) { preferences.shiftWordCasing = it }
        help(getString(R.string.shift_word_casing_summary))

        action(R.string.personal_dictionary_title) { startActivity(Intent(this@SettingsActivity, PersonalDictionaryActivity::class.java)) }

        title(getString(R.string.try_title))
        addView(metroTextField(getString(R.string.test_hint)))
    }

    // 3. LAYOUT
    private fun createLayoutPage() = page {
        title(getString(R.string.one_handed_title))
        segmented(
            listOf(
                "OFF" to getString(R.string.one_handed_off),
                "LEFT" to getString(R.string.one_handed_left),
                "RIGHT" to getString(R.string.one_handed_right),
            ),
            preferences.oneHandedMode,
        ) { preferences.oneHandedMode = it }

        title(getString(R.string.height_scale_title))
        segmented(
            listOf(
                "COMPACT" to getString(R.string.height_small),
                "NORMAL" to getString(R.string.height_normal),
                "TALL" to getString(R.string.height_tall),
            ),
            preferences.heightMode,
        ) { preferences.heightMode = it }

        toggle(R.string.show_number_row, preferences.numberRow) { preferences.numberRow = it }
        toggle(R.string.key_popup, preferences.keyPopup) { preferences.keyPopup = it }
    }

    // 4. APPEARANCE
    /**
     * The appearance controls with the real keyboard pinned underneath, so every change (theme, accent, key tint)
     * shows on the keyboard as it is made. The preview is for looking only: it does not type.
     */
    private fun createAppearancePage(): View {
        val controls = page {
            title(getString(R.string.theme_title))
            segmented(
                listOf(
                    "DARK" to getString(R.string.theme_dark),
                    "LIGHT" to getString(R.string.theme_light),
                    "SYSTEM" to getString(R.string.theme_system),
                ),
                preferences.theme,
            ) {
                preferences.theme = it
                refreshUi()
            }

            val activeName = KeyboardColors.LUMIA_PALETTE.find { it.color == preferences.accentColor }?.name ?: ""
            title(getString(R.string.accent_color_title) + if (activeName.isNotEmpty()) ": $activeName" else "")
            addView(createColorPicker())

            toggle(R.string.tint_keys, preferences.tintKeys) {
                preferences.tintKeys = it
                keyboardPreview?.let(::applyPreview)
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(createKeyboardPreview(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private var keyboardPreview: KeyboardView? = null

    private fun createKeyboardPreview(): View {
        val keyboard = KeyboardView(this, object : KeyboardView.Listener {
            override fun onKey(key: KeySpec) = Unit
            override fun onSuggestion(word: String) = Unit
            override fun onSwitchKeyboard() = Unit
            override fun onAlternativesOpened() = Unit
            override fun onWordFlow(word: String, candidates: List<String>) = Unit
            override fun onLanguageToggled() = Unit
            override fun onSpacebarCursorMoved(step: Int) = Unit
            override fun onSpacebarLongPressed() = Unit
            override fun onClipboardPasteRequested() = Unit
        })
        // The Settings window already pads for the system bars; the keyboard's own inset padding would double it.
        keyboard.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(dp(3), 0, dp(3), dp(4))
            insets
        }
        keyboardPreview = keyboard
        applyPreview(keyboard)
        // The frame takes every touch, so the preview is for looking at, not typing on. (A transparent overlay View
        // would be wrong here: a MATCH_PARENT View under a wrapping parent grows to the whole screen.)
        return object : FrameLayout(this) {
            override fun onInterceptTouchEvent(ev: android.view.MotionEvent) = true
        }.apply {
            isClickable = true
            addView(keyboard, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun applyPreview(keyboard: KeyboardView) {
        val policy = EditorPolicy.from(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, preferences)
        keyboard.setLayout(
            page = KeyboardPage.LETTERS,
            policy = policy,
            numberRow = preferences.numberRow,
            heightScale = preferences.heightScale,
            language = preferences.language,
            wordFlow = preferences.wordFlow,
            tintKeys = preferences.tintKeys,
            layoutOverride = KeyboardLayout.resolve(preferences.layoutOverride, preferences.language),
            theme = preferences.theme,
            oneHandedMode = preferences.oneHandedMode,
            spacebarCursor = preferences.spacebarCursor,
            longPressSpace = preferences.longPressSpace,
            keyPopups = preferences.keyPopup,
        )
        keyboard.render(ShiftState.LOWERCASE, false, getString(R.string.preview_suggestions).split("|"), "")
    }

    // 5. SOUND & HAPTICS
    private fun createSoundHapticsPage() = page {
        toggle(R.string.sound, preferences.sound) { preferences.sound = it }
        title(getString(R.string.sound_volume))
        val volumes = listOf(0.35f, 0.70f, 1.0f)
        segmented(
            listOf(
                "0" to getString(R.string.volume_low),
                "1" to getString(R.string.volume_medium),
                "2" to getString(R.string.volume_high),
            ),
            volumes.indexOfFirst { kotlin.math.abs(preferences.soundVolume - it) < 0.15f }.toString(),
        ) { preferences.soundVolume = volumes[it.toInt()] }

        toggle(R.string.vibration, preferences.vibration) { preferences.vibration = it }
        title(getString(R.string.haptic_strength))
        segmented(
            listOf(
                "LIGHT" to getString(R.string.haptic_light),
                "MEDIUM" to getString(R.string.haptic_medium),
                "STRONG" to getString(R.string.haptic_strong),
            ),
            preferences.hapticStrength.uppercase(),
        ) { preferences.hapticStrength = it }
    }

    // 6. GESTURES
    private fun createGesturesPage() = page {
        toggle(R.string.wordflow, preferences.wordFlow) { preferences.wordFlow = it }
        help(getString(R.string.wordflow_summary))

        title(getString(R.string.swipe_delete_title))
        help(getString(R.string.swipe_delete_summary))

        toggle(R.string.spacebar_cursor, preferences.spacebarCursor) { preferences.spacebarCursor = it }
        help(getString(R.string.spacebar_cursor_summary))

        toggle(R.string.long_press_space, preferences.longPressSpace) { preferences.longPressSpace = it }
        help(getString(R.string.long_press_space_summary))

        title(getString(R.string.long_press_delay_title))
        segmented(
            listOf(
                "SHORT" to getString(R.string.delay_short), "NORMAL" to getString(R.string.delay_normal),
                "LONG" to getString(R.string.delay_long), "VERY_LONG" to getString(R.string.delay_very_long),
            ),
            preferences.longPressDelay,
        ) { preferences.longPressDelay = it }
        help(getString(R.string.long_press_delay_summary))

        title(getString(R.string.backspace_delay_title))
        segmented(
            listOf(
                "SHORT" to getString(R.string.delay_short), "NORMAL" to getString(R.string.delay_normal),
                "LONG" to getString(R.string.delay_long),
            ),
            preferences.backspaceDelay,
        ) { preferences.backspaceDelay = it }
        title(getString(R.string.backspace_speed_title))
        segmented(
            listOf(
                "SLOW" to getString(R.string.speed_slow), "NORMAL" to getString(R.string.delay_normal),
                "FAST" to getString(R.string.speed_fast),
            ),
            preferences.backspaceSpeed,
        ) { preferences.backspaceSpeed = it }
        help(getString(R.string.backspace_summary))
    }

    // 7. PRIVACY
    private fun createPrivacyPage() = page {
        addView(metroLabel(getString(R.string.offline_indicator), MetroType.BODY, preferences.accentColor))
        help(getString(R.string.privacy_note))

        toggle(R.string.clipboard_history, preferences.clipboardHistory) {
            preferences.clipboardHistory = it
            // Turning it off also forgets what was kept, pinned clips included: off must mean nothing is stored.
            if (!it) ClipboardHistories.shared(this@SettingsActivity).clearAll()
        }
        help(getString(R.string.clipboard_history_summary))
        action(R.string.clipboard_manage) { startActivity(Intent(this@SettingsActivity, ClipboardManagerActivity::class.java)) }
        action(R.string.clear_clipboard) {
            ClipboardHistories.shared(this@SettingsActivity).clearAll()
            Toast.makeText(this@SettingsActivity, R.string.clipboard_cleared, Toast.LENGTH_SHORT).show()
        }


        action(R.string.import_android_dict) {
            val count = personalDict.importFromSystemUserDictionary(this@SettingsActivity)
            Toast.makeText(this@SettingsActivity, getString(R.string.import_android_dict_done, count), Toast.LENGTH_LONG).show()
        }
        action(R.string.personal_dictionary_title) { startActivity(Intent(this@SettingsActivity, PersonalDictionaryActivity::class.java)) }
        action(R.string.clear_learned_words) {
            personalDict.clear()
            Toast.makeText(this@SettingsActivity, R.string.dictionary_cleared, Toast.LENGTH_SHORT).show()
        }
        action(R.string.export_settings) { exportSettings() }
        action(R.string.import_settings) { showImportDialog() }
        action(R.string.reset_settings) { showResetDialog() }
    }

    // 8. ABOUT
    private fun createAboutPage() = page {
        // Who made it. The address is tappable and opens the mail app.
        addView(metroLabel(getString(R.string.about_credit), MetroType.BODY).apply {
            android.text.util.Linkify.addLinks(this, android.text.util.Linkify.EMAIL_ADDRESSES)
            setLinkTextColor(KeyboardColors.accent)
        })
        title(getString(R.string.changelog_title))
        changelog(R.string.version_040, R.array.changelog_0_4_0)
        changelog(R.string.version_030, R.array.changelog_0_3_0)
        changelog(R.string.version_020, R.array.changelog_0_2_0)
        changelog(R.string.version_010, R.array.changelog_0_1_0)

        help(getString(R.string.about_legal))
        help(getString(R.string.about_licenses))

        // Support: lets a release build report why it feels slow on this particular phone, without adb.
        title(getString(R.string.diagnostics_title))
        help(getString(R.string.diagnostics_help))
        toggle(R.string.diagnostics_toggle, preferences.diagnostics) {
            preferences.diagnostics = it
            Diagnostics.configure(this@SettingsActivity, it)
        }
        action(R.string.diagnostics_copy) {
            val report = Diagnostics.log.report(versionName(), Diagnostics.deviceInfo())
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("MetroBoard diagnostics", report))
            Toast.makeText(this@SettingsActivity, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
        }
        action(R.string.diagnostics_clear) {
            Diagnostics.log.clear()
            Toast.makeText(this@SettingsActivity, R.string.diagnostics_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    /** One version heading in the accent colour, then its notes as a plain list. */
    private fun LinearLayout.changelog(versionRes: Int, notesRes: Int) {
        addView(metroLabel(getString(versionRes), MetroType.BODY, KeyboardColors.accent))
        resources.getStringArray(notesRes).forEach {
            addView(metroLabel("–  $it", MetroType.LIST, KeyboardColors.foreground).apply { setPadding(0, dp(3), 0, dp(3)) })
        }
    }

    private fun createColorPicker(): View {
        val grid = GridLayout(this).apply {
            columnCount = 5
            setPadding(0, dp(8), 0, dp(16))
        }

        val tileWidth = (resources.displayMetrics.widthPixels - dp(48)) / 5
        KeyboardColors.LUMIA_PALETTE.forEach { accent ->
            val chosen = accent.color == preferences.accentColor
            val tile = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(accent.color)
                    if (chosen) setStroke(dp(3), KeyboardColors.foreground)
                    cornerRadius = 0f
                }
                contentDescription = accent.name
                isSelected = chosen
                setOnClickListener {
                    preferences.accentColor = accent.color
                    refreshUi()
                }
            }
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = tileWidth - dp(6)
                height = dp(46)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        return grid
    }

    // Dialogs and backup -------------------------------------------------------------------------------

    /** Dialogs follow the keyboard theme, not the system one, so they match the screen they sit on. */
    private fun dialog() = AlertDialog.Builder(
        this,
        if (KeyboardColors.isDarkTheme) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert,
    )

    // Backup goes through the Storage Access Framework: the user picks the file, the app needs no
    // storage permission and nothing is placed on the system clipboard.
    private fun exportSettings() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "metroboard-backup.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun showImportDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQUEST_EXPORT -> writeBackup(uri)
            REQUEST_IMPORT -> readBackup(uri)
        }
    }

    private fun writeBackup(uri: android.net.Uri) {
        try {
            val json = BackupFormat.write(preferences.exportSettingsMap(), personalDict.allWords())
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                ?: throw java.io.IOException("no output stream")
            Toast.makeText(this, R.string.backup_saved, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun readBackup(uri: android.net.Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { BackupFormat.readBounded(it) }
                ?: throw java.io.IOException("no input stream")
            val backup = BackupFormat.parse(text)
            val settings = preferences.applySettings(backup.settings)
            val words = personalDict.importWords(backup.words)
            Toast.makeText(this, getString(R.string.backup_imported, settings, words), Toast.LENGTH_SHORT).show()
            refreshUi()
        } catch (_: Exception) {
            // Deliberately generic: nothing from a rejected file is echoed back or logged.
            Toast.makeText(this, R.string.invalid_json, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResetDialog() {
        dialog()
            .setTitle(R.string.reset_settings)
            .setMessage(R.string.reset_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                preferences.resetToDefaults()
                personalDict.clear()
                refreshUi()
                Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun makePageScrollView(content: View): View {
        return ScrollView(this).apply {
            pageScrolls += this
            addView(content)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
    }
}
