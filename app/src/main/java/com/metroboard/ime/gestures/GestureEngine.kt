package com.metroboard.ime.gestures

data class GesturePoint(val x: Float, val y: Float, val timeMillis: Long)

/** Extension contracts only. No path capture or decoding is active in v0.1. */
interface GestureEngine {
    fun start(point: GesturePoint)
    fun move(point: GesturePoint)
    fun finish(): List<GesturePoint>
    fun cancel()
}

fun interface ShapeWritingDecoder {
    fun decode(path: List<GesturePoint>): List<String>
}
