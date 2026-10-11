package com.windowsphonekeyboard.ime.keyboard

import org.junit.Assert.*
import org.junit.Test

class ShiftStateManagerTest {
    @Test fun sentenceStartUsesAutoShift() {
        val shift = ShiftStateManager()
        shift.reset(true)
        assertEquals(ShiftState.AUTO_SHIFT, shift.state)
        assertTrue(shift.isUppercase)
    }
    @Test fun manualTapUsesOneShot() {
        val shift = ShiftStateManager()
        shift.tap(1000)
        assertEquals(ShiftState.ONE_SHOT_SHIFT, shift.state)
    }
    @Test fun manualTapFromAutoIsOneShot() {
        val shift = ShiftStateManager()
        shift.reset(true)
        shift.tap(1000)
        assertEquals(ShiftState.ONE_SHOT_SHIFT, shift.state)
    }
    @Test fun oneShotReturnsToLowercaseAfterLetter() {
        val shift = ShiftStateManager()
        shift.tap(1000)
        shift.onLetterCommitted()
        assertEquals(ShiftState.LOWERCASE, shift.state)
    }
    @Test fun autoReturnsToLowercaseAfterLetter() {
        val shift = ShiftStateManager()
        shift.reset(true)
        shift.onLetterCommitted()
        assertEquals(ShiftState.LOWERCASE, shift.state)
    }
    @Test fun doubleTapLocksAndLettersKeepLock() {
        val shift = ShiftStateManager()
        shift.tap(1000)
        shift.tap(1250)
        assertEquals(ShiftState.CAPS_LOCK, shift.state)
        shift.onLetterCommitted()
        shift.updateAutomatic(false)
        assertEquals(ShiftState.CAPS_LOCK, shift.state)
    }
    @Test fun tapExitsCapsLockEvenInsideDoubleTapWindow() {
        val shift = ShiftStateManager()
        shift.tap(1000); shift.tap(1100); shift.tap(1200)
        shift.updateAutomatic(true)
        assertEquals(ShiftState.LOWERCASE, shift.state)
    }
    @Test fun slowSecondTapDoesNotLock() {
        val shift = ShiftStateManager()
        shift.tap(1000); shift.tap(1301)
        assertEquals(ShiftState.LOWERCASE, shift.state)
    }
    @Test fun punctuationEnablesAutoShift() {
        val shift = ShiftStateManager()
        shift.onSentenceBoundary()
        assertEquals(ShiftState.AUTO_SHIFT, shift.state)
    }
    @Test fun punctuationDoesNotClearCapsLock() {
        val shift = ShiftStateManager()
        shift.tap(1000); shift.tap(1100); shift.onSentenceBoundary()
        assertEquals(ShiftState.CAPS_LOCK, shift.state)
    }
    @Test fun nonShiftActionBreaksDoubleTap() {
        val shift = ShiftStateManager()
        shift.tap(1000); shift.breakTapSequence(); shift.tap(1100)
        assertNotEquals(ShiftState.CAPS_LOCK, shift.state)
    }
    @Test fun cursorMoveClearsOneShotAndUpdatesContext() {
        val shift = ShiftStateManager()
        shift.tap(1000); shift.onCursorMoved(false)
        assertEquals(ShiftState.LOWERCASE, shift.state)
        shift.onCursorMoved(true)
        assertEquals(ShiftState.AUTO_SHIFT, shift.state)
    }
    @Test fun newEditorClearsLock() {
        val shift = ShiftStateManager()
        shift.tap(1000); shift.tap(1100); shift.reset(false)
        assertEquals(ShiftState.LOWERCASE, shift.state)
    }
}
