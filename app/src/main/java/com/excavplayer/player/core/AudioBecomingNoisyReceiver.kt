package com.excavplayer.player.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.excavplayer.core.logging.AppLogger

class AudioBecomingNoisyReceiver(
    private val context: Context,
    private val logger: AppLogger,
    private val onBecomingNoisy: () -> Unit
) : BroadcastReceiver() {

    private var isRegistered = false

    fun register() {
        if (!isRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(this, filter)
            isRegistered = true
            logger.d("AudioBecomingNoisy", "Receiver registered")
        }
    }

    fun unregister() {
        if (isRegistered) {
            runCatching { context.unregisterReceiver(this) }
            isRegistered = false
            logger.d("AudioBecomingNoisy", "Receiver unregistered")
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            logger.i("AudioBecomingNoisy", "Audio becoming noisy received -> triggering pause")
            onBecomingNoisy()
        }
    }
}
