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
        const val STATE_PAGE = "settings.page"
        const val STATE_SCROLLS = "settings.scrolls"
    }

    private lateinit var preferences: KeyboardPreferences
    private lateinit var personalDict: LocalPersonalDictionary
    private val pivotTabViews = mutableListOf<TextView>()
    private val pageScrolls = mutableListOf<ScrollView>()
    private lateinit var lateralPager: PivotHorizontalScrollView
    private lateinit var pivotHeaderScroll: HorizontalScrollView
    private var currentPageIndex = 0
    private var animateHeader = true

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
        currentPageIndex = (savedInstanceState?.getInt(STATE_PAGE) ?: 0).coerceIn(0, SECTIONS.size - 1)
        buildUi(savedInstanceState?.getIntArray(STATE_SCROLLS))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, currentPageIndex)
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

        // 2. Windows Phone Panoramic Pivot Header
        root.addView(createPivotHeader())

        // 3. Lateral Sliding Pages
        pageScrolls.clear()
        root.addView(createLateralPages(), LinearLayout.LayoutParams(-1, -1))

        restorePosition(root, scrolls)
    }

    /** Puts the pivot and each page's vertical scroll back once the new views have been laid out. */
    private fun restorePosition(root: View, scrolls: IntArray?) {
        val page = currentPageIndex
        root.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (lateralPager.width == 0) return
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                animateHeader = false
                lateralPager.snapToPage(page, smooth = false)
                animateHeader = true
                scrolls?.forEachIndexed { index, y -> pageScrolls.getOrNull(index)?.scrollTo(0, y) }
            }
        })
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
        animateHeader = false
        updatePivotHeaderStyles(currentPageIndex)
        animateHeader = true
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
        val smooth = animateHeader
        pivotTabViews.getOrNull(activeIndex)?.let { activeTab ->
            pivotHeaderScroll.post {
                val scrollTarget = (activeTab.left - dp(24)).coerceAtLeast(0)
                if (smooth) pivotHeaderScroll.smoothScrollTo(scrollTarget, 0)
                else pivotHeaderScroll.scrollTo(scrollTarget, 0)
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
        title("idioma activo")
        segmented(
            listOf("es" to "Español", "en" to "English", "fr" to "Français", "de" to "Deutsch"),
            preferences.language,
        ) { preferences.language = it }

        title("distribución de teclas")
        segmented(
            listOf("AUTO" to "Auto", "QWERTY" to "QWERTY", "AZERTY" to "AZERTY", "QWERTZ" to "QWERTZ"),
            preferences.layoutOverride,
        ) { preferences.layoutOverride = it }

        title("método de entrada")
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
            listOf("OFF" to "Desactivado", "LEFT" to "Izquierda", "RIGHT" to "Derecha"),
            preferences.oneHandedMode,
        ) { preferences.oneHandedMode = it }

        title(getString(R.string.height_scale_title))
        segmented(
            listOf("COMPACT" to "Compacto", "NORMAL" to "Normal", "TALL" to "Alto"),
            preferences.heightMode,
        ) { preferences.heightMode = it }

        toggle(R.string.show_number_row, preferences.numberRow) { preferences.numberRow = it }
        toggle(R.string.key_popup, preferences.keyPopup) { preferences.keyPopup = it }
    }

    // 4. APPEARANCE
    private fun createAppearancePage() = page {
        title(getString(R.string.theme_title))
        segmented(
            listOf("DARK" to "Oscuro", "LIGHT" to "Claro", "SYSTEM" to "Sistema"),
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
            listOf("0" to "Bajo", "1" to "Medio", "2" to "Alto"),
            volumes.indexOfFirst { kotlin.math.abs(preferences.soundVolume - it) < 0.15f }.toString(),
        ) { preferences.soundVolume = volumes[it.toInt()] }

        toggle(R.string.vibration, preferences.vibration) { preferences.vibration = it }
        title(getString(R.string.haptic_strength))
        segmented(
            listOf("LIGHT" to "Suave", "MEDIUM" to "Media", "STRONG" to "Fuerte"),
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
            Toast.makeText(this@SettingsActivity, "Diccionario personal borrado", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "No hay palabras aprendidas aún", Toast.LENGTH_SHORT).show()
            return
        }
        val items = words.map { "${it.word} (${it.frequency})" }.toTypedArray()
        dialog()
            .setTitle(R.string.view_learned_words)
            .setItems(items) { _, which ->
                val selected = words[which]
                dialog()
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
            refreshUi()
        } catch (_: Exception) {
            // Deliberately generic: nothing from a rejected file is echoed back or logged.
            Toast.makeText(this, R.string.invalid_json, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResetDialog() {
        dialog()
            .setTitle(R.string.reset_settings)
            .setMessage("¿Restablecer todos los ajustes a los valores iniciales de Windows Phone 8.1?")
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
