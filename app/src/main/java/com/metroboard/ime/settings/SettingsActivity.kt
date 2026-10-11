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
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.metroboard.ime.R
import com.metroboard.ime.dictionary.BackupFormat
import com.metroboard.ime.dictionary.LocalPersonalDictionary
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
        personalDict = LocalPersonalDictionary(this)
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

        // 1. App Subtitle
        val appTitle = TextView(this).apply {
            setText(R.string.setup_subtitle)
            textSize = MetroType.HELP
            setTextColor(KeyboardColors.muted)
            setPadding(dp(24), dp(16), dp(24), 0)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        root.addView(appTitle)

        // 2. The circular pivot: section labels on top, pages below
        pageScrolls.clear()
        root.addView(createPivot(), LinearLayout.LayoutParams(-1, 0, 1f))

        restorePosition(root, scrolls)
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

        action(R.string.view_learned_words) { showLearnedWordsDialog() }

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
    private fun createAppearancePage() = page {
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

        toggle(R.string.tint_keys, preferences.tintKeys) { preferences.tintKeys = it }
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
    }

    // 7. PRIVACY
    private fun createPrivacyPage() = page {
        addView(metroLabel(getString(R.string.offline_indicator), MetroType.BODY, preferences.accentColor))
        help(getString(R.string.privacy_note))

        action(R.string.import_android_dict) {
            val count = personalDict.importFromSystemUserDictionary(this@SettingsActivity)
            Toast.makeText(this@SettingsActivity, getString(R.string.import_android_dict_done, count), Toast.LENGTH_LONG).show()
        }
        action(R.string.view_learned_words) { showLearnedWordsDialog() }
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
        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        title(getString(R.string.about_version, versionName))
        help(getString(R.string.about_legal))
        help(getString(R.string.about_licenses))

        // Same field as on the typing page, for instant verification.
        title(getString(R.string.try_title))
        addView(metroTextField(getString(R.string.test_hint)))
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

    private fun showLearnedWordsDialog() {
        val words = personalDict.getAllWords(preferences.language)
        if (words.isEmpty()) {
            Toast.makeText(this, R.string.no_learned_words, Toast.LENGTH_SHORT).show()
            return
        }
        val items = words.map { "${it.word} (${it.frequency})" }.toTypedArray()
        dialog()
            .setTitle(R.string.view_learned_words)
            .setItems(items) { _, which ->
                val selected = words[which]
                dialog()
                    .setTitle(R.string.delete_word_title)
                    .setMessage(getString(R.string.delete_word_message, selected.word))
                    .setPositiveButton(R.string.delete_word) { _, _ ->
                        personalDict.remove(selected.word, preferences.language)
                        Toast.makeText(this, R.string.word_deleted, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

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

    /** Light theme needs dark status/navigation icons; otherwise the clock and battery are white on white. */
    private fun syncSystemBarAppearance() {
        val light = !KeyboardColors.isDarkTheme
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            val bits = if (android.os.Build.VERSION.SDK_INT >= 26)
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            flags = if (light) flags or bits else flags and bits.inv()
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }
}
