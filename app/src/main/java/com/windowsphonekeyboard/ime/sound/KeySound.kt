package com.windowsphonekeyboard.ime.sound

import android.content.Context
import android.media.AudioManager
import com.windowsphonekeyboard.ime.keyboard.KeyAction

class KeySound(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    fun play(enabled: Boolean, action: KeyAction = KeyAction.TEXT) {
        if (!enabled || audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val effect = when (action) {
            KeyAction.DELETE -> AudioManager.FX_KEYPRESS_DELETE
            KeyAction.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyAction.ENTER -> AudioManager.FX_KEYPRESS_RETURN
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audio?.playSoundEffect(effect)
    }
}
