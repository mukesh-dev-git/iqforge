package com.iqforge.sensors

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** One buzz pattern per severity — a BUG finding should feel different from a routine reply. */
enum class HapticCue { REPLY, BUG, ERROR }

fun Context.buzz(cue: HapticCue) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return

    val pattern = when (cue) {
        HapticCue.REPLY -> longArrayOf(0, 40)
        HapticCue.BUG -> longArrayOf(0, 80, 50, 120)
        HapticCue.ERROR -> longArrayOf(0, 60, 40, 60, 40, 60)
    }
    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
}

/**
 * Registers shake / tilt / face-down while this composable is on screen; unregisters on
 * pause so it doesn't drain battery in the background. Add once, near the top of the chat
 * screen's Composable body — see the 3-line activation snippet in the PR/commit message.
 */
@Composable
fun SensorFeedback(
    onShake: () -> Unit = {},
    onFaceDown: (Boolean) -> Unit = {},
    onTiltRight: () -> Unit = {},
    onTiltLeft: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val manager = SensorManager(context).apply {
            onShakeCallback = onShake
            onFaceDownCallback = onFaceDown
            onTiltRightCallback = onTiltRight
            onTiltLeftCallback = onTiltLeft
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> manager.startListening()
                Lifecycle.Event.ON_PAUSE -> manager.stopListening()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            manager.stopListening()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
