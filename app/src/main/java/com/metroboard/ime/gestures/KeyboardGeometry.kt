package com.metroboard.ime.gestures

import kotlin.math.hypot

data class KeyCenter(val char: Char, val x: Float, val y: Float)

object KeyboardGeometry {
    val KEY_CENTERS_ES: Map<Char, KeyCenter>
    val KEY_CENTERS_EN: Map<Char, KeyCenter>

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
    }

    fun centers(language: String = "es"): Map<Char, KeyCenter> =
        if (language == "en") KEY_CENTERS_EN else KEY_CENTERS_ES

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
