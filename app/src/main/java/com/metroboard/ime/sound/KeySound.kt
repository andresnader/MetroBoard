/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.metroboard.ime.keyboard.KeyAction
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sin

/**
 * Low-latency, synthesized dry digital click sounds inspired by the crisp tactile feedback
 * of Windows Phone 8.1 Word Flow.
 * 100% original programmatic synthesis, zero proprietary assets.
 */
class KeySound(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var soundPool: SoundPool? = null
    private var soundLetterId = 0
    private var soundSpaceId = 0
    private var soundDeleteId = 0
    private var soundEnterId = 0
    private var isLoaded = false

    init {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build()

            pool.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) isLoaded = true
            }

            val cacheDir = context.cacheDir
            val letterFile = createClickWav(File(cacheDir, "snd_letter.wav"), frequency = 850.0, durationMs = 16)
            val spaceFile = createClickWav(File(cacheDir, "snd_space.wav"), frequency = 480.0, durationMs = 24)
            val deleteFile = createClickWav(File(cacheDir, "snd_delete.wav"), frequency = 380.0, durationMs = 20)
            val enterFile = createClickWav(File(cacheDir, "snd_enter.wav"), frequency = 680.0, durationMs = 24)

            soundLetterId = pool.load(letterFile.absolutePath, 1)
            soundSpaceId = pool.load(spaceFile.absolutePath, 1)
            soundDeleteId = pool.load(deleteFile.absolutePath, 1)
            soundEnterId = pool.load(enterFile.absolutePath, 1)

            soundPool = pool
        } catch (_: Exception) {
            soundPool = null
        }
    }

    fun play(enabled: Boolean, action: KeyAction = KeyAction.TEXT, volumeScale: Float = 0.7f) {
        if (!enabled) return
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        val pool = soundPool
        val vol = volumeScale.coerceIn(0.05f, 1.0f)

        if (pool != null && isLoaded) {
            val soundId = when (action) {
                KeyAction.DELETE -> soundDeleteId
                KeyAction.SPACE -> soundSpaceId
                KeyAction.ENTER -> soundEnterId
                else -> soundLetterId
            }
            if (soundId != 0) {
                pool.play(soundId, vol, vol, 1, 0, 1.0f)
                return
            }
        }

        // System fallback if SoundPool hasn't loaded yet
        val effect = when (action) {
            KeyAction.DELETE -> AudioManager.FX_KEYPRESS_DELETE
            KeyAction.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyAction.ENTER -> AudioManager.FX_KEYPRESS_RETURN
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audioManager?.playSoundEffect(effect, vol)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        isLoaded = false
    }

    companion object {
        private fun createClickWav(file: File, frequency: Double, durationMs: Int): File {
            if (file.exists() && file.length() > 44) return file

            val sampleRate = 44100
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val byteCount = numSamples * 2 // 16-bit mono

            val buffer = ByteBuffer.allocate(44 + byteCount).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            buffer.put("RIFF".toByteArray())
            buffer.putInt(36 + byteCount)
            buffer.put("WAVE".toByteArray())

            // fmt subchunk
            buffer.put("fmt ".toByteArray())
            buffer.putInt(16) // subchunk size
            buffer.putShort(1) // PCM format
            buffer.putShort(1) // Mono channel
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * 2) // byte rate
            buffer.putShort(2) // block align
            buffer.putShort(16) // bits per sample

            // data subchunk
            buffer.put("data".toByteArray())
            buffer.putInt(byteCount)

            // Synthesize dry, sharp digital click with fast exponential decay
            val twoPiF = 2.0 * Math.PI * frequency
            val decayRate = 8.0 / durationMs
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val tMs = t * 1000.0
                val envelope = exp(-decayRate * tMs)
                val sampleValue = (sin(twoPiF * t) * envelope * 24000.0).toInt().coerceIn(-32767, 32767)
                buffer.putShort(sampleValue.toShort())
            }

            FileOutputStream(file).use { it.write(buffer.array()) }
            return file
        }
    }
}
