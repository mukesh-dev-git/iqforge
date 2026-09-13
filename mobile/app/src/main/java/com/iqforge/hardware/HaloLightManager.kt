package com.iqforge.hardware

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the camera hardware lighting effects on iQOO and modern Android devices.
 * Specifically controls the RGB camera ring ("Monster Halo" / "Energy Halo") on the
 * iQOO 15 (via VivoLightManager) while the AI model is actively inferring and thinking.
 * Also provides a smooth torch / flash breathing pulse fallback for devices without an RGB ring.
 */
object HaloLightManager {

    private const val TAG = "HaloLightManager"
    private const val PREFS_NAME = "iqforge_halo_prefs"
    private const val KEY_MODE = "halo_mode"
    private const val KEY_EFFECT = "halo_effect"
    private const val KEY_COLOR = "halo_color"

    enum class HaloMode(val displayName: String, val description: String) {
        MONSTER_HALO("Monster Halo (Camera Ring)", "RGB ambient light ring around rear camera"),
        CAMERA_TORCH("Flashlight Pulse", "Gentle breathing strobe from camera LED"),
        BOTH("Halo & Flashlight", "Both RGB camera ring and flashlight pulse"),
        DISABLED("Disabled", "No hardware lighting while thinking")
    }

    enum class HaloEffect(val displayName: String, val subType: Int) {
        BREATHING("Slow Breathing (Huxi)", 2),
        FLOWING("Flowing Streamer (Liuguang)", 3),
        PULSE("Rhythmic Pulse (Maichong)", 5),
        HEARTBEAT("Heartbeat (Xintiao)", 1)
    }

    enum class HaloColor(val displayName: String, val idx: Int) {
        CYAN("Cyber Cyan", 2),
        BLUE("Galactic Blue", 1),
        ORANGE("Sunset Orange", 3),
        RED("Crimson Red", 4),
        PURPLE("Neon Purple", 5),
        WHITE("Pure White", 6)
    }

    private var prefs: SharedPreferences? = null
    private val isPulsing = AtomicBoolean(false)
    private var torchJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Cached reflection references
    private var vlmInstance: Any? = null
    private var startLightForGameByTimesMethod: Method? = null
    private var stopLightAllMethod: Method? = null
    private var hasHardwareChecked = false
    var isHaloHardwareSupported: Boolean = false
        private set

    var mode: HaloMode = HaloMode.MONSTER_HALO
        set(value) {
            field = value
            prefs?.edit()?.putString(KEY_MODE, value.name)?.apply()
        }

    var effect: HaloEffect = HaloEffect.BREATHING
        set(value) {
            field = value
            prefs?.edit()?.putString(KEY_EFFECT, value.name)?.apply()
        }

    var color: HaloColor = HaloColor.CYAN
        set(value) {
            field = value
            prefs?.edit()?.putString(KEY_COLOR, value.name)?.apply()
        }

    fun init(context: Context) {
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val savedMode = prefs?.getString(KEY_MODE, null)
        val savedEffect = prefs?.getString(KEY_EFFECT, null)
        val savedColor = prefs?.getString(KEY_COLOR, null)

        mode = if (savedMode != null) {
            try { HaloMode.valueOf(savedMode) } catch (_: Exception) { HaloMode.MONSTER_HALO }
        } else HaloMode.MONSTER_HALO

        effect = if (savedEffect != null) {
            try { HaloEffect.valueOf(savedEffect) } catch (_: Exception) { HaloEffect.BREATHING }
        } else HaloEffect.BREATHING

        color = if (savedColor != null) {
            try { HaloColor.valueOf(savedColor) } catch (_: Exception) { HaloColor.CYAN }
        } else HaloColor.CYAN

        checkHardwareSupport()
    }

    fun onShizukuReady() {
        if (!isHaloHardwareSupported) {
            ensureHiddenApiExemption()
            hasHardwareChecked = false
            checkHardwareSupport()
        }
    }

    private fun ensureHiddenApiExemption() {
        try {
            if (rikka.shizuku.Shizuku.pingBinder() &&
                rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val newProcessMethod = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
                val cmd = "settings put global hidden_api_policy 1; " +
                          "settings put global hidden_api_policy_p_apps 1; " +
                          "settings put global hidden_api_policy_pre_p_apps 1"
                newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null)
                Log.i(TAG, "Hidden API policy 1 configured via Shizuku")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not apply hidden API policy via Shizuku: ${t.message}")
        }
    }

    private fun checkHardwareSupport() {
        if (hasHardwareChecked) return
        hasHardwareChecked = true
        ensureHiddenApiExemption()
        try {
            val vlmClass = Class.forName("com.vivo.framework.vivolight.VivoLightManager")
            val getInstanceMethod = vlmClass.getMethod("getInstance")
            vlmInstance = getInstanceMethod.invoke(null)
            if (vlmInstance != null) {
                startLightForGameByTimesMethod = vlmClass.getMethod(
                    "startLightForGameByTimes",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
                stopLightAllMethod = vlmClass.getMethod("stopLightAll")
                isHaloHardwareSupported = true
                Log.i(TAG, "VivoLightManager successfully initialized. Monster Halo hardware ready.")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "VivoLightManager not available on this platform: ${e.message}")
            isHaloHardwareSupported = false
            if (mode == HaloMode.MONSTER_HALO) {
                mode = HaloMode.CAMERA_TORCH
            }
        }
    }

    /**
     * Activates the hardware light effect when model starts thinking.
     * Safe to call multiple times; idempotent.
     */
    fun startThinkingPulse(context: Context) {
        init(context)
        if (mode == HaloMode.DISABLED) return
        if (isPulsing.getAndSet(true)) return // Already active

        Log.d(TAG, "Starting thinking light animation (Mode: $mode, Effect: $effect, Color: $color)")

        // 1. Activate Monster Halo (RGB Camera Ring)
        if ((mode == HaloMode.MONSTER_HALO || mode == HaloMode.BOTH) && isHaloHardwareSupported) {
            try {
                val gameJson = "{\"game\":{\"id\":1,\"subId\":1,\"type\":1,\"defaultSubtype\":${effect.subType},\"defaultColorIdx\":${color.idx},\"customSubtype\":${effect.subType},\"customColorIdx\":${color.idx}}}"
                startLightForGameByTimesMethod?.invoke(vlmInstance, "com.iqforge", gameJson, 999, true)
                Log.i(TAG, "Monster Halo activated with ${effect.displayName}")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to start Monster Halo: ${e.message}")
            }
        }

        // 2. Activate Camera Flash Pulse
        if (mode == HaloMode.CAMERA_TORCH || mode == HaloMode.BOTH || (!isHaloHardwareSupported && mode != HaloMode.DISABLED)) {
            startTorchPulse(context.applicationContext)
        }
    }

    /**
     * Shuts off all hardware lighting immediately when inference completes.
     */
    fun stopThinkingPulse() {
        if (!isPulsing.getAndSet(false)) return
        Log.d(TAG, "Stopping thinking light animation")

        // Stop Monster Halo
        if (isHaloHardwareSupported) {
            try {
                stopLightAllMethod?.invoke(vlmInstance)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to stop Monster Halo: ${e.message}")
            }
        }

        // Stop Torch Pulse
        stopTorchPulse()
    }

    /**
     * Preview helper to test lighting animation for a short duration.
     */
    fun testLight(context: Context, durationMs: Long = 3000L) {
        scope.launch {
            startThinkingPulse(context)
            delay(durationMs)
            stopThinkingPulse()
        }
    }

    private var activeCameraId: String? = null
    private var cameraManager: CameraManager? = null

    private fun startTorchPulse(context: Context) {
        torchJob?.cancel()
        torchJob = scope.launch {
            try {
                if (cameraManager == null) {
                    cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                }
                val cm = cameraManager ?: return@launch

                if (activeCameraId == null) {
                    for (id in cm.cameraIdList) {
                        val chars = cm.getCameraCharacteristics(id)
                        val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                        val facing = chars.get(CameraCharacteristics.LENS_FACING)
                        if (flashAvailable && facing == CameraCharacteristics.LENS_FACING_BACK) {
                            activeCameraId = id
                            break
                        }
                    }
                    if (activeCameraId == null && cm.cameraIdList.isNotEmpty()) {
                        activeCameraId = cm.cameraIdList[0]
                    }
                }

                val camId = activeCameraId ?: return@launch
                val maxStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    cm.getCameraCharacteristics(camId).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                } else 1

                while (isActive && isPulsing.get()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxStrength > 1) {
                        // Smooth breathing step
                        val lowStrength = 1.coerceAtMost(maxStrength)
                        cm.turnOnTorchWithStrengthLevel(camId, lowStrength)
                        delay(250)
                        val medStrength = (maxStrength / 2).coerceAtLeast(1)
                        cm.turnOnTorchWithStrengthLevel(camId, medStrength)
                        delay(250)
                        cm.setTorchMode(camId, false)
                        delay(350)
                    } else {
                        // Clean rhythmic blink
                        cm.setTorchMode(camId, true)
                        delay(200)
                        cm.setTorchMode(camId, false)
                        delay(400)
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Torch pulse error: ${e.message}")
            } finally {
                try {
                    activeCameraId?.let { cameraManager?.setTorchMode(it, false) }
                } catch (_: Exception) {}
            }
        }
    }

    private fun stopTorchPulse() {
        torchJob?.cancel()
        torchJob = null
        try {
            activeCameraId?.let { cameraManager?.setTorchMode(it, false) }
        } catch (_: Exception) {}
    }
}
