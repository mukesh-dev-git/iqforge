package com.iqforge.sensors

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Pulses the rear flash while [active] is true — a visible "the model is working" cue for when
 * the phone is face-down, across the room, or the screen just isn't being watched. Silently does
 * nothing on hardware without a flash, or if the OS refuses torch control (e.g. another app is
 * mid-capture) — this is a nice-to-have indicator, never worth crashing or blocking generation
 * over. `CameraManager.setTorchMode` needs no runtime permission by itself; CAMERA is already
 * declared in the manifest for the attach-from-camera feature.
 */
@Composable
fun TorchFeedback(active: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return@LaunchedEffect
        val cameraId = runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return@LaunchedEffect

        try {
            var on = false
            while (true) {
                on = !on
                runCatching { cameraManager.setTorchMode(cameraId, on) }
                delay(350)
            }
        } finally {
            // Runs on cancellation too (LaunchedEffect relaunches when `active` flips to false,
            // which cancels this coroutine) — guarantees the torch never gets stuck on.
            runCatching { cameraManager.setTorchMode(cameraId, false) }
        }
    }
}
