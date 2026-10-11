/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A Windows Phone style pivot: a row of large section labels above swipeable pages, and it is circular.
 * Swiping past the last section lands on the first one and the other way round; the labels rotate with it,
 * so the one that has just been passed leaves on the left and comes back in from the right.
 *
 * One continuous `position` drives everything ([PivotMath]): pages are slid by `translationX`, the labels
 * are placed on a ring, and only the page(s) currently on screen are visible. Pages are ordinary views
 * (the Settings pages are vertical scroll views), so vertical scrolling inside a page keeps working.
 */
internal class MetroPivot(
    context: Context,
    titles: List<String>,
    private val pages: List<View>,
    private val onPageSelected: (Int) -> Unit,
) : LinearLayout(context) {
    private val count = pages.size
    private val labelGap = context.dp(28)

    private val header = FrameLayout(context).apply {
        clipToPadding = false
        setPadding(context.dp(24), context.dp(8), 0, context.dp(12))
    }
    private val body = Body(context)
    private val labels: List<TextView> = titles.mapIndexed { index, title ->
        TextView(context).apply {
            text = title
            textSize = MetroType.PIVOT
            maxLines = 1
            // The light face for every label. The active one is told apart by colour, never by weight.
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            isClickable = true
            isFocusable = true
            setOnClickListener { goTo(index) }
        }
    }

    private val idleColor = KeyboardColors.muted
    private val activeColor = KeyboardColors.foreground
    private val colors = ArgbEvaluator()
    private var animator: ValueAnimator? = null

    /** Continuous position in pages. Wrapped into `[0, count)` whenever the pivot is at rest. */
    var position = 0f
        private set

    val currentPage: Int get() = PivotMath.nearestPage(position, count)

    init {
        orientation = VERTICAL
        require(count > 0 && titles.size == count) { "one title per page" }
        labels.forEach { header.addView(it, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START)) }
        pages.forEach { body.addView(it, FrameLayout.LayoutParams(MATCH, MATCH)) }
        addView(header, LayoutParams(MATCH, WRAP))
        addView(body, LayoutParams(MATCH, 0, 1f))
        // Labels get their widths on the first layout, and again if the font scale changes.
        header.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply() }
        body.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply() }
    }

    /** Jumps to [page] without animating; used when the screen is rebuilt or restored. */
    fun showPage(page: Int) {
        animator?.cancel()
        position = page.coerceIn(0, count - 1).toFloat()
        apply()
    }

    /** Animates to [page] by the shorter way round the ring. */
    fun goTo(page: Int) {
        val from = position.roundToInt().toFloat()
        animateTo(from + PivotMath.offset(page, from, count))
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        val distance = abs(target - position)
        if (distance < 0.001f) {
            settle(target)
            return
        }
        var cancelled = false
        animator = ValueAnimator.ofFloat(position, target).apply {
            // Under 500 ms reads as instant (the WP motion rules); a longer hop takes a little longer.
            duration = (200 + 70 * distance).coerceAtMost(420f).toLong()
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { move(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) settle(target) }
            })
            start()
        }
    }

    private fun move(newPosition: Float) {
        position = newPosition
        apply()
    }

    /** At rest: wrap the position back into range and tell the owner which page is showing. */
    private fun settle(target: Float) {
        position = PivotMath.normalize(target, count)
        apply()
        onPageSelected(currentPage)
    }

    private fun apply() {
        val width = body.width
        if (width == 0) return
        pages.forEachIndexed { index, page ->
            val offset = PivotMath.offset(index, position, count)
            page.translationX = offset * width
            // INVISIBLE rather than GONE: hidden pages stay laid out, so their scroll position survives.
            page.visibility = if (abs(offset) < 1f) VISIBLE else INVISIBLE
            page.importantForAccessibility =
                if (abs(offset) < 0.5f) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val widths = IntArray(count) { labels[it].width }
        if (widths.any { it == 0 }) return
        val xs = PivotMath.headerOffsets(widths, labelGap, position)
        labels.forEachIndexed { index, label ->
            label.translationX = xs[index]
            label.setTextColor(
                colors.evaluate(PivotMath.activeness(index, position, count), idleColor, activeColor) as Int,
            )
            label.isSelected = abs(PivotMath.offset(index, position, count)) < 0.5f
        }
    }

    /** The page area. Owns the horizontal drag so a swipe never fights the pages' vertical scrolling. */
    private inner class Body(context: Context) : FrameLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var dragStartPosition = 0f
        private var dragStartPage = 0
        private var velocity: VelocityTracker? = null

        init {
            clipChildren = true
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    dragging = false
                    velocity?.recycle()
                    velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
                }
                MotionEvent.ACTION_MOVE -> {
                    velocity?.addMovement(ev)
                    val dx = ev.x - downX
                    if (!dragging && abs(dx) > slop && abs(dx) > abs(ev.y - downY)) beginDrag()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release(cancelled = ev.actionMasked == MotionEvent.ACTION_CANCEL)
            }
            return dragging
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (!dragging) return false
            velocity?.addMovement(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> move(dragStartPosition - (ev.x - downX) / width.coerceAtLeast(1))
                MotionEvent.ACTION_UP -> release(cancelled = false)
                MotionEvent.ACTION_CANCEL -> release(cancelled = true)
            }
            return true
        }

        private fun beginDrag() {
            animator?.cancel()
            dragging = true
            dragStartPosition = position
            dragStartPage = position.roundToInt()
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        private fun release(cancelled: Boolean) {
            val wasDragging = dragging
            dragging = false
            val tracker = velocity
            velocity = null
            if (wasDragging) {
                tracker?.computeCurrentVelocity(1000)
                // A finger moving left (negative px/s) carries the content towards the next page.
                val pagesPerSecond = -(tracker?.xVelocity ?: 0f) / width.coerceAtLeast(1)
                val target = if (cancelled) position.roundToInt()
                else PivotMath.settleTarget(dragStartPage, position, pagesPerSecond)
                animateTo(target.toFloat())
            }
            tracker?.recycle()
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            // TalkBack's own scroll gestures turn the pages, and (being a ring) both always apply.
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }

        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { goTo(currentPage + 1); true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { goTo(currentPage - 1); true }
            else -> super.performAccessibilityAction(action, arguments)
        }
    }

    private companion object {
        const val MATCH = LayoutParams.MATCH_PARENT
        const val WRAP = LayoutParams.WRAP_CONTENT
    }
}
