package com.excavplayer.player.core

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.excavplayer.core.logging.AppLogger

class AudioBecomingNoisyReceiver(
    private val context: Context,
    private val logger: AppLogger,
    private val onHeadsetDisconnected: () -> Unit,
    private val onHeadsetConnected: () -> Unit
) : BroadcastReceiver() {

    private var isRegistered = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialPlugIgnored = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val hasHeadset = addedDevices?.any { isHeadsetDevice(it) } == true
            if (hasHeadset) {
                logger.i(TAG, "AudioDeviceCallback: Headset connected")
                onHeadsetConnected()
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val hasHeadset = removedDevices?.any { isHeadsetDevice(it) } == true
            if (hasHeadset) {
                logger.i(TAG, "AudioDeviceCallback: Headset disconnected")
                onHeadsetDisconnected()
            }
        }
    }

    fun register() {
        if (!isRegistered) {
            initialPlugIgnored = false
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            }
            try {
                ContextCompat.registerReceiver(
                    context,
                    this,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            } catch (e: Exception) {
                logger.w(TAG, "Failed to register broadcast receiver: ${e.message}")
            }

            try {
                audioManager?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
            } catch (e: Exception) {
                logger.w(TAG, "Failed to register audio device callback: ${e.message}")
            }

            isRegistered = true
            logger.d(TAG, "Headset detection receiver registered")
        }
    }

    fun unregister() {
        if (isRegistered) {
            runCatching { context.unregisterReceiver(this) }
            runCatching { audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback) }
            isRegistered = false
            logger.d(TAG, "Headset detection receiver unregistered")
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                logger.i(TAG, "ACTION_AUDIO_BECOMING_NOISY received -> triggering disconnect")
                onHeadsetDisconnected()
            }
            Intent.ACTION_HEADSET_PLUG -> {
                // ACTION_HEADSET_PLUG is a sticky broadcast and fires immediately upon registration with current state
                val state = intent.getIntExtra("state", -1)
                if (!initialPlugIgnored) {
                    initialPlugIgnored = true
                    return
                }
                if (state == 0) {
                    logger.i(TAG, "ACTION_HEADSET_PLUG unplugged -> triggering disconnect")
                    onHeadsetDisconnected()
                } else if (state == 1) {
                    logger.i(TAG, "ACTION_HEADSET_PLUG plugged -> triggering connect")
                    onHeadsetConnected()
                }
            }
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED, BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                    logger.i(TAG, "Bluetooth disconnected -> triggering disconnect")
                    onHeadsetDisconnected()
                } else if (state == BluetoothHeadset.STATE_CONNECTED) {
                    logger.i(TAG, "Bluetooth connected -> triggering connect")
                    onHeadsetConnected()
                }
            }
        }
    }

    private fun isHeadsetDevice(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID -> true
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                } else {
                    false
                }
            }
        }
    }

    companion object {
        private const val TAG = "HeadsetReceiver"
    }
}
