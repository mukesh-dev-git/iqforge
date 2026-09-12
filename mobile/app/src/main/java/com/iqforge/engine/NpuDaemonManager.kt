package com.iqforge.engine

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Manages the Qualcomm Snapdragon Hexagon NPU daemon (llama-server with HTP offload).
 *
 * Standalone Mode:
 * If the daemon is asleep or killed after a phone reboot, uses Shizuku (which runs
 * under the elevated 'shell' UID 2000) to start the daemon directly on the phone
 * without requiring any laptop, PC, or ADB cable.
 */
object NpuDaemonManager {
    private const val TAG = "NpuDaemonManager"
    private const val HEALTH_URL = "http://127.0.0.1:8080/health"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    suspend fun isServerAlive(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(HEALTH_URL).get().build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int = 8080) {
        if (!isShizukuAvailable()) return
        try {
            Shizuku.requestPermission(requestCode)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", t)
        }
    }

    fun openDeveloperOptions(context: android.content.Context) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open developer options", t)
        }
    }

    fun openShizuku(context: android.content.Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent != null) context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open Shizuku", t)
        }
    }

    suspend fun waitForShizuku(timeoutMs: Long = 2000): Boolean = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isShizukuAvailable()) return@withContext true
            delay(150)
        }
        isShizukuAvailable()
    }

    /**
     * Terminates the running Hexagon HTP daemon via Shizuku shell.
     */
    suspend fun stopServer(): Boolean = withContext(Dispatchers.IO) {
        if (!waitForShizuku()) return@withContext false
        if (!hasShizukuPermission()) return@withContext false
        try {
            Log.i(TAG, "Stopping NPU daemon via Shizuku...")
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            newProcessMethod.invoke(null, arrayOf("sh", "-c", "pkill -9 -f llama-server"), null, null)
            for (i in 1..15) {
                delay(150)
                if (!isServerAlive()) {
                    Log.i(TAG, "NPU daemon successfully stopped")
                    return@withContext true
                }
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error stopping NPU daemon via Shizuku", t)
            false
        }
    }

    /**
     * Spawns the Hexagon HTP daemon on device via Shizuku shell.
     */
    suspend fun startNpuDaemon(
        modelPath: String = "/data/local/tmp/gguf/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
    ): Boolean = withContext(Dispatchers.IO) {
        if (!waitForShizuku()) {
            Log.w(TAG, "Cannot start NPU daemon: Shizuku service not available")
            return@withContext false
        }
        if (!hasShizukuPermission()) {
            Log.w(TAG, "Cannot start NPU daemon: Shizuku permission not granted")
            return@withContext false
        }

        // Clean up any stale llama-server process first
        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            newProcessMethod.invoke(null, arrayOf("sh", "-c", "pkill -9 -f llama-server"), null, null)
            delay(200)
        } catch (t: Throwable) {
            Log.w(TAG, "Stale process cleanup notice", t)
        }

        val launchCommand = "export LD_LIBRARY_PATH=/data/local/tmp/llama.cpp/lib; " +
            "export ADSP_LIBRARY_PATH=/data/local/tmp/llama.cpp/lib; " +
            "nohup /data/local/tmp/llama.cpp/bin/llama-server " +
            "-m \"$modelPath\" " +
            "--host 127.0.0.1 --port 8080 -ngl 99 --device HTP0 " +
            "</dev/null >/data/local/tmp/llama-server.log 2>&1 &"

        try {
            Log.i(TAG, "Executing NPU launch via Shizuku shell with model: $modelPath")
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            newProcessMethod.invoke(null, arrayOf("sh", "-c", launchCommand), null, null)
            Log.i(TAG, "NPU daemon spawn command dispatched successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Error spawning NPU daemon via Shizuku", t)
            return@withContext false
        }

        // Poll for health check (HTP model initialization typically takes 1.5 - 3 seconds)
        for (i in 1..20) {
            delay(300)
            if (isServerAlive()) {
                Log.i(TAG, "NPU server is alive and responding on port 8080!")
                return@withContext true
            }
        }
        false
    }

    /**
     * Ensures the NPU daemon is online. If offline and Shizuku is authorized,
     * wakes it up automatically.
     */
    suspend fun ensureNpuAwake(
        modelPath: String = "/data/local/tmp/gguf/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
    ): Boolean = withContext(Dispatchers.IO) {
        if (isServerAlive()) return@withContext true
        if (waitForShizuku() && hasShizukuPermission()) {
            return@withContext startNpuDaemon(modelPath)
        }
        false
    }
}
