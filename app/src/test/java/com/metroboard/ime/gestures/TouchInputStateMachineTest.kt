/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.gestures

import com.metroboard.ime.keyboard.KeyAction
import com.metroboard.ime.keyboard.KeySpec
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TouchInputStateMachineTest {

    private val tappedKeys = mutableListOf<KeySpec>()
    private val longPressedKeys = mutableListOf<KeySpec>()
    private var swipeStarted = false
    private var swipeEnded = false
    private var swipeCancelled = false
    private var spacebarLongPressed = false
    private var cursorMoveSteps = 0
    private var swipeDeletedWords = 0

    private val testListener = object : TouchInputListener {
        override fun onKeyTapped(key: KeySpec) {
            tappedKeys.add(key)
        }

        override fun onKeyLongPressed(key: KeySpec, anchorX: Float, anchorY: Float) {
            longPressedKeys.add(key)
        }

        override fun onSwipeStarted(startPoint: GesturePoint) {
            swipeStarted = true
        }

        override fun onSwipeUpdated(point: GesturePoint) {}

        override fun onSwipeEnded(path: List<GesturePoint>) {
            swipeEnded = true
        }

        override fun onSwipeCancelled() {
            swipeCancelled = true
        }

        override fun onSpacebarCursorMoved(step: Int) {
            cursorMoveSteps += step
        }

        override fun onSpacebarLongPressed() {
            spacebarLongPressed = true
        }

        override fun onSwipeToDelete(wordsCount: Int) {
            swipeDeletedWords += wordsCount
        }
    }

    private lateinit var sm: TouchInputStateMachine

    @Before
    fun setup() {
        tappedKeys.clear()
        longPressedKeys.clear()
        swipeStarted = false
        swipeEnded = false
        swipeCancelled = false
        spacebarLongPressed = false
        cursorMoveSteps = 0

        sm = TouchInputStateMachine(
            listener = testListener,
            swipeStartDistance = 40f,
            swipeStartTimeMinMs = 30L,
            longPressTimeoutMs = 300L,
            spaceCursorThreshold = 20f,
            touchSlop = 15f,
            wordFlowEnabled = true
        )
    }

    @Test
    fun singleTapEmitsKeyTapped() {
        val keyA = KeySpec("a", KeyAction.TEXT)
        sm.onDown(0, 100f, 100f, 1000L, keyA)
        sm.onUp(0, 102f, 101f, 1050L, keyA)

        assertEquals(1, tappedKeys.size)
        assertEquals(keyA, tappedKeys.first())
        assertFalse(swipeStarted)
        assertFalse(swipeEnded)
    }

    @Test
    fun multiTouchTwoThumbsTypingNeverTriggersSwipeAndEmitsBothTaps() {
        val keyA = KeySpec("a", KeyAction.TEXT)
        val keyL = KeySpec("l", KeyAction.TEXT)

        // Thumb 1 presses 'a'
        sm.onDown(0, 50f, 100f, 1000L, keyA)
        assertEquals(TouchInteractionState.TAP_CANDIDATE, sm.state)

        // Thumb 2 presses 'l' while thumb 1 is still down
        sm.onPointerDown(1, 400f, 100f, 1040L, keyL)
        assertEquals(TouchInteractionState.MULTITOUCH_ACTIVE, sm.state)

        // Thumb 1 moves slightly (natural thumb roll)
        sm.onMove(0, 54f, 102f, 1060L, keyA)
        // Thumb 2 moves slightly
        sm.onMove(1, 403f, 98f, 1070L, keyL)

        // Thumb 1 releases
        sm.onPointerUp(0, 54f, 102f, 1080L, keyA)
        assertEquals(1, tappedKeys.size)
        assertEquals("a", tappedKeys[0].label)

        // Thumb 2 releases
        sm.onUp(1, 403f, 98f, 1100L, keyL)
        assertEquals(2, tappedKeys.size)
        assertEquals("l", tappedKeys[1].label)

        assertFalse("Multi-touch must NEVER start swipe typing", swipeStarted)
        assertFalse("Multi-touch must NEVER end swipe typing", swipeEnded)
        assertFalse(swipeCancelled)
    }

    @Test
    fun singleFingerSwipeAcrossAlphaKeysActivatesSwipeAndEnds() {
        val keyH = KeySpec("h", KeyAction.TEXT)
        val keyO = KeySpec("o", KeyAction.TEXT)

        sm.onDown(0, 100f, 100f, 1000L, keyH)
        sm.onMove(0, 120f, 100f, 1020L, keyH)
        sm.onMove(0, 150f, 100f, 1050L, keyO) // totalDist = 50f >= 40f, time >= 30ms

        // Only two letter keys over a short path is not yet evidence of shape writing
        // (a thumb rolling onto a neighbour key must stay a tap).
        assertFalse("Two keys over a short path must not start shape writing", sm.isSwiping)

        sm.onMove(0, 180f, 100f, 1080L, keyO) // path now >= 2 * swipeStartDistance
        assertTrue("Expected swipe to be active", sm.isSwiping)
        assertTrue(swipeStarted)

        sm.onUp(0, 200f, 100f, 1100L, keyO)

        assertTrue(swipeEnded)
        assertEquals("Swipe must not emit single key tap", 0, tappedKeys.size)
    }

    @Test
    fun spacebarHorizontalSlideMovesCursorAndDoesNotTypeSpace() {
        val keySpace = KeySpec("space", KeyAction.SPACE)

        sm.onDown(0, 200f, 300f, 1000L, keySpace)
        // Move right by 25px (> 20px threshold)
        sm.onMove(0, 225f, 300f, 1050L, keySpace)
        assertEquals(TouchInteractionState.SPACEBAR_CURSOR_ACTIVE, sm.state)
        assertEquals(1, cursorMoveSteps)

        // Move right by another 25px (total dx = 50px)
        sm.onMove(0, 250f, 300f, 1080L, keySpace)
        assertEquals(2, cursorMoveSteps)

        // Move left by 25px
        sm.onMove(0, 225f, 300f, 1100L, keySpace)
        assertEquals(1, cursorMoveSteps)

        // Release spacebar
        sm.onUp(0, 225f, 300f, 1150L, keySpace)

        assertFalse("Spacebar cursor gesture must not trigger swipe", swipeStarted)
        assertEquals("Spacebar cursor gesture must NOT insert space on release", 0, tappedKeys.size)
    }

    @Test
    fun spacebarLongPressOpensLanguageSelector() {
        val keySpace = KeySpec("space", KeyAction.SPACE)

        sm.onDown(0, 200f, 300f, 1000L, keySpace)
        sm.onLongPressTimerExpired(0)

        assertTrue("Long press on spacebar must trigger language picker", spacebarLongPressed)
        assertEquals(TouchInteractionState.LONG_PRESS_ACTIVE, sm.state)

        sm.onUp(0, 200f, 300f, 1400L, keySpace)
        assertEquals(0, tappedKeys.size)
    }

    @Test
    fun keyLongPressWithAlternativesOpensPopupAndDoesNotSwipe() {
        val keyA = KeySpec("a", KeyAction.TEXT, alternatives = listOf("á", "à"))

        sm.onDown(0, 50f, 100f, 1000L, keyA)
        sm.onLongPressTimerExpired(0)

        assertEquals(1, longPressedKeys.size)
        assertEquals(keyA, longPressedKeys.first())
        assertFalse(swipeStarted)
    }

    @Test
    fun swipeLeftOnDeleteTriggersSwipeToDelete() {
        val keyDel = KeySpec("del", KeyAction.DELETE)
        sm.onDown(0, 300f, 200f, 1000L, keyDel)
        // Move leftwards by 80px (> swipeStartDistance = 40f)
        sm.onMove(0, 220f, 200f, 1050L, keyDel)
        assertEquals(TouchInteractionState.BACKSPACE_SWIPE_DELETE_ACTIVE, sm.state)
        sm.onUp(0, 220f, 200f, 1100L, keyDel)

        assertTrue(swipeDeletedWords > 0)
        assertEquals(0, tappedKeys.size)
    }
}
