/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.metroboard.ime.R
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp

/**
 * The only controls the Settings screen uses. Every control reads [KeyboardColors] at build time, so the
 * whole screen follows the keyboard theme and accent as a unit, and every control has the same height,
 * type size and flat Metro look. Nothing here is a stock Material widget.
 */
internal object MetroType {
    const val APP_TITLE = 26f
    const val PIVOT = 30f
    const val LIST = 15f
    const val TITLE = 22f
    const val BODY = 16f
    const val SEGMENT = 15f
    const val HELP = 13f
}

private val CONTROL_HEIGHT_DP = 48

internal fun Context.metroLabel(text: CharSequence, size: Float, color: Int = KeyboardColors.foreground) =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        setPadding(0, dp(12), 0, dp(4))
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

/** One row of mutually exclusive options. Updates itself on tap; the caller only stores the choice. */
internal fun Context.metroSegmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, dp(16))
    }
    val segments = mutableListOf<Pair<String, TextView>>()
    fun paint(current: String) {
        segments.forEach { (key, view) ->
            val on = key == current
            val fill = if (on) KeyboardColors.accent else KeyboardColors.key
            view.setBackgroundColor(fill)
            view.setTextColor(if (on) KeyboardColors.onColor(fill) else KeyboardColors.foreground)
            view.isSelected = on
        }
    }
    options.forEach { (key, title) ->
        val view = TextView(this).apply {
            text = title
            gravity = Gravity.CENTER
            maxLines = 1
            isClickable = true
            isFocusable = true
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            // A fixed 15 sp, shrinking only when a long word (Desactivado, Deutsch) would not fit.
            setAutoSizeTextTypeUniformWithConfiguration(11, MetroType.SEGMENT.toInt(), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener {
                paint(key)
                onSelect(key)
            }
        }
        segments += key to view
        row.addView(view, LinearLayout.LayoutParams(0, dp(CONTROL_HEIGHT_DP), 1f).apply {
            setMargins(dp(2), 0, dp(2), 0)
        })
    }
    paint(selected)
    return row
}

/** A rectangular Windows Phone style switch: outlined track, accent fill when on, square thumb. */
internal class MetroSwitchView(context: Context) : View(context) {
    var on: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = context.dp(2).toFloat()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(54), context.dp(26))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val inset = border / 2
        if (on) {
            paint.style = Paint.Style.FILL
            paint.color = KeyboardColors.accent
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = border
        paint.color = if (on) KeyboardColors.accent else KeyboardColors.foreground
        canvas.drawRect(inset, inset, w - inset, h - inset, paint)

        val thumbW = context.dp(12).toFloat()
        val gap = border + context.dp(3)
        paint.style = Paint.Style.FILL
        paint.color = if (on) KeyboardColors.onColor(KeyboardColors.accent) else KeyboardColors.foreground
        val left = if (on) w - gap - thumbW else gap
        canvas.drawRect(left, gap, left + thumbW, h - gap, paint)
    }
}

/** Title on the left, [MetroSwitchView] on the right; the whole row is the touch target. */
internal fun Context.metroToggle(title: CharSequence, checked: Boolean, onToggle: (Boolean) -> Unit): View {
    val switch = MetroSwitchView(this).apply { on = checked }
    val text = TextView(this).apply {
        this.text = title
        textSize = MetroType.BODY
        setTextColor(KeyboardColors.foreground)
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(CONTROL_HEIGHT_DP)
        isClickable = true
        isFocusable = true
        contentDescription = title
    }
    row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(12) })
    row.addView(switch)

    fun describe() {
        if (Build.VERSION.SDK_INT >= 30) {
            row.stateDescription = getString(if (switch.on) R.string.state_on else R.string.state_off)
        }
    }
    describe()
    row.accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = "android.widget.Switch"
            info.isCheckable = true
            info.isChecked = switch.on
        }
    }
    row.setOnClickListener {
        switch.on = !switch.on
        describe()
        row.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
        onToggle(switch.on)
    }
    return row
}

/** Outlined Metro button; fills with the accent while pressed. */
internal fun Context.metroButton(title: CharSequence, onClick: () -> Unit): View = Button(this).apply {
    text = title
    isAllCaps = false
    textSize = MetroType.BODY
    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    gravity = Gravity.CENTER
    stateListAnimator = null
    minHeight = dp(CONTROL_HEIGHT_DP)
    minimumHeight = dp(CONTROL_HEIGHT_DP)
    setPadding(dp(12), 0, dp(12), 0)
    setTextColor(
        ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(KeyboardColors.onColor(KeyboardColors.accent), KeyboardColors.foreground),
        ),
    )
    background = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            GradientDrawable().apply { setColor(KeyboardColors.accent); setStroke(dp(2), KeyboardColors.accent) },
        )
        addState(
            intArrayOf(),
            GradientDrawable().apply { setColor(0); setStroke(dp(2), KeyboardColors.foreground) },
        )
    }
    setOnClickListener { onClick() }
}

/** The "try it" box. One implementation so every page that offers it looks and behaves the same. */
internal fun Context.metroTextField(hintText: CharSequence): EditText = EditText(this).apply {
    hint = hintText
    setTextColor(KeyboardColors.foreground)
    setHintTextColor(KeyboardColors.muted)
    textSize = MetroType.BODY
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    isFocusable = true
    isFocusableInTouchMode = true
    setPadding(dp(12), dp(12), dp(12), dp(12))
    background = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_focused),
            GradientDrawable().apply { setColor(KeyboardColors.key); setStroke(dp(2), KeyboardColors.accent) },
        )
        addState(
            intArrayOf(),
            GradientDrawable().apply { setColor(KeyboardColors.key); setStroke(dp(2), KeyboardColors.muted) },
        )
    }
    setOnClickListener {
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.showSoftInput(this, InputMethodManager.SHOW_FORCED)
    }
}

/** Light theme needs dark status/navigation icons; otherwise the clock and battery are white on white. */
internal fun android.app.Activity.syncSystemBarAppearance() {
    val light = !KeyboardColors.isDarkTheme
    if (Build.VERSION.SDK_INT >= 30) {
        val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
    } else {
        @Suppress("DEPRECATION")
        var flags = window.decorView.systemUiVisibility
        val bits = if (Build.VERSION.SDK_INT >= 26)
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        flags = if (light) flags or bits else flags and bits.inv()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = flags
    }
}

/**
 * The window every full-screen Settings screen shares: theme and accent from the preferences, system bars that
 * match, insets, and an empty vertical root to fill. Used by the personal dictionary and clipboard screens.
 */
internal fun android.app.Activity.metroScaffold(preferences: com.metroboard.ime.settings.KeyboardPreferences): LinearLayout {
    KeyboardColors.accent = preferences.accentColor
    val systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    KeyboardColors.setTheme(preferences.theme, systemDark)
    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(KeyboardColors.background))
    @Suppress("DEPRECATION")
    run {
        window.statusBarColor = KeyboardColors.background
        window.navigationBarColor = KeyboardColors.background
    }
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(KeyboardColors.background)
        setPadding(dp(16), 0, dp(16), 0)
        setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(
                dp(16) + insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                dp(16) + insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
            )
            insets
        }
    }
    setContentView(root)
    syncSystemBarAppearance()
    return root
}

/** The title row of a full-screen Settings screen: a back arrow and the title. */
internal fun Context.metroBackHeader(title: CharSequence, backDescription: CharSequence, onBack: () -> Unit): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(android.widget.ImageView(context).apply {
            setImageResource(R.drawable.ic_back)
            contentDescription = backDescription
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(KeyboardColors.foreground)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(metroLabel(title, MetroType.TITLE).apply { setPadding(dp(4), 0, 0, 0) })
    }
