package com.metroboard.ime.keyboard

import org.junit.Assert.*
import org.junit.Test

class DoubleSpaceHandlerTest {
    @Test fun fastSecondSpaceConverts() {
        val handler = DoubleSpaceHandler()
        assertFalse(handler.shouldReplace(1000, "hola", false))
        assertTrue(handler.shouldReplace(1200, "hola ", false))
    }
    @Test fun outsideIntervalDoesNotConvert() {
        val handler = DoubleSpaceHandler()
        handler.shouldReplace(1000, "hola", false)
        assertFalse(handler.shouldReplace(1401, "hola ", false))
    }
    @Test fun intervalIsConfigurableAndInclusive() {
        val handler = DoubleSpaceHandler(600)
        handler.shouldReplace(1000, "hola", false)
        assertTrue(handler.shouldReplace(1600, "hola ", false))
    }
    @Test fun existingSpaceDoesNotCountAsFirstTap() {
        assertFalse(DoubleSpaceHandler().shouldReplace(1000, "hola ", false))
    }
    @Test fun punctuationAndEmptyTextDoNotArm() {
        for (value in listOf("", " ", "hola.", "hola!", "hola?", "hola\n")) {
            val handler = DoubleSpaceHandler()
            assertFalse(handler.shouldReplace(1000, value, false))
            assertFalse(handler.shouldReplace(1100, "$value ", false))
        }
    }
    @Test fun selectionDoesNotConvert() {
        val handler = DoubleSpaceHandler()
        handler.shouldReplace(1000, "hola", false)
        assertFalse(handler.shouldReplace(1100, "hola ", true))
    }
    @Test fun interruptionCancelsPair() {
        val handler = DoubleSpaceHandler()
        handler.shouldReplace(1000, "hola", false); handler.reset()
        assertFalse(handler.shouldReplace(1100, "hola ", false))
    }
    @Test fun thirdSpaceDoesNotCreateSecondPeriod() {
        val handler = DoubleSpaceHandler()
        handler.shouldReplace(1000, "hola", false)
        assertTrue(handler.shouldReplace(1100, "hola ", false))
        assertFalse(handler.shouldReplace(1200, "hola. ", false))
    }
    @Test fun backwardsTimeDoesNotConvert() {
        val handler = DoubleSpaceHandler()
        handler.shouldReplace(1000, "hola", false)
        assertFalse(handler.shouldReplace(900, "hola ", false))
    }
}
