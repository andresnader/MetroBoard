package com.windowsphonekeyboard.ime.gestures

import org.junit.Assert.*
import org.junit.Test

class ShapeWritingDecoderTest {

    @Test
    fun resamplerProducesExactTargetPointCount() {
        val raw = listOf(
            GesturePoint(10f, 10f, 0L),
            GesturePoint(50f, 50f, 100L),
            GesturePoint(100f, 100f, 200L)
        )
        val resampled = GestureResampler.resample(raw, 32)
        assertEquals(32, resampled.size)
        assertEquals(10f, resampled.first().x, 0.01f)
        assertEquals(100f, resampled.last().x, 0.01f)
    }

    @Test
    fun resamplerHandlesDegenerateInputGracefully() {
        assertTrue(GestureResampler.resample(emptyList(), 32).isEmpty())
        val single = listOf(GesturePoint(5f, 5f, 0L))
        val resampledSingle = GestureResampler.resample(single, 32)
        assertEquals(32, resampledSingle.size)
        assertEquals(5f, resampledSingle.first().x, 0.01f)
    }

    @Test
    fun keyboardGeometryFindsKeysNearNormalizedLocation() {
        val h = KeyboardGeometry.KEY_CENTERS['h']!!
        val nearby = KeyboardGeometry.findKeysNear(h.x, h.y, 0.15f)
        assertTrue(nearby.contains('h'))
        assertEquals('h', KeyboardGeometry.nearestKey(h.x, h.y))
    }

    @Test
    fun decoderDecodesGestureForHola() {
        val decoder = WordFlowShapeDecoder()
        // Simulate a gesture through 'h' -> 'o' -> 'l' -> 'a'
        val h = KeyboardGeometry.KEY_CENTERS['h']!!
        val o = KeyboardGeometry.KEY_CENTERS['o']!!
        val l = KeyboardGeometry.KEY_CENTERS['l']!!
        val a = KeyboardGeometry.KEY_CENTERS['a']!!

        val path = listOf(
            GesturePoint(h.x, h.y, 0L),
            GesturePoint((h.x + o.x) / 2f, (h.y + o.y) / 2f, 50L),
            GesturePoint(o.x, o.y, 100L),
            GesturePoint(l.x, l.y, 150L),
            GesturePoint((l.x + a.x) / 2f, (l.y + a.y) / 2f, 200L),
            GesturePoint(a.x, a.y, 250L)
        )

        val results = decoder.decode(path)
        assertFalse(results.isEmpty())
        assertEquals("hola", results.first())
    }

    @Test
    fun decoderDecodesGestureForCasa() {
        val decoder = WordFlowShapeDecoder()
        // Simulate a gesture through 'c' -> 'a' -> 's' -> 'a'
        val c = KeyboardGeometry.KEY_CENTERS['c']!!
        val a = KeyboardGeometry.KEY_CENTERS['a']!!
        val s = KeyboardGeometry.KEY_CENTERS['s']!!

        val path = listOf(
            GesturePoint(c.x, c.y, 0L),
            GesturePoint((c.x + a.x) / 2f, (c.y + a.y) / 2f, 50L),
            GesturePoint(a.x, a.y, 100L),
            GesturePoint(s.x, s.y, 150L),
            GesturePoint(a.x, a.y, 200L)
        )

        val results = decoder.decode(path)
        assertFalse(results.isEmpty())
        assertTrue(results.contains("casa"))
    }
}
