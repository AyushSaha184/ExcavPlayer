package com.excavplayer.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * True when the system requests reduced/no animations
 * (Accessibility > Remove animations, or animator scale = 0).
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scale by remember { mutableFloatStateOf(1f) }

    DisposableEffect(lifecycleOwner) {
        val resolver = context.contentResolver
        fun read() {
            scale = Settings.Global.getFloat(
                resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
        }
        read()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) read()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { read() }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            contentObserver
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            resolver.unregisterContentObserver(contentObserver)
        }
    }
    return scale == 0f
}
