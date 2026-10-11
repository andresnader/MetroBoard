package com.metroboard.ime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.widget.Button
import com.metroboard.ime.keyboard.KeyAction
import com.metroboard.ime.keyboard.ShiftState
import com.metroboard.ime.util.KeyboardColors

/** Original geometric icons; normal Button accessibility/click semantics. */
class KeyButton @JvmOverloads constructor(context: Context, val action: KeyAction = KeyAction.TEXT) : Button(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPath = Path()
    var shiftState = ShiftState.LOWERCASE
        set(value) { field = value; invalidate() }
    var onRepeat: (() -> Unit)? = null
    private var repeating = false
    private val repeat = object : Runnable {
        override fun run() {
            if (!isPressed) return
            repeating = true
            onRepeat?.invoke()
            postDelayed(this, 65)
        }
    }

    init {
        isAllCaps = false
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = true
        isFocusable = false
        setPadding(0, 0, 0, 0)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        stateListAnimator = null
        setTextColor(android.graphics.Color.WHITE)
        textSize = 23f
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        maxLines = 1
        background = KeyboardColors.keyBackground()
    }

    // Ordinary taps delegate to Button, which calls performClick (including TalkBack).
    // Only an already-repeating delete cancels the release click to avoid double deletion.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (action != KeyAction.DELETE) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                repeating = false
                postDelayed(repeat, 400)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(repeat)
                if (repeating) {
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.onTouchEvent(cancel)
                    cancel.recycle()
                    repeating = false
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(repeat)
        super.onDetachedFromWindow()
    }

    // Button's touch handling invokes this for taps; accessibility invokes it directly.
    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = scrollX + width / 2f
        val cy = scrollY + height / 2f
        val u = resources.displayMetrics.density
        iconPath.reset()
        paint.color = currentTextColor
        paint.strokeWidth = 1.6f * resources.displayMetrics.density
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.MITER
        when (action) {
            KeyAction.SHIFT -> {
                val p = iconPath.apply {
                    moveTo(cx, cy - 10*u); lineTo(cx + 10*u, cy)
                    lineTo(cx + 5*u, cy); lineTo(cx + 5*u, cy + 8*u)
                    lineTo(cx - 5*u, cy + 8*u); lineTo(cx - 5*u, cy)
                    lineTo(cx - 10*u, cy); close()
                }
                if (shiftState != ShiftState.LOWERCASE) paint.style = Paint.Style.FILL
                canvas.drawPath(p, paint)
                if (shiftState == ShiftState.CAPS_LOCK) canvas.drawRect(cx-7*u, cy+11*u, cx+7*u, cy+13*u, paint)
                if (shiftState == ShiftState.AUTO_SHIFT) {
                    paint.color = KeyboardColors.accent
                    canvas.drawCircle(cx+13*u, cy-10*u, 2*u, paint)
                }
            }
            KeyAction.DELETE -> {
                val p = iconPath.apply {
                    moveTo(cx-11*u, cy); lineTo(cx-4*u, cy-7*u)
                    lineTo(cx+11*u, cy-7*u); lineTo(cx+11*u, cy+7*u)
                    lineTo(cx-4*u, cy+7*u); close()
                    moveTo(cx-1*u, cy-3*u); lineTo(cx+6*u, cy+3*u)
                    moveTo(cx+6*u, cy-3*u); lineTo(cx-1*u, cy+3*u)
                }
                canvas.drawPath(p, paint)
            }
            KeyAction.ENTER -> if (text.isEmpty()) {
                val p = iconPath.apply {
                    moveTo(cx+9*u, cy-8*u); lineTo(cx+9*u, cy+2*u)
                    lineTo(cx-9*u, cy+2*u); moveTo(cx-3*u, cy-4*u)
                    lineTo(cx-9*u, cy+2*u); lineTo(cx-3*u, cy+8*u)
                }
                canvas.drawPath(p, paint)
            }
            KeyAction.EMOJI -> {
                canvas.drawCircle(cx, cy, 10*u, paint)
                canvas.drawArc(cx-6*u, cy-4*u, cx+6*u, cy+6*u, 20f, 140f, false, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx-3*u, cy-3*u, 1.2f*u, paint)
                canvas.drawCircle(cx+3*u, cy-3*u, 1.2f*u, paint)
            }
            else -> Unit
        }
    }
}
