/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DictionaryRegistryAsyncTest {
    @Before @After fun reset() = LanguageDictionaryRegistry.clearCache()

    private fun dict(lang: String) = LanguageDictionary(lang, listOf("palabra" to 100))

    @Test fun preloadNeverBlocksTheCallerAndPublishesWhenDone() {
        val gate = CountDownLatch(1)
        val done = CountDownLatch(1)
        val t0 = System.nanoTime()
        LanguageDictionaryRegistry.preload("es", { done.countDown() }) { gate.await(5, TimeUnit.SECONDS); dict("es") }
        assertTrue("preload must return immediately", (System.nanoTime() - t0) / 1_000_000 < 500)
        assertNull("not loaded yet", LanguageDictionaryRegistry.getIfLoaded("es"))
        gate.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertNotNull(LanguageDictionaryRegistry.getIfLoaded("es"))
    }

    @Test fun concurrentPreloadsOfTheSameLanguageLoadOnce() {
        val loads = AtomicInteger()
        val gate = CountDownLatch(1)
        val done = CountDownLatch(2)
        repeat(2) {
            LanguageDictionaryRegistry.preload("es", { done.countDown() }) { loads.incrementAndGet(); gate.await(5, TimeUnit.SECONDS); dict("es") }
        }
        gate.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, loads.get())
    }

    @Test fun onlyTheTwoMostRecentlyUsedLanguagesStayInMemory() {
        for (lang in listOf("es", "en", "fr")) {
            val done = CountDownLatch(1)
            LanguageDictionaryRegistry.preload(lang, { done.countDown() }) { dict(lang) }
            assertTrue(done.await(5, TimeUnit.SECONDS))
        }
        assertNull("least recently used language is evicted", LanguageDictionaryRegistry.getIfLoaded("es"))
        assertNotNull(LanguageDictionaryRegistry.getIfLoaded("en"))
        assertNotNull(LanguageDictionaryRegistry.getIfLoaded("fr"))
    }

    @Test fun usingALanguageKeepsItFromBeingEvicted() {
        for (lang in listOf("es", "en")) {
            val done = CountDownLatch(1)
            LanguageDictionaryRegistry.preload(lang, { done.countDown() }) { dict(lang) }
            assertTrue(done.await(5, TimeUnit.SECONDS))
        }
        assertNotNull(LanguageDictionaryRegistry.getIfLoaded("es")) // touch es: now en is the oldest
        val done = CountDownLatch(1)
        LanguageDictionaryRegistry.preload("fr", { done.countDown() }) { dict("fr") }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertNotNull(LanguageDictionaryRegistry.getIfLoaded("es"))
        assertNull(LanguageDictionaryRegistry.getIfLoaded("en"))
    }

    @Test fun aFailingLoadDoesNotWedgeTheLanguage() {
        val failed = CountDownLatch(1)
        LanguageDictionaryRegistry.preload("es", { failed.countDown() }) { error("boom") }
        assertTrue(failed.await(5, TimeUnit.SECONDS))
        assertNull(LanguageDictionaryRegistry.getIfLoaded("es"))
        val ok = CountDownLatch(1)
        LanguageDictionaryRegistry.preload("es", { ok.countDown() }) { dict("es") }
        assertTrue("a retry must be possible", ok.await(5, TimeUnit.SECONDS))
        assertNotNull(LanguageDictionaryRegistry.getIfLoaded("es"))
    }

    @Test fun nonBlockingGetWithoutContextReturnsTheFallbackSynchronously() {
        val d = LanguageDictionaryRegistry.getNonBlocking("es", null)
        assertTrue(d.contains("hola"))
        assertSame(d, LanguageDictionaryRegistry.getNonBlocking("es", null))
        assertFalse(LanguageDictionaryRegistry.getNonBlocking("en", null).contains("hola"))
    }
}
