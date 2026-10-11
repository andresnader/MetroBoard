package com.windowsphonekeyboard.ime.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.windowsphonekeyboard.ime.keyboard.KeyAction
import com.windowsphonekeyboard.ime.util.KeyboardColors
import com.windowsphonekeyboard.ime.util.dp

class AlternativesPopup {
    private var popup: PopupWindow? = null

    // x is an absolute screen coordinate; QWERTY and its popup are intentionally LTR.
    @SuppressLint("RtlHardcoded")
    fun show(anchor: View, values: List<String>, onSelected: (String) -> Unit) {
        dismiss()
        if (values.isEmpty() || !anchor.isAttachedToWindow) return
        val context = anchor.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
            setBackgroundColor(KeyboardColors.accent)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        values.forEach { value ->
            row.addView(KeyButton(context, KeyAction.TEXT).apply {
                text = value
                contentDescription = value
                setOnClickListener { dismiss(); onSelected(value) }
            }, LinearLayout.LayoutParams(context.dp(if (value.length > 1) 58 else 48), context.dp(48)))
        }
        val window = PopupWindow(row, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            elevation = context.dp(4).toFloat()
        }
        popup = window
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val x = (location[0] + anchor.width / 2 - row.measuredWidth / 2)
            .coerceIn(0, (screenWidth - row.measuredWidth).coerceAtLeast(0))
        window.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, x, location[1] - row.measuredHeight)
    }

    fun dismiss() { popup?.dismiss(); popup = null }
}
