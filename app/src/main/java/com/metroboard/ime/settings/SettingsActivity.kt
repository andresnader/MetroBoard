package com.metroboard.ime.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
import com.metroboard.ime.ime.WordFlowImeService
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp

class SettingsActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var preferences: KeyboardPreferences
    private val colorTileViews = mutableListOf<Pair<Int, View>>()
    private val pivotTabViews = mutableListOf<TextView>()
    private lateinit var lateralPager: PivotHorizontalScrollView
    private lateinit var pivotHeaderScroll: HorizontalScrollView
    private lateinit var accentColorLabel: TextView
    private var currentPageIndex = 0
    private var screenWidthPx = 1080

    private val PIVOT_TITLES = listOf(
        R.string.pivot_general,
        R.string.pivot_typing,
        R.string.pivot_appearance,
        R.string.pivot_backup,
        R.string.pivot_try
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = KeyboardPreferences(this)
        KeyboardColors.accent = preferences.accentColor
        screenWidthPx = resources.displayMetrics.widthPixels

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(KeyboardColors.background)
            // Android 15+ edge-to-edge
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
        PIVOT_TITLES.forEachIndexed { index, titleRes ->
            val tabView = TextView(this).apply {
                setText(titleRes)
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
            tabView.textSize = if (isActive) 34f else 26f
            tabView.setTextColor(if (isActive) Color.WHITE else KeyboardColors.muted)
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

        pagesContainer.addView(createGeneralPage())
        pagesContainer.addView(createTypingPage())
        pagesContainer.addView(createAppearancePage())
        pagesContainer.addView(createBackupPage())
        pagesContainer.addView(createTestingPage())

        lateralPager = PivotHorizontalScrollView(this).apply {
            pageCount = PIVOT_TITLES.size
            addView(pagesContainer)
            onPageSelected = { pageIndex ->
                updatePivotHeaderStyles(pageIndex)
            }
        }
        return lateralPager
    }

    private fun scrollToPage(index: Int) {
        val safeIndex = index.coerceIn(0, PIVOT_TITLES.size - 1)
        lateralPager.snapToPage(safeIndex)
    }

    private fun createGeneralPage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }
        content.addView(label(R.string.setup_title, 36f))
        content.addView(label(R.string.setup_instructions, 15f))
        status = label(R.string.keyboard_disabled, 14f, preferences.accentColor)
        content.addView(status)

        content.addView(action(R.string.enable_keyboard) {
            try { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
            }
        })
        content.addView(action(R.string.select_keyboard) {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        })

        content.addView(label(R.string.section_feedback, 22f))
        content.addView(toggle(R.string.vibration, preferences.vibration) { preferences.vibration = it })
        content.addView(toggle(R.string.sound, preferences.sound) { preferences.sound = it })
        content.addView(toggle(R.string.wordflow, preferences.wordFlow) { preferences.wordFlow = it })
        content.addView(label(R.string.wordflow_summary, 12f, KeyboardColors.muted))

        return makePageScrollView(content)
    }

    private fun createTypingPage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        content.addView(label(R.string.language_title, 22f))
        content.addView(createLanguageSelector())

        content.addView(label(R.string.section_typing, 22f))
        content.addView(toggle(R.string.suggestions, preferences.suggestions) { preferences.suggestions = it })
        content.addView(toggle(R.string.autocorrect, preferences.autoCorrect) { preferences.autoCorrect = it })
        content.addView(toggle(R.string.autocap, preferences.autoCapitalization) { preferences.autoCapitalization = it })
        content.addView(toggle(R.string.double_space_period, preferences.doubleSpacePeriod) { preferences.doubleSpacePeriod = it })
        content.addView(toggle(R.string.shift_word_casing, preferences.shiftWordCasing) { preferences.shiftWordCasing = it })
        content.addView(label(R.string.shift_word_casing_summary, 12f, KeyboardColors.muted))

        return makePageScrollView(content)
    }

    private fun createAppearancePage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        content.addView(toggle(R.string.show_number_row, preferences.numberRow) { preferences.numberRow = it })

        val activeName = KeyboardColors.LUMIA_PALETTE.find { it.color == preferences.accentColor }?.name ?: ""
        accentColorLabel = label(R.string.accent_color_title, 22f).apply {
            text = getString(R.string.accent_color_title) + if (activeName.isNotEmpty()) ": $activeName" else ""
        }
        content.addView(accentColorLabel)
        content.addView(createColorPicker())
        content.addView(label(R.string.height_scale_title, 22f))
        content.addView(createHeightScaleSelector())

        return makePageScrollView(content)
    }

    private fun createBackupPage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        content.addView(label(R.string.section_tools, 22f))
        content.addView(action(R.string.export_settings) { exportSettings() })
        content.addView(action(R.string.import_settings) { showImportDialog() })
        content.addView(action(R.string.reset_settings) { showResetDialog() })

        return makePageScrollView(content)
    }

    private fun createTestingPage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(24))
        }

        content.addView(label(R.string.try_title, 22f))
        content.addView(testField(R.string.test_hint, InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        content.addView(testField(R.string.test_email, InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        content.addView(testField(R.string.test_password, InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_PASSWORD))
        content.addView(testField(R.string.test_number, InputType.TYPE_CLASS_NUMBER or
            InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED))
        content.addView(label(R.string.privacy_note, 13f, KeyboardColors.muted))
        content.addView(label(R.string.independent_note, 12f, KeyboardColors.muted))

        return makePageScrollView(content)
    }

    private fun makePageScrollView(content: View): View {
        return ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(screenWidthPx, -1)
            addView(content)
        }
    }

    private fun createLanguageSelector(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(14))
        }
        val languages = listOf("es" to getString(R.string.lang_spanish), "en" to getString(R.string.lang_english))
        val buttons = mutableListOf<Button>()

        fun updateStyles(active: String) {
            buttons.forEachIndexed { i, btn ->
                val code = languages[i].first
                val isSelected = code == active
                btn.setTextColor(if (isSelected) Color.BLACK else Color.WHITE)
                btn.setBackgroundColor(if (isSelected) Color.WHITE else KeyboardColors.key)
            }
        }

        languages.forEach { (code, name) ->
            val btn = Button(this).apply {
                text = name
                textSize = 13f
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    preferences.language = code
                    updateStyles(code)
                }
            }
            buttons.add(btn)
            row.addView(btn)
        }
        updateStyles(preferences.language)
        return row
    }

    private fun createColorPicker(): View {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }

        colorTileViews.clear()
        val currentAccent = preferences.accentColor
        val palette = KeyboardColors.LUMIA_PALETTE
        val numColumns = 5

        for (rowIdx in 0 until (palette.size + numColumns - 1) / numColumns) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dp(8))
                }
            }

            for (colIdx in 0 until numColumns) {
                val itemIndex = rowIdx * numColumns + colIdx
                if (itemIndex < palette.size) {
                    val item = palette[itemIndex]
                    val tile = View(this).apply {
                        val size = dp(46)
                        layoutParams = LinearLayout.LayoutParams(0, size, 1f).apply {
                            setMargins(dp(3), 0, dp(3), 0)
                        }
                        background = createTileDrawable(item.color, item.color == currentAccent)
                        contentDescription = item.name
                        setOnClickListener {
                            preferences.accentColor = item.color
                            KeyboardColors.accent = item.color
                            status.setTextColor(item.color)
                            updateColorTileSelections(item.color)
                            accentColorLabel.text = getString(R.string.accent_color_title) + ": " + item.name
                        }
                    }
                    colorTileViews.add(item.color to tile)
                    row.addView(tile)
                } else {
                    val spacer = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                            setMargins(dp(3), 0, dp(3), 0)
                        }
                    }
                    row.addView(spacer)
                }
            }
            grid.addView(row)
        }
        return grid
    }

    private fun createTileDrawable(color: Int, isSelected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            if (isSelected) {
                setStroke(dp(3), Color.WHITE)
            } else {
                setStroke(dp(1), Color.rgb(60, 60, 60))
            }
        }
    }

    private fun updateColorTileSelections(selectedColor: Int) {
        colorTileViews.forEach { (color, view) ->
            view.background = createTileDrawable(color, color == selectedColor)
        }
    }

    private fun createHeightScaleSelector(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(14))
        }
        val currentScale = preferences.heightScale
        val scales = listOf(
            0.85f to getString(R.string.height_small),
            1.00f to getString(R.string.height_normal),
            1.15f to getString(R.string.height_tall)
        )
        val buttons = mutableListOf<Button>()

        fun updateScaleButtonStyles(activeScale: Float) {
            buttons.forEachIndexed { i, btn ->
                val scaleValue = scales[i].first
                val isActive = Math.abs(scaleValue - activeScale) < 0.05f
                btn.setTextColor(if (isActive) Color.BLACK else Color.WHITE)
                btn.setBackgroundColor(if (isActive) Color.WHITE else KeyboardColors.key)
            }
        }

        scales.forEach { (scale, labelText) ->
            val btn = Button(this).apply {
                text = labelText
                textSize = 12f
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    preferences.heightScale = scale
                    updateScaleButtonStyles(scale)
                }
            }
            buttons.add(btn)
            row.addView(btn)
        }
        updateScaleButtonStyles(currentScale)
        return row
    }

    private fun exportSettings() {
        val json = preferences.exportJson()
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("wkb_settings", json))
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun showImportDialog() {
        val input = EditText(this).apply {
            hint = "{\n  \"settings\": {\n  ...\n  }\n}"
            setHintTextColor(KeyboardColors.muted)
            setTextColor(Color.WHITE)
            setBackgroundColor(KeyboardColors.key)
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.import_settings)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    try {
                        preferences.importJson(text)
                        Toast.makeText(this, R.string.settings_imported, Toast.LENGTH_SHORT).show()
                        recreate()
                    } catch (_: Exception) {
                        Toast.makeText(this, R.string.invalid_json, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showResetDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.reset_settings)
            .setMessage(R.string.reset_settings)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                preferences.resetToDefaults()
                KeyboardColors.accent = KeyboardPreferences.DEFAULT_ACCENT_COLOR
                Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show()
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun label(resource: Int, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        setText(resource)
        textSize = size
        setTextColor(color)
        setPadding(0, dp(10), 0, dp(14))
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private fun action(resource: Int, callback: () -> Unit) = Button(this).apply {
        setText(resource)
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = KeyboardColors.keyBackground()
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) }
        setOnClickListener { callback() }
    }

    private fun toggle(resource: Int, enabled: Boolean, callback: (Boolean) -> Unit) = Switch(this).apply {
        setText(resource)
        setTextColor(Color.WHITE)
        isChecked = enabled
        minHeight = dp(52)
        setOnCheckedChangeListener { _, value -> callback(value) }
    }

    private fun testField(hintResource: Int, type: Int) = EditText(this).apply {
        setHint(hintResource)
        inputType = type
        setTextColor(Color.WHITE)
        setHintTextColor(KeyboardColors.muted)
        minHeight = dp(64)
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(InputMethodManager::class.java)
        val id = ComponentName(this, WordFlowImeService::class.java)
        val selected = ComponentName.unflattenFromString(
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        ) == id
        val enabled = manager?.enabledInputMethodList?.any {
            it.packageName == packageName && it.serviceName == WordFlowImeService::class.java.name
        } == true
        status.setTextColor(preferences.accentColor)
        status.setText(when {
            selected -> R.string.keyboard_active
            enabled -> R.string.keyboard_enabled
            else -> R.string.keyboard_disabled
        })
    }
}

class PivotHorizontalScrollView(context: Context) : HorizontalScrollView(context) {
    var onPageSelected: ((Int) -> Unit)? = null
    var pageCount = 5
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
        // Prevent child views (like EditText gaining focus) from disrupting page snapping!
        return false
    }
}
