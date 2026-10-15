/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.gestures

import kotlin.math.hypot

data class KeyCenter(val char: Char, val x: Float, val y: Float)

object KeyboardGeometry {
    val KEY_CENTERS_ES: Map<Char, KeyCenter>
    val KEY_CENTERS_EN: Map<Char, KeyCenter>
    val KEY_CENTERS_FR: Map<Char, KeyCenter>
    val KEY_CENTERS_DE: Map<Char, KeyCenter>

    init {
        // Spanish Layout: Row 1 has 10 keys (asdfghjklñ) without left indent
        val mapEs = HashMap<Char, KeyCenter>()
        val row0 = "qwertyuiop"
        val row1Es = "asdfghjklñ"
        val row2 = "zxcvbnm"

        for (i in row0.indices) {
            mapEs[row0[i]] = KeyCenter(row0[i], (i + 0.5f) * 0.10f, 0.17f)
        }
        for (i in row1Es.indices) {
            mapEs[row1Es[i]] = KeyCenter(row1Es[i], (i + 0.5f) * 0.10f, 0.50f)
        }
        for (i in row2.indices) {
            mapEs[row2[i]] = KeyCenter(row2[i], 0.15f + (i + 0.5f) * 0.10f, 0.83f)
        }
        KEY_CENTERS_ES = mapEs

        // English Layout: Row 1 has 9 keys (asdfghjkl) indented by half key width (0.05f)
        val mapEn = HashMap<Char, KeyCenter>()
        val row1En = "asdfghjkl"
        for (i in row0.indices) {
            mapEn[row0[i]] = KeyCenter(row0[i], (i + 0.5f) * 0.10f, 0.17f)
        }
        for (i in row1En.indices) {
            mapEn[row1En[i]] = KeyCenter(row1En[i], 0.05f + (i + 0.5f) * 0.10f, 0.50f)
        }
        for (i in row2.indices) {
            mapEn[row2[i]] = KeyCenter(row2[i], 0.15f + (i + 0.5f) * 0.10f, 0.83f)
        }
        KEY_CENTERS_EN = mapEn

        // French AZERTY Layout: azertyuiop, qsdfghjklm, wxcvbn
        val mapFr = HashMap<Char, KeyCenter>()
        val row0Fr = "azertyuiop"
        val row1Fr = "qsdfghjklm"
        val row2Fr = "wxcvbn"
        for (i in row0Fr.indices) {
            mapFr[row0Fr[i]] = KeyCenter(row0Fr[i], (i + 0.5f) * 0.10f, 0.17f)
        }
        for (i in row1Fr.indices) {
            mapFr[row1Fr[i]] = KeyCenter(row1Fr[i], (i + 0.5f) * 0.10f, 0.50f)
        }
        for (i in row2Fr.indices) {
            mapFr[row2Fr[i]] = KeyCenter(row2Fr[i], 0.20f + (i + 0.5f) * 0.10f, 0.83f)
        }
        KEY_CENTERS_FR = mapFr

        // German QWERTZ Layout: qwertzuiop, asdfghjkl, yxcvbnm
        val mapDe = HashMap<Char, KeyCenter>()
        val row0De = "qwertzuiop"
        val row1De = "asdfghjkl"
        val row2De = "yxcvbnm"
        for (i in row0De.indices) {
            mapDe[row0De[i]] = KeyCenter(row0De[i], (i + 0.5f) * 0.10f, 0.17f)
        }
        for (i in row1De.indices) {
            mapDe[row1De[i]] = KeyCenter(row1De[i], 0.05f + (i + 0.5f) * 0.10f, 0.50f)
        }
        for (i in row2De.indices) {
            mapDe[row2De[i]] = KeyCenter(row2De[i], 0.15f + (i + 0.5f) * 0.10f, 0.83f)
        }
        KEY_CENTERS_DE = mapDe
    }

    fun centers(language: String = "es"): Map<Char, KeyCenter> = when (language) {
        "en" -> KEY_CENTERS_EN
        "fr" -> KEY_CENTERS_FR
        "de" -> KEY_CENTERS_DE
        else -> KEY_CENTERS_ES
    }

    val KEY_CENTERS: Map<Char, KeyCenter> get() = KEY_CENTERS_ES

    fun findKeysNear(x: Float, y: Float, maxDistance: Float = 0.22f, language: String = "es"): List<Char> {
        return centers(language).values
            .map { it.char to hypot(it.x - x, it.y - y) }
            .filter { it.second <= maxDistance }
            .sortedBy { it.second }
            .map { it.first }
    }

    fun nearestKey(x: Float, y: Float, language: String = "es"): Char? {
        return centers(language).values
            .minByOrNull { hypot(it.x - x, it.y - y) }
            ?.char
    }
}
