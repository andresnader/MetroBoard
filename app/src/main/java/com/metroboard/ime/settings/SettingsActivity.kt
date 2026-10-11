/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.settings

import android.annotation.SuppressLint
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
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
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
    }

    private lateinit var preferences: KeyboardPreferences
    private lateinit var personalDict: LocalPersonalDictionary
    private val colorTileViews = mutableListOf<Pair<Int, View>>()
    private val pivotTabViews = mutableListOf<TextView>()
    private lateinit var lateralPager: PivotHorizontalScrollView
    private lateinit var pivotHeaderScroll: HorizontalScrollView
    private lateinit var accentColorLabel: TextView
    private var currentPageIndex = 0

    private val SECTIONS = listOf(
        "LANGUAGES",
        "TYPING",
        "LAYOUT",
        "APPEARANCE",
        "SOUND & HAPTICS",
        "GESTURES",
        "PRIVACY",
        "ABOUT"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = KeyboardPreferences(this)
        personalDict = LocalPersonalDictionary(this)
        KeyboardColors.accent = preferences.accentColor
        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        KeyboardColors.setTheme(preferences.theme, isSystemDark)

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
            textSize = 13f
            setTextColor(KeyboardColors.muted)
            setPadding(dp(24), dp(16), dp(24), 0)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        root.addView(appTitle)

        // 2. Windows Phone Panoramic Pivot Header
        root.addView(createPivotHeader())

        // 3. Lateral Sliding Pages
        root.addView(createLateralPages(), LinearLayout.LayoutParams(-1, -1))
    }

    private fun createPivotHeader(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(24), dp(8), dp(24), dp(12))
        }
        pivotHeaderScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(container)
        }

        pivotTabViews.clear()
        SECTIONS.forEachIndexed { index, title ->
            val tabView = TextView(this).apply {
                text = title.lowercase()
                setPadding(0, 0, dp(28), 0)
                setOnClickListener { scrollToPage(index) }
            }
            pivotTabViews.add(tabView)
            container.addView(tabView)
        }
        updatePivotHeaderStyles(0)
        return pivotHeaderScroll
    }

    private fun updatePivotHeaderStyles(activeIndex: Int) {
        currentPageIndex = activeIndex
        pivotTabViews.forEachIndexed { index, tabView ->
            val isActive = index == activeIndex
            tabView.textSize = if (isActive) 32f else 24f
            tabView.setTextColor(if (isActive) KeyboardColors.foreground else KeyboardColors.muted)
            tabView.typeface = Typeface.create("sans-serif-light", if (isActive) Typeface.BOLD else Typeface.NORMAL)
        }
        pivotTabViews.getOrNull(activeIndex)?.let { activeTab ->
            pivotHeaderScroll.post {
                val scrollTarget = (activeTab.left - dp(24)).coerceAtLeast(0)
                pivotHeaderScroll.smoothScrollTo(scrollTarget, 0)
            }
        }
    }

    private fun createLateralPages(): View {
        val pagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        pagesContainer.addView(createLanguagesPage())
        pagesContainer.addView(createTypingPage())
        pagesContainer.addView(createLayoutPage())
        pagesContainer.addView(createAppearancePage())
        pagesContainer.addView(createSoundHapticsPage())
        pagesContainer.addView(createGesturesPage())
        pagesContainer.addView(createPrivacyPage())
        pagesContainer.addView(createAboutPage())

        lateralPager = PivotHorizontalScrollView(this).apply {
            pageCount = SECTIONS.size
            addView(pagesContainer)
            onPageSelected = { pageIndex ->
                updatePivotHeaderStyles(pageIndex)
            }
        }
        return lateralPager
    }

    private fun scrollToPage(index: Int) {
        val safeIndex = index.coerceIn(0, SECTIONS.size - 1)
        lateralPager.snapToPage(safeIndex)
    }

    // 1. LANGUAGES
    private fun createLanguagesPage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        content.addView(label("idioma activo", 22f))
        content.addView(createLanguageSelector())

        content.addView(label("distribución de teclas", 22f))
        content.addView(createLayoutOverrideSelector())

        content.addView(label("método de entrada", 22f))
        content.addView(action(R.string.enable_keyboard) {
            try { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
            }
        })
        content.addView(action(R.string.select_keyboard) {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        })

        return makePageScrollView(content)
    }

    // 2. TYPING
    private fun createTypingPage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        content.addView(toggle(R.string.suggestions, preferences.suggestions) { preferences.suggestions = it })
        content.addView(toggle(R.string.autocorrect, preferences.autoCorrect) { preferences.autoCorrect = it })
        content.addView(toggle(R.string.learn_words, preferences.learnWords) { preferences.learnWords = it })
        content.addView(toggle(R.string.autocap, preferences.autoCapitalization) { preferences.autoCapitalization = it })
        content.addView(toggle(R.string.double_space_period, preferences.doubleSpacePeriod) { preferences.doubleSpacePeriod = it })
        content.addView(toggle(R.string.shift_word_casing, preferences.shiftWordCasing) { preferences.shiftWordCasing = it })
        content.addView(label(R.string.shift_word_casing_summary, 12f, KeyboardColors.muted))

        content.addView(action(R.string.view_learned_words) { showLearnedWordsDialog() })

        content.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(16)) })
        content.addView(label(R.string.try_title, 20f))
        val testField = EditText(this).apply {
            hint = getString(R.string.test_hint)
            setTextColor(KeyboardColors.foreground)
            setHintTextColor(KeyboardColors.muted)
            textSize = 16f
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = GradientDrawable().apply {
                setColor(if (KeyboardColors.isDarkTheme) 0xFF1A1A1A.toInt() else 0xFFF0F0F0.toInt())
                setStroke(dp(1), KeyboardColors.accent)
            }
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.postDelayed({
                        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(v, InputMethodManager.SHOW_FORCED)
                    }, 100)
                }
            }
            setOnClickListener {
                requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(this, InputMethodManager.SHOW_FORCED)
            }
        }
        content.addView(testField)

        return makePageScrollView(content)
    }

    // 3. LAYOUT
    private fun createLayoutPage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        content.addView(label(R.string.one_handed_title, 22f))
        content.addView(createOneHandedSelector())

        content.addView(label(R.string.height_scale_title, 22f))
        content.addView(createHeightScaleSelector())

        content.addView(toggle(R.string.show_number_row, preferences.numberRow) { preferences.numberRow = it })
        content.addView(toggle(R.string.key_popup, preferences.keyPopup) { preferences.keyPopup = it })

        return makePageScrollView(content)
    }

    // 4. APPEARANCE
    private fun createAppearancePage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        content.addView(label(R.string.theme_title, 22f))
        content.addView(createThemeSelector())

        val activeName = KeyboardColors.LUMIA_PALETTE.find { it.color == preferences.accentColor }?.name ?: ""
        accentColorLabel = label(R.string.accent_color_title, 22f).apply {
            text = getString(R.string.accent_color_title) + if (activeName.isNotEmpty()) ": $activeName" else ""
        }
        content.addView(accentColorLabel)
        content.addView(createColorPicker())

        content.addView(toggle(R.string.tint_keys, preferences.tintKeys) { preferences.tintKeys = it })

        return makePageScrollView(content)
    }

    // 5. SOUND & HAPTICS
    private fun createSoundHapticsPage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        content.addView(toggle(R.string.sound, preferences.sound) { preferences.sound = it })
        content.addView(label(R.string.sound_volume, 18f))
        content.addView(createSoundVolumeSelector())

        content.addView(toggle(R.string.vibration, preferences.vibration) { preferences.vibration = it })
        content.addView(label(R.string.haptic_strength, 18f))
        content.addView(createHapticStrengthSelector())

        return makePageScrollView(content)
    }

    // 6. GESTURES
    private fun createGesturesPage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        content.addView(toggle(R.string.wordflow, preferences.wordFlow) { preferences.wordFlow = it })
        content.addView(label(R.string.wordflow_summary, 12f, KeyboardColors.muted))

        content.addView(label(R.string.swipe_delete_title, 18f).apply { setPadding(0, dp(12), 0, dp(2)) })
        content.addView(label(R.string.swipe_delete_summary, 12f, KeyboardColors.muted))

        content.addView(toggle(R.string.spacebar_cursor, preferences.spacebarCursor) { preferences.spacebarCursor = it })
        content.addView(label(R.string.spacebar_cursor_summary, 12f, KeyboardColors.muted))

        content.addView(toggle(R.string.long_press_space, preferences.longPressSpace) { preferences.longPressSpace = it })
        content.addView(label(R.string.long_press_space_summary, 12f, KeyboardColors.muted))

        return makePageScrollView(content)
    }

    // 7. PRIVACY
    private fun createPrivacyPage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        content.addView(label(R.string.offline_indicator, 18f, preferences.accentColor))
        content.addView(label(R.string.privacy_note, 13f, KeyboardColors.muted))

        content.addView(action(R.string.import_android_dict) {
            val count = personalDict.importFromSystemUserDictionary(this)
            Toast.makeText(this, getString(R.string.import_android_dict_done, count), Toast.LENGTH_LONG).show()
        })

        content.addView(action(R.string.view_learned_words) { showLearnedWordsDialog() })
        content.addView(action(R.string.clear_learned_words) {
            personalDict.clear()
            Toast.makeText(this, "Diccionario personal borrado", Toast.LENGTH_SHORT).show()
        })
        content.addView(action(R.string.export_settings) { exportSettings() })
        content.addView(action(R.string.import_settings) { showImportDialog() })
        content.addView(action(R.string.reset_settings) { showResetDialog() })

        return makePageScrollView(content)
    }

    // 8. ABOUT
    private fun createAboutPage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        content.addView(label(getString(R.string.about_version, versionName), 24f))
        content.addView(label(R.string.about_legal, 14f, KeyboardColors.muted))
        content.addView(label(R.string.about_licenses, 13f, KeyboardColors.muted))

        // Quick test field in About page for instant verification
        content.addView(label(R.string.try_title, 20f).apply { setPadding(0, dp(16), 0, dp(4)) })
        content.addView(EditText(this).apply {
            hint = getString(R.string.test_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(KeyboardColors.muted)
            setBackgroundColor(Color.parseColor("#222222"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        })

        return makePageScrollView(content)
    }

    // Selectors & Controls
    private fun createLanguageSelector(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(16))
        }
        val languages = listOf("es" to "Español", "en" to "English", "fr" to "Français", "de" to "Deutsch")
        languages.forEach { (code, title) ->
            val isCurrent = preferences.language == code
            val btn = Button(this).apply {
                text = title
                isAllCaps = false
                textSize = 13f
                setTextColor(if (isCurrent) Color.BLACK else KeyboardColors.foreground)
                background = GradientDrawable().apply {
                    setColor(if (isCurrent) preferences.accentColor else KeyboardColors.key)
                    cornerRadius = 0f
                }
                setOnClickListener {
                    preferences.language = code
                    Toast.makeText(this@SettingsActivity, "$title seleccionado", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }
            row.addView(btn, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return row
    }

    private fun createLayoutOverrideSelector(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(16))
        }
        val options = listOf("AUTO" to "Auto", "QWERTY" to "QWERTY", "AZERTY" to "AZERTY", "QWERTZ" to "QWERTZ")
        options.forEach { (code, labelText) ->
            val isCurrent = preferences.layoutOverride == code
            val btn = Button(this).apply {
                text = labelText
                isAllCaps = false
                textSize = 12f
                setTextColor(if (isCurrent) Color.BLACK else KeyboardColors.foreground)
                background = GradientDrawable().apply {
                    setColor(if (isCurrent) preferences.accentColor else KeyboardColors.key)
                    cornerRadius = 0f
                }
                setOnClickListener {
                    preferences.layoutOverride = code
                    Toast.makeText(this@SettingsActivity, "$labelText seleccionado", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }
            row.addView(btn, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return row
    }

    private fun createHeightScaleSelector(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(16))
        }
        val options = listOf("COMPACT" to "Compacto", "NORMAL" to "Normal", "TALL" to "Alto")
        options.forEach { (mode, title) ->
            val isCurrent = preferences.heightMode == mode
            val btn = Button(this).apply {
                text = title
                isAllCaps = false
                textSize = 13f
                setTextColor(if (isCurrent) Color.BLACK else KeyboardColors.foreground)
                background = GradientDrawable().apply {
                    setColor(if (isCurrent) preferences.accentColor else KeyboardColors.key)
                    cornerRadius = 0f
                }
                setOnClickListener {
                    preferences.heightMode = mode
                    recreate()
                }
            }
            row.addView(btn, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return row
    }

    private fun createThemeSelector(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(16))
        }
        val options = listOf("DARK" to "Oscuro", "LIGHT" to "Claro", "SYSTEM" to "Sistema")
        options.forEach { (code, title) ->
            val isCurrent = preferences.theme == code
            val btn = Button(this).apply {
                text = title
                isAllCaps = false
                textSize = 13f
                setTextColor(if (isCurrent) Color.BLACK else KeyboardColors.foreground)
                background = GradientDrawable().apply {
                    setColor(if (isCurrent) preferences.accentColor else KeyboardColors.key)
                    cornerRadius = 0f
                }
                setOnClickListener {
                    preferences.theme = code
                    val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    KeyboardColors.setTheme(code, isSystemDark)
                    Toast.makeText(this@SettingsActivity, "$title seleccionado", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }
            row.addView(btn, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return row
    }

    private fun createOneHandedSelector(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(16))
        }
        val options = listOf("OFF" to "Desactivado", "LEFT" to "Izquierda", "RIGHT" to "Derecha")
        options.forEach { (code, title) ->
            val isCurrent = preferences.oneHandedMode == code
            val btn = Button(this).apply {
                text = title
                isAllCaps = false
                textSize = 12f
                setTextColor(if (isCurrent) Color.BLACK else KeyboardColors.foreground)
                background = GradientDrawable().apply {
                    setColor(if (isCurrent) preferences.accentColor else KeyboardColors.key)
                    cornerRadius = 0f
                }
                setOnClickListener {
                    preferences.oneHandedMode = code
                    Toast.makeText(this@SettingsActivity, "$title seleccionado", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }
            row.addView(btn, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return row
    }

    private fun createSoundVolumeSelector(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(14))
        }
        val volumes = listOf(0.35f to "Bajo", 0.70f to "Medio", 1.0f to "Alto")
        volumes.forEach { (vol, label) ->
            val isCurrent = kotlin.math.abs(preferences.soundVolume - vol) < 0.15f
            val btn = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 13f
                setTextColor(if (isCurrent) Color.BLACK else KeyboardColors.foreground)
                background = GradientDrawable().apply {
                    setColor(if (isCurrent) preferences.accentColor else KeyboardColors.key)
                    cornerRadius = 0f
                }
                setOnClickListener {
                    preferences.soundVolume = vol
                    recreate()
                }
            }
            row.addView(btn, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return row
    }

    private fun createHapticStrengthSelector(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(14))
        }
        val strengths = listOf("LIGHT" to "Suave", "MEDIUM" to "Media", "STRONG" to "Fuerte")
        strengths.forEach { (key, label) ->
            val isCurrent = preferences.hapticStrength.equals(key, ignoreCase = true)
            val btn = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 13f
                setTextColor(if (isCurrent) Color.BLACK else KeyboardColors.foreground)
                background = GradientDrawable().apply {
                    setColor(if (isCurrent) preferences.accentColor else KeyboardColors.key)
                    cornerRadius = 0f
                }
                setOnClickListener {
                    preferences.hapticStrength = key
                    recreate()
                }
            }
            row.addView(btn, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return row
    }

    private fun createColorPicker(): View {
        val grid = GridLayout(this).apply {
            columnCount = 5
            setPadding(0, dp(8), 0, dp(16))
        }
        colorTileViews.clear()

        val tileWidth = (resources.displayMetrics.widthPixels - dp(48)) / 5
        KeyboardColors.LUMIA_PALETTE.forEach { accent ->
            val isSelected = accent.color == preferences.accentColor
            val tile = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(accent.color)
                    if (isSelected) setStroke(dp(3), Color.WHITE)
                    cornerRadius = 0f
                }
                setOnClickListener {
                    preferences.accentColor = accent.color
                    KeyboardColors.accent = accent.color
                    accentColorLabel.text = getString(R.string.accent_color_title) + ": ${accent.name}"
                    updateColorSelection(accent.color)
                }
            }
            colorTileViews.add(accent.color to tile)
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = tileWidth - dp(6)
                height = dp(46)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        return grid
    }

    private fun updateColorSelection(selectedColor: Int) {
        colorTileViews.forEach { (color, view) ->
            val isSelected = color == selectedColor
            (view.background as? GradientDrawable)?.apply {
                if (isSelected) setStroke(dp(3), Color.WHITE)
                else setStroke(0, Color.TRANSPARENT)
            }
        }
    }

    private fun showLearnedWordsDialog() {
        val words = personalDict.getAllWords(preferences.language)
        if (words.isEmpty()) {
            Toast.makeText(this, "No hay palabras aprendidas aún", Toast.LENGTH_SHORT).show()
            return
        }
        val items = words.map { "${it.word} (${it.frequency})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.view_learned_words)
            .setItems(items) { _, which ->
                val selected = words[which]
                AlertDialog.Builder(this)
                    .setTitle("Eliminar palabra")
                    .setMessage("¿Deseas eliminar '${selected.word}' del diccionario?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        personalDict.remove(selected.word, preferences.language)
                        Toast.makeText(this, "Palabra eliminada", Toast.LENGTH_SHORT).show()
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
            recreate()
        } catch (_: Exception) {
            // Deliberately generic: nothing from a rejected file is echoed back or logged.
            Toast.makeText(this, R.string.invalid_json, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResetDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_settings)
            .setMessage("¿Restablecer todos los ajustes a los valores iniciales de Windows Phone 8.1?")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                preferences.resetToDefaults()
                personalDict.clear()
                recreate()
                Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun makePageScrollView(content: View): View {
        return ScrollView(this).apply {
            addView(content)
            layoutParams = LinearLayout.LayoutParams(resources.displayMetrics.widthPixels, LinearLayout.LayoutParams.MATCH_PARENT)
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

    private fun label(resId: Int, size: Float, color: Int = KeyboardColors.foreground) = TextView(this).apply {
        setText(resId); textSize = size; setTextColor(color)
        setPadding(0, dp(12), 0, dp(4))
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private fun label(text: String, size: Float, color: Int = KeyboardColors.foreground) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        setPadding(0, dp(12), 0, dp(4))
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private fun toggle(titleRes: Int, checked: Boolean, onToggle: (Boolean) -> Unit) = Switch(this).apply {
        setText(titleRes); isChecked = checked
        textSize = 15f; setTextColor(KeyboardColors.foreground)
        setPadding(0, dp(8), 0, dp(8))
        setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
    }

    private fun action(titleRes: Int, onClick: () -> Unit) = Button(this).apply {
        setText(titleRes); isAllCaps = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(KeyboardColors.foreground); textSize = 16f
        background = ColorDrawable(Color.TRANSPARENT)
        setPadding(0, dp(12), 0, dp(12))
        setOnClickListener { onClick() }
    }
}

class PivotHorizontalScrollView(context: Context) : HorizontalScrollView(context) {
    var onPageSelected: ((Int) -> Unit)? = null
    var pageCount = 8
    var currentPage = 0
        private set

    private var initialX = 0f
    private var initialY = 0f

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val w = measuredWidth
        if (w > 0) {
            val container = getChildAt(0) as? LinearLayout
            container?.let {
                for (i in 0 until it.childCount) {
                    val child = it.getChildAt(i)
                    if (child.layoutParams.width != w) {
                        child.layoutParams.width = w
                        child.requestLayout()
                    }
                }
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ev.x
                initialY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(ev.x - initialX)
                val dy = kotlin.math.abs(ev.y - initialY)
                if (dx > dy && dx > 25) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val deltaX = ev.x - initialX
                val pageWidth = width.takeIf { it > 0 } ?: 1
                val threshold = pageWidth / 6
                val targetPage = when {
                    deltaX < -threshold -> (currentPage + 1).coerceAtMost(pageCount - 1)
                    deltaX > threshold -> (currentPage - 1).coerceAtLeast(0)
                    else -> currentPage
                }
                snapToPage(targetPage)
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    fun snapToPage(page: Int, smooth: Boolean = true) {
        currentPage = page.coerceIn(0, pageCount - 1)
        if (width > 0) {
            val targetX = currentPage * width
            if (smooth) {
                smoothScrollTo(targetX, 0)
            } else {
                scrollTo(targetX, 0)
            }
            onPageSelected?.invoke(currentPage)
        } else {
            post {
                snapToPage(currentPage, smooth)
            }
        }
    }

    override fun requestChildRectangleOnScreen(
        child: View,
        rectangle: android.graphics.Rect,
        immediate: Boolean
    ): Boolean {
        return false
    }
}
