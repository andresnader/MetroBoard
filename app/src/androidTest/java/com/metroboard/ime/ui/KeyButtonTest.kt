package com.metroboard.ime.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metroboard.ime.keyboard.KeyAction
import com.metroboard.ime.keyboard.ShiftState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyButtonTest {
    @Test
    fun testKeyButtonDrawsIcons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val button = KeyButton(context, KeyAction.SHIFT)
        button.shiftState = ShiftState.AUTO_SHIFT
        button.setTextColor(Color.BLACK)
        button.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY)
        )
        button.layout(0, 0, 200, 200)

        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        button.draw(canvas)

        var nonTransparentCount = 0
        var blackPixels = 0
        for (x in 0 until 200) {
            for (y in 0 until 200) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) > 0) nonTransparentCount++
                if (pixel == Color.BLACK) blackPixels++
            }
        }
        android.util.Log.d("KeyButtonTest", "nonTransparentCount=$nonTransparentCount, blackPixels=$blackPixels")
        assertTrue("Should have drawn pixels", nonTransparentCount > 0)
        assertTrue("Should have drawn black icon pixels: got $blackPixels", blackPixels > 0)
    }

    @Test
    fun testDeleteButtonDrawsIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val button = KeyButton(context, KeyAction.DELETE)
        button.setTextColor(Color.WHITE)
        button.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY)
        )
        button.layout(0, 0, 200, 200)

        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        button.draw(canvas)

        var whitePixels = 0
        for (x in 0 until 200) {
            for (y in 0 until 200) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == Color.WHITE) whitePixels++
            }
        }
        android.util.Log.d("KeyButtonTest", "whitePixels=$whitePixels")
        assertTrue("Should have drawn white delete icon pixels: got $whitePixels", whitePixels > 0)
    }

    @Test
    fun testEmojiButtonDrawsIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val button = KeyButton(context, KeyAction.EMOJI)
        button.setTextColor(Color.WHITE)
        button.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY)
        )
        button.layout(0, 0, 200, 200)

        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        button.draw(canvas)

        var whitePixels = 0
        for (x in 0 until 200) {
            for (y in 0 until 200) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == Color.WHITE) whitePixels++
            }
        }
        assertTrue("Should have drawn white emoji icon pixels: got $whitePixels", whitePixels > 0)
    }

    @Test
    fun testEnterButtonDrawsIconWhenTextEmpty() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val button = KeyButton(context, KeyAction.ENTER)
        button.setTextColor(Color.WHITE)
        button.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY)
        )
        button.layout(0, 0, 200, 200)

        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        button.draw(canvas)

        var whitePixels = 0
        for (x in 0 until 200) {
            for (y in 0 until 200) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == Color.WHITE) whitePixels++
            }
        }
        assertTrue("Should have drawn white enter icon pixels: got $whitePixels", whitePixels > 0)
    }
}
