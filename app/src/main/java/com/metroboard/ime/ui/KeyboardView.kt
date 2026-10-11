/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.metroboard.ime.R
import com.metroboard.ime.gestures.GesturePoint
import com.metroboard.ime.gestures.TouchInputListener
import com.metroboard.ime.gestures.TouchInputStateMachine
import com.metroboard.ime.gestures.TouchInteractionState
import com.metroboard.ime.gestures.WordFlowShapeDecoder
import com.metroboard.ime.ime.EditorPolicy
import com.metroboard.ime.ime.FieldKind
import com.metroboard.ime.keyboard.*
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp

@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: Listener) : LinearLayout(context) {
    interface Listener {
        fun onKey(key: KeySpec)
        fun onSuggestion(word: String)
        fun onSwitchKeyboard()
        fun onAlternativesOpened()
        fun onWordFlow(word: String, candidates: List<String>)
        fun onLanguageToggled()
        fun onSpacebarCursorMoved(step: Int)
        fun onSpacebarLongPressed()
        fun onDoubleTapWordDetected()
        fun onClipboardPasteRequested()
        fun onSwipeToDelete(wordsCount: Int) = Unit
        fun onOneHandedToggled(newMode: String) = Unit
    }

    private val suggestionButtons = mutableListOf<TextView>()
    private val keys = mutableListOf<Pair<KeySpec, KeyButton>>()
    private val suggestionBar = LinearLayout(context)
    private val keyRows = LinearLayout(context)
    private val keyboardRowContainer = LinearLayout(context)
    private val leftDockBar = LinearLayout(context)
    private val rightDockBar = LinearLayout(context)
    private val clipButton = TextView(context)
    private var currentOneHandedMode = "OFF"
    private var currentTheme = "DARK"
    private val alternatives = AlternativesPopup()
    private var emojiKeyboard: EmojiKeyboardView? = null
    private val contentContainer = FrameLayout(context)

    private var uppercase = false
    private val decoder = WordFlowShapeDecoder(context = context)
    private var wordFlowEnabled = true
    private var currentPage = KeyboardPage.LETTERS
    private var currentLanguage = "es"
    private var currentLayoutType = KeyboardLayoutType.QWERTY
    private var hasNumberRow = false
    private var isTintKeysEnabled = false

    // Visual gesture trail
    private val trailPoints = mutableListOf<GesturePoint>()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trailPath = Path()

    // Touch State Machine
    private val touchListener = object : TouchInputListener {
        override fun onKeyTapped(key: KeySpec) {
            listener.onKey(key)
        }

        override fun onKeyLongPressed(key: KeySpec, anchorX: Float, anchorY: Float) {
            val anchorButton = findButtonForKey(key) ?: this@KeyboardView
            listener.onAlternativesOpened()
            val values = if (uppercase) key.alternatives.map { it.uppercase(java.util.Locale.ROOT) }
                else key.alternatives
            alternatives.show(anchorButton, values) { alt ->
                listener.onKey(KeySpec(alt))
            }
        }

        override fun onSwipeStarted(startPoint: GesturePoint) {
            trailPoints.clear()
            trailPoints.add(startPoint)
            invalidate()
        }

        override fun onSwipeUpdated(point: GesturePoint) {
            trailPoints.add(point)
            invalidate()
        }

        override fun onSwipeEnded(path: List<GesturePoint>) {
            if (path.size >= 3 && keyRows.width > 0) {
                val firstAlphaIndex = if (hasNumberRow) 1 else 0
                val lastAlphaIndex = firstAlphaIndex + 2
                val firstAlphaRow = keyRows.getChildAt(firstAlphaIndex)
                val lastAlphaRow = keyRows.getChildAt(lastAlphaIndex)
                var krLeft = 0
                var krTop = 0
                var p: View? = keyRows
                while (p != null && p != this@KeyboardView) {
                    krLeft += p.left
                    krTop += p.top
                    p = p.parent as? View
                }
                val alphaTop = krTop + (firstAlphaRow?.top ?: 0)
                val alphaBottom = krTop + (lastAlphaRow?.bottom ?: keyRows.height)
                val alphaHeight = (alphaBottom - alphaTop).coerceAtLeast(1)

                val normPoints = path.map { pt ->
                    val nx = ((pt.x - krLeft) / keyRows.width.toFloat()).coerceIn(0f, 1f)
                    val ny = ((pt.y - alphaTop) / alphaHeight.toFloat()).coerceIn(0f, 1f)
                    GesturePoint(nx, ny, pt.timeMillis)
                }
                val candidates = decoder.decode(normPoints, currentLanguage)
                if (candidates.isNotEmpty()) {
                    listener.onWordFlow(candidates.first(), candidates)
                }
            }
            trailPoints.clear()
            invalidate()
        }

        override fun onSwipeCancelled() {
            trailPoints.clear()
            invalidate()
        }

        override fun onSpacebarCursorMoved(step: Int) {
            listener.onSpacebarCursorMoved(step)
        }

        override fun onSpacebarLongPressed() {
            listener.onSpacebarLongPressed()
        }

        override fun onDoubleTapWordDetected() {
            listener.onDoubleTapWordDetected()
        }

        override fun onSwipeToDelete(wordsCount: Int) {
            listener.onSwipeToDelete(wordsCount)
        }
    }

    private val stateMachine = TouchInputStateMachine(
        listener = touchListener,
        swipeStartDistance = context.dp(24).toFloat(),
        swipeStartTimeMinMs = 45L,
        longPressTimeoutMs = 350L,
        spaceCursorThreshold = context.dp(16).toFloat(),
        touchSlop = context.dp(12).toFloat(),
        wordFlowEnabled = true
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var repeatingKey: KeySpec? = null
    private val repeatRunnable = object : Runnable {
        override fun run() {
            repeatingKey?.let {
                listener.onKey(it)
                mainHandler.postDelayed(this, 60)
            }
        }
    }

    private var activeDownPointerId = 0
    private val longPressTimerRunnable = Runnable {
        stateMachine.onLongPressTimerExpired(activeDownPointerId)
    }

    // Taps released while an earlier-pressed key is still held wait (bounded) to keep
    // press order; this timer guarantees a held key can never delay them indefinitely.
    private val flushPendingTapsRunnable = Runnable {
        stateMachine.flushPending(android.os.SystemClock.uptimeMillis())
    }

    private fun scheduleFlushPendingTaps() {
        mainHandler.removeCallbacks(flushPendingTapsRunnable)
        mainHandler.postDelayed(flushPendingTapsRunnable, stateMachine.tapOrderWindowMs + 10L)
    }

    init {
        orientation = VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(KeyboardColors.background)
        setPadding(context.dp(3), 0, context.dp(3), context.dp(4))
        setWillNotDraw(false)

        setOnApplyWindowInsetsListener { view, insets ->
            val navBottom = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            view.setPadding(context.dp(3), 0, context.dp(3), navBottom + context.dp(4))
            insets
        }

        // 1. Suggestion Bar with Clipboard Button
        suggestionBar.apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(2), 0, context.dp(2), 0)
            setBackgroundColor(KeyboardColors.background)
        }

        // Discreet Clipboard Button on left
        clipButton.apply {
            text = "📋"
            textSize = 15f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(KeyboardColors.foreground)
            setPadding(context.dp(6), 0, context.dp(6), 0)
            contentDescription = "Clipboard"
            setOnClickListener { listener.onClipboardPasteRequested() }
        }
        suggestionBar.addView(clipButton, LayoutParams(context.dp(34), context.dp(40)))

        // 3 Suggestion candidate buttons: Left, PRIMARY (center in BOLD), Right
        repeat(3) { index ->
            val button = TextView(context).apply {
                textSize = 18f
                gravity = Gravity.CENTER
                setBackgroundColor(KeyboardColors.background)
                setTextColor(KeyboardColors.foreground)
                setPadding(context.dp(3), 0, context.dp(3), 0)
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxLines = 1
                if (index == 1) {
                    // WP8.1 Authentic: Primary center candidate is BOLD
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                setOnClickListener {
                    if (text.isNotEmpty()) listener.onSuggestion(text.toString())
                }
            }
            suggestionButtons += button
            suggestionBar.addView(button, LayoutParams(0, context.dp(40), 1f))
        }
        addView(suggestionBar)

        // 2. Keyboard Row Container holding leftDockBar, keyRows, rightDockBar
        keyboardRowContainer.apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(leftDockBar)
            addView(keyRows, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(rightDockBar)
        }
        keyRows.orientation = VERTICAL
        contentContainer.addView(keyboardRowContainer, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(contentContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun createDockButton(symbol: String, desc: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = symbol
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(KeyboardColors.foreground)
            background = KeyboardColors.keyBackground()
            contentDescription = desc
            setOnClickListener { onClick() }
        }
    }

    private fun setupDockBars() {
        leftDockBar.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            removeAllViews()
            val switchBtn = createDockButton("⮜", "Mover a la izquierda") {
                listener.onOneHandedToggled("LEFT")
            }
            val expandBtn = createDockButton("⤢", "Expandir teclado") {
                listener.onOneHandedToggled("OFF")
            }
            addView(switchBtn, LinearLayout.LayoutParams(context.dp(44), context.dp(48)).apply {
                setMargins(context.dp(2), context.dp(8), context.dp(2), context.dp(8))
            })
            addView(expandBtn, LinearLayout.LayoutParams(context.dp(44), context.dp(48)).apply {
                setMargins(context.dp(2), context.dp(8), context.dp(2), context.dp(8))
            })
        }

        rightDockBar.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            removeAllViews()
            val expandBtn = createDockButton("⤢", "Expandir teclado") {
                listener.onOneHandedToggled("OFF")
            }
            val switchBtn = createDockButton("⮞", "Mover a la derecha") {
                listener.onOneHandedToggled("RIGHT")
            }
            addView(expandBtn, LinearLayout.LayoutParams(context.dp(44), context.dp(48)).apply {
                setMargins(context.dp(2), context.dp(8), context.dp(2), context.dp(8))
            })
            addView(switchBtn, LinearLayout.LayoutParams(context.dp(44), context.dp(48)).apply {
                setMargins(context.dp(2), context.dp(8), context.dp(2), context.dp(8))
            })
        }
    }

    private fun applyOneHandedMode(mode: String) {
        currentOneHandedMode = mode
        when (mode.uppercase()) {
            "LEFT" -> {
                leftDockBar.visibility = View.GONE
                rightDockBar.visibility = View.VISIBLE
                keyRows.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                rightDockBar.layoutParams = LinearLayout.LayoutParams(context.dp(52), LinearLayout.LayoutParams.MATCH_PARENT)
            }
            "RIGHT" -> {
                leftDockBar.visibility = View.VISIBLE
                rightDockBar.visibility = View.GONE
                leftDockBar.layoutParams = LinearLayout.LayoutParams(context.dp(52), LinearLayout.LayoutParams.MATCH_PARENT)
                keyRows.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            else -> {
                leftDockBar.visibility = View.GONE
                rightDockBar.visibility = View.GONE
                keyRows.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        }
        setupDockBars()
    }

    fun setLayout(
        page: KeyboardPage,
        policy: EditorPolicy,
        numberRow: Boolean = false,
        heightScale: Float = 1.0f,
        language: String = "es",
        wordFlow: Boolean = true,
        tintKeys: Boolean = false,
        layoutOverride: KeyboardLayoutType? = null,
        theme: String = "DARK",
        oneHandedMode: String = "OFF"
    ) {
        currentPage = page
        wordFlowEnabled = wordFlow
        stateMachine.wordFlowEnabled = wordFlow
        currentLanguage = language
        hasNumberRow = numberRow
        isTintKeysEnabled = tintKeys
        currentLayoutType = layoutOverride ?: KeyboardLayout.defaultLayoutFor(language)
        currentTheme = theme

        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        KeyboardColors.setTheme(theme, isSystemDark)
        setBackgroundColor(KeyboardColors.background)
        suggestionBar.setBackgroundColor(KeyboardColors.background)
        clipButton.setTextColor(KeyboardColors.foreground)

        applyOneHandedMode(oneHandedMode)
        currentPage = page
        wordFlowEnabled = wordFlow
        stateMachine.wordFlowEnabled = wordFlow
        currentLanguage = language
        hasNumberRow = numberRow
        isTintKeysEnabled = tintKeys
        currentLayoutType = layoutOverride ?: KeyboardLayout.defaultLayoutFor(language)

        closeEmoji()
        alternatives.dismiss()
        keyRows.removeAllViews()
        keys.clear()

        val rows = if (policy.kind == FieldKind.TEXT) {
            KeyboardLayout.rows(page, numberRow, language, currentLayoutType)
        } else {
            KeyboardLayout.numeric(
                phone = policy.kind == FieldKind.PHONE,
                decimal = policy.decimal,
                signed = policy.signed,
                datetime = policy.kind == FieldKind.DATETIME,
            )
        }

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val baseHeight = context.dp(if (landscape) 42 else 54)
        val height = (baseHeight * heightScale.coerceIn(0.7f, 1.4f)).toInt()

        val secondAlphaIndex = if (numberRow) 2 else 1
        rows.forEachIndexed { index, specs ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            val needsIndent = specs.size == 9 && policy.kind == FieldKind.TEXT && page == KeyboardPage.LETTERS && index == secondAlphaIndex
            if (needsIndent) {
                row.addView(View(context), LayoutParams(0, 1, .5f))
            }
            specs.forEach { spec ->
                val key = KeyButton(context, spec.action).apply {
                    text = spec.label
                    textSize = if (spec.action in listOf(KeyAction.SPACE, KeyAction.SYMBOLS,
                        KeyAction.LETTERS, KeyAction.MORE_SYMBOLS)) 16f else 23f
                    contentDescription = description(spec)
                    if (spec.label.isEmpty() && spec.action == KeyAction.TEXT) {
                        isEnabled = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        background = android.graphics.drawable.ColorDrawable(KeyboardColors.background)
                    }
                    if (isTintKeysEnabled && spec.action == KeyAction.TEXT) {
                        background = KeyboardColors.keyBackground(false, isTintKeysEnabled)
                    }
                }
                keys += spec to key
                row.addView(key, LayoutParams(0, LayoutParams.MATCH_PARENT, spec.weight).apply {
                    setMargins(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
                })
            }
            if (needsIndent) {
                row.addView(View(context), LayoutParams(0, 1, .5f))
            }
            keyRows.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, height))
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // If touches occur on suggestion bar, allow normal button click dispatch
        val childY = ev.y
        if (childY < contentContainer.top || emojiKeyboard != null) {
            return super.dispatchTouchEvent(ev)
        }

        // Custom touch state machine for the keys area
        val action = ev.actionMasked
        val pointerIndex = ev.actionIndex
        val pointerId = ev.getPointerId(pointerIndex)
        val x = ev.getX(pointerIndex)
        val y = ev.getY(pointerIndex)
        val time = ev.eventTime
        val key = findKeyAt(x, y)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                activeDownPointerId = pointerId
                mainHandler.removeCallbacks(longPressTimerRunnable)
                mainHandler.removeCallbacks(repeatRunnable)
                highlightKey(key, true)
                stateMachine.onDown(pointerId, x, y, time, key)

                if (key?.action == KeyAction.DELETE) {
                    repeatingKey = key
                    mainHandler.postDelayed(repeatRunnable, 400)
                } else if (key?.alternatives?.isNotEmpty() == true || key?.action == KeyAction.SPACE) {
                    mainHandler.postDelayed(longPressTimerRunnable, 350)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                mainHandler.removeCallbacks(longPressTimerRunnable)
                mainHandler.removeCallbacks(repeatRunnable)
                highlightKey(key, true)
                stateMachine.onPointerDown(pointerId, x, y, time, key)
                scheduleFlushPendingTaps()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val pid = ev.getPointerId(i)
                    val px = ev.getX(i)
                    val py = ev.getY(i)
                    val pKey = findKeyAt(px, py)
                    stateMachine.onMove(pid, px, py, time, pKey)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                highlightKey(key, false)
                stateMachine.onPointerUp(pointerId, x, y, time, key)
                scheduleFlushPendingTaps()
                return true
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressTimerRunnable)
                mainHandler.removeCallbacks(repeatRunnable)
                repeatingKey = null
                highlightKey(null, false)
                stateMachine.onUp(pointerId, x, y, time, key)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressTimerRunnable)
                mainHandler.removeCallbacks(repeatRunnable)
                repeatingKey = null
                highlightKey(null, false)
                stateMachine.onCancel()
                return true
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun findKeyAt(x: Float, y: Float): KeySpec? {
        val loc = Rect()
        for ((spec, button) in keys) {
            if (!button.isShown) continue
            button.getHitRect(loc)
            var pLeft = 0
            var pTop = 0
            var p: View? = button.parent as? View
            while (p != null && p != this@KeyboardView) {
                pLeft += p.left
                pTop += p.top
                p = p.parent as? View
            }
            val keyLeft = pLeft + loc.left
            val keyRight = pLeft + loc.right
            val keyTop = pTop + loc.top
            val keyBottom = pTop + loc.bottom
            if (x >= keyLeft && x <= keyRight && y >= keyTop && y <= keyBottom) {
                return spec
            }
        }
        return null
    }

    private fun findButtonForKey(spec: KeySpec): KeyButton? {
        return keys.firstOrNull { it.first == spec }?.second
    }

    private fun highlightKey(activeKey: KeySpec?, pressed: Boolean) {
        keys.forEach { (spec, button) ->
            if (spec == activeKey && pressed) {
                button.isPressed = true
            } else if (button.isPressed) {
                button.isPressed = false
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // Word Flow Glowing Gesture Trail
        if (stateMachine.isSwiping && trailPoints.size >= 2) {
            trailPath.reset()
            trailPath.moveTo(trailPoints.first().x, trailPoints.first().y)
            for (i in 1 until trailPoints.size) {
                val p = trailPoints[i]
                val prev = trailPoints[i - 1]
                trailPath.quadTo(prev.x, prev.y, (prev.x + p.x) / 2f, (prev.y + p.y) / 2f)
            }
            trailPaint.color = KeyboardColors.accent
            trailPaint.strokeWidth = context.dp(7).toFloat()
            trailPaint.alpha = 220
            canvas.drawPath(trailPath, trailPaint)
        }
    }

    fun render(state: ShiftState, forceUppercase: Boolean, candidates: List<String>, enterLabel: String) {
        uppercase = state != ShiftState.LOWERCASE || forceUppercase
        keys.forEach { (spec, key) ->
            if (spec.action == KeyAction.TEXT && spec.label.any(Char::isLetter)) {
                key.text = if (uppercase) spec.label.uppercase(java.util.Locale.ROOT) else spec.label
                key.contentDescription = key.text
            }
            if (spec.action == KeyAction.SHIFT) {
                key.shiftState = if (forceUppercase) ShiftState.CAPS_LOCK else state
                val active = uppercase
                key.background = KeyboardColors.keyBackground(active)
                key.setTextColor(if (active) (if (KeyboardColors.isDarkTheme) Color.BLACK else Color.WHITE) else KeyboardColors.foreground)
                key.contentDescription = context.getString(when (key.shiftState) {
                    ShiftState.LOWERCASE -> R.string.shift_lower
                    ShiftState.AUTO_SHIFT -> R.string.shift_auto
                    ShiftState.ONE_SHOT_SHIFT -> R.string.shift_once
                    ShiftState.CAPS_LOCK -> R.string.shift_locked
                })
            }
            if (spec.action == KeyAction.ENTER) {
                key.text = enterLabel
                key.textSize = 13f
                key.contentDescription = enterLabel.ifEmpty { context.getString(R.string.enter) }
            }
        }

        // Render suggestion bar: left, center (bold), right. The engine ranks best first; the best
        // candidate goes in the emphasised centre slot, the runner-up on its left.
        val slots = listOf(candidates.getOrNull(1), candidates.getOrNull(0), candidates.getOrNull(2))
        suggestionButtons.forEachIndexed { index, view ->
            val value = slots.getOrNull(index).orEmpty()
            view.text = value
            view.isEnabled = value.isNotEmpty()
            view.contentDescription = if (value.isEmpty()) null else
                context.getString(R.string.suggestion_description, value)
            view.importantForAccessibility = if (value.isEmpty()) View.IMPORTANT_FOR_ACCESSIBILITY_NO
                else View.IMPORTANT_FOR_ACCESSIBILITY_YES

            // Primary candidate emphasis
            if (index == 1) {
                view.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                view.setTextColor(if (value.isNotEmpty()) KeyboardColors.foreground else KeyboardColors.muted)
            } else {
                view.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                view.setTextColor(KeyboardColors.muted)
            }
            view.setBackgroundColor(KeyboardColors.background)
        }
    }

    fun showEmoticons() {
        if (emojiKeyboard != null) return
        val ek = EmojiKeyboardView(
            context = context,
            onEmojiSelected = { emoji -> listener.onKey(KeySpec(emoji, KeyAction.TEXT)) },
            onCloseEmoji = { closeEmoji() },
            onBackspace = { listener.onKey(KeySpec("", KeyAction.DELETE)) }
        )
        emojiKeyboard = ek
        keyboardRowContainer.visibility = View.GONE
        contentContainer.addView(ek, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, keyRows.height.coerceAtLeast(context.dp(220))))
    }

    fun closeEmoji(): Boolean {
        val ek = emojiKeyboard ?: return false
        contentContainer.removeView(ek)
        emojiKeyboard = null
        keyboardRowContainer.visibility = View.VISIBLE
        return true
    }

    fun dismissPopups() {
        alternatives.dismiss()
        closeEmoji()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onDetachedFromWindow() {
        mainHandler.removeCallbacksAndMessages(null)
        alternatives.dismiss()
        closeEmoji()
        super.onDetachedFromWindow()
    }

    private fun description(spec: KeySpec): String = when (spec.action) {
        KeyAction.SHIFT -> context.getString(R.string.shift_lower)
        KeyAction.DELETE -> context.getString(R.string.backspace)
        KeyAction.SPACE -> context.getString(R.string.space)
        KeyAction.ENTER -> context.getString(R.string.enter)
        KeyAction.SYMBOLS -> context.getString(R.string.symbols)
        KeyAction.LETTERS -> context.getString(R.string.letters)
        KeyAction.MORE_SYMBOLS -> context.getString(R.string.more_symbols)
        KeyAction.EMOJI -> context.getString(R.string.emoji)
        KeyAction.CLIPBOARD -> "Clipboard"
        KeyAction.TEXT -> if (spec.label == ".") context.getString(R.string.punctuation) else spec.label
    }
}
