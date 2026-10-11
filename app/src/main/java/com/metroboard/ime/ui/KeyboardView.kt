package com.metroboard.ime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.metroboard.ime.R
import com.metroboard.ime.gestures.GesturePoint
import com.metroboard.ime.gestures.TouchGestureArbiter
import com.metroboard.ime.gestures.TouchGestureMode
import com.metroboard.ime.gestures.WordFlowShapeDecoder
import com.metroboard.ime.ime.EditorPolicy
import com.metroboard.ime.ime.FieldKind
import com.metroboard.ime.keyboard.*
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp

// Built by the IME service, never inflated from XML; an editing listener is required.
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: Listener) : LinearLayout(context) {
    interface Listener {
        fun onKey(key: KeySpec)
        fun onSuggestion(word: String)
        fun onSwitchKeyboard()
        fun onAlternativesOpened()
        fun onWordFlow(word: String, candidates: List<String>)
        fun onLanguageToggled()
    }

    private val suggestionButtons = mutableListOf<TextView>()
    private val keys = mutableListOf<Pair<KeySpec, KeyButton>>()
    private val keyRows = LinearLayout(context)
    private val alternatives = AlternativesPopup()
    private var uppercase = false

    private val decoder = WordFlowShapeDecoder()
    private var wordFlowEnabled = true
    private var currentPage = KeyboardPage.LETTERS
    private var currentLanguage = "es"
    private var hasNumberRow = false
    private val trailPoints = mutableListOf<GesturePoint>()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trailPath = Path()
    private var isSwiping = false
    private val touchSlop = context.dp(16).toFloat()
    private val gestureArbiter = TouchGestureArbiter(
        swipeStartDistancePx = touchSlop,
        minSwipeDurationMs = 28L,
    )

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
        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        repeat(3) {
            val button = KeyButton(context, KeyAction.TEXT).apply {
                textSize = 18f
                background = android.graphics.drawable.ColorDrawable(KeyboardColors.background)
                setPadding(context.dp(3), 0, context.dp(3), 0)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener { listener.onSuggestion(text.toString()) }
            }
            suggestionButtons += button
            bar.addView(button, LayoutParams(0, context.dp(40), 1f))
        }
        addView(bar)
        keyRows.orientation = VERTICAL
        addView(keyRows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setLayout(
        page: KeyboardPage,
        policy: EditorPolicy,
        numberRow: Boolean = false,
        heightScale: Float = 1.0f,
        language: String = "es",
        wordFlow: Boolean = true,
    ) {
        currentPage = page
        wordFlowEnabled = wordFlow
        currentLanguage = language
        hasNumberRow = numberRow
        alternatives.dismiss()
        keyRows.removeAllViews()
        keys.clear()
        val rows = if (policy.kind == FieldKind.TEXT) {
            KeyboardLayout.rows(page, numberRow, language)
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
            // Indent 9-key rows on alpha layout (e.g. English asdfghjkl), but not 10-key Spanish row (asdfghjklñ).
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
                    setOnClickListener { listener.onKey(spec) }
                    onRepeat = { listener.onKey(spec) }
                    if (spec.alternatives.isNotEmpty()) setOnLongClickListener {
                        listener.onAlternativesOpened()
                        val values = if (uppercase) spec.alternatives.map { it.uppercase(java.util.Locale.ROOT) }
                            else spec.alternatives
                        alternatives.show(this, values) { listener.onKey(KeySpec(it)) }
                        true
                    }
                    if (spec.action == KeyAction.SPACE) setOnLongClickListener {
                        listener.onLanguageToggled()
                        true
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

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!wordFlowEnabled || currentPage != KeyboardPage.LETTERS) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerId = ev.getPointerId(0)
                gestureArbiter.onDown(pointerId, ev.getX(0), ev.getY(0), ev.eventTime)
                isSwiping = false
                trailPoints.clear()
                trailPoints.add(GesturePoint(ev.getX(0), ev.getY(0), ev.eventTime))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                gestureArbiter.onAdditionalPointerDown(ev.pointerCount)
                // Two-thumb typing wins over shape writing for this whole gesture.
                isSwiping = false
                trailPoints.clear()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureArbiter.isMultitouch || ev.pointerCount != 1) return false
                val pointerIndex = ev.findPointerIndex(gestureArbiter.primaryPointerId)
                if (pointerIndex < 0) return false
                val x = ev.getX(pointerIndex)
                val y = ev.getY(pointerIndex)
                if (gestureArbiter.shouldStartSwipe(
                        pointerId = gestureArbiter.primaryPointerId,
                        x = x,
                        y = y,
                        eventTime = ev.eventTime,
                        pointerCount = ev.pointerCount,
                    )
                ) {
                    isSwiping = true
                    trailPoints.add(GesturePoint(x, y, ev.eventTime))
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (gestureArbiter.isMultitouch) {
                    isSwiping = false
                    trailPoints.clear()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSwiping = false
                trailPoints.clear()
                gestureArbiter.endGesture()
                invalidate()
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!wordFlowEnabled || currentPage != KeyboardPage.LETTERS) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                gestureArbiter.onAdditionalPointerDown(ev.pointerCount)
                isSwiping = false
                trailPoints.clear()
                invalidate()
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureArbiter.isMultitouch || ev.pointerCount != 1) return false
                if (isSwiping && gestureArbiter.mode == TouchGestureMode.SWIPE_ACTIVE) {
                    val pointerIndex = ev.findPointerIndex(gestureArbiter.primaryPointerId)
                    if (pointerIndex < 0) return false
                    trailPoints.add(GesturePoint(ev.getX(pointerIndex), ev.getY(pointerIndex), ev.eventTime))
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isSwiping && gestureArbiter.mode == TouchGestureMode.SWIPE_ACTIVE) {
                    val pointerIndex = ev.findPointerIndex(gestureArbiter.primaryPointerId)
                    val x = if (pointerIndex >= 0) ev.getX(pointerIndex) else ev.x
                    val y = if (pointerIndex >= 0) ev.getY(pointerIndex) else ev.y
                    trailPoints.add(GesturePoint(x, y, ev.eventTime))
                    if (trailPoints.size >= 3 && keyRows.width > 0) {
                        val firstAlphaIndex = if (hasNumberRow) 1 else 0
                        val lastAlphaIndex = firstAlphaIndex + 2
                        val firstAlphaRow = keyRows.getChildAt(firstAlphaIndex)
                        val lastAlphaRow = keyRows.getChildAt(lastAlphaIndex)
                        val alphaTop = keyRows.top + (firstAlphaRow?.top ?: 0)
                        val alphaBottom = keyRows.top + (lastAlphaRow?.bottom ?: keyRows.height)
                        val alphaHeight = (alphaBottom - alphaTop).coerceAtLeast(1)

                        val normPoints = trailPoints.map { pt ->
                            val nx = ((pt.x - keyRows.left) / keyRows.width.toFloat()).coerceIn(0f, 1f)
                            val ny = ((pt.y - alphaTop) / alphaHeight.toFloat()).coerceIn(0f, 1f)
                            GesturePoint(nx, ny, pt.timeMillis)
                        }
                        val candidates = decoder.decode(normPoints, currentLanguage)
                        if (candidates.isNotEmpty()) {
                            listener.onWordFlow(candidates.first(), candidates)
                        }
                    }
                    isSwiping = false
                    trailPoints.clear()
                    gestureArbiter.endGesture()
                    invalidate()
                    return true
                }
                gestureArbiter.endGesture()
            }
            MotionEvent.ACTION_CANCEL -> {
                isSwiping = false
                trailPoints.clear()
                gestureArbiter.endGesture()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (wordFlowEnabled && trailPoints.size >= 2) {
            trailPath.reset()
            trailPath.moveTo(trailPoints.first().x, trailPoints.first().y)
            for (i in 1 until trailPoints.size) {
                val p = trailPoints[i]
                val prev = trailPoints[i - 1]
                trailPath.quadTo(prev.x, prev.y, (prev.x + p.x) / 2f, (prev.y + p.y) / 2f)
            }
            trailPaint.color = KeyboardColors.accent
            trailPaint.strokeWidth = context.dp(7).toFloat()
            trailPaint.alpha = 210
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
                key.setTextColor(if (active) Color.BLACK else Color.WHITE)
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
        suggestionButtons.forEachIndexed { index, view ->
            val value = candidates.getOrNull(index).orEmpty()
            view.text = value
            view.isEnabled = value.isNotEmpty()
            view.contentDescription = if (value.isEmpty()) null else
                context.getString(R.string.suggestion_description, value)
            view.importantForAccessibility = if (value.isEmpty()) View.IMPORTANT_FOR_ACCESSIBILITY_NO
                else View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    fun showEmoticons() {
        val anchor = keys.firstOrNull { it.first.action == KeyAction.EMOJI }?.second ?: return
        listener.onAlternativesOpened()
        alternatives.show(anchor, listOf(":)", ":D", ";)", ":(")) { listener.onKey(KeySpec(it)) }
    }

    fun dismissPopups() = alternatives.dismiss()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onDetachedFromWindow() {
        alternatives.dismiss()
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
        KeyAction.TEXT -> if (spec.label == ".") context.getString(R.string.punctuation) else spec.label
    }
}
