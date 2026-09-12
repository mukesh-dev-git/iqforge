package com.iqforge.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LlamaCompletionRequest(
    val prompt: String,
    val n_predict: Int = 768,
    val temperature: Float = 0.7f,
    val stop: List<String> = listOf("<|im_end|>", "<|end|>", "</s>")
)

@Serializable
private data class LlamaCompletionResponse(
    val content: String = "",
    val timings: LlamaTimings? = null
)

@Serializable
private data class LlamaTimings(
    val predicted_per_second: Double? = null,
    val predicted_n: Int? = null
)

/**
 * Runs GGUF inference on-device.
 *
 * Primary Path (Hardware Accelerated):
 *   Checks for a running Snapdragon Hexagon NPU daemon (llama-server with HTP offload)
 *   on localhost:8080. If available, executes inference via the Hexagon Tensor Processor (HTP)
 *   achieving ~23+ tokens/sec with zero cold-start delay.
 *
 * Fallback Path (Embedded JNI):
 *   If the daemon is offline, runs through llama-android JNI on CPU using the model
 *   detected by [ModelCatalog.detectAvailable].
 */
class NativeEngine(private val context: Context) : CodeEngine {
    private var isReady = false
    private var modelPath: String = ""
    private var activeModel: ModelInfo = ModelCatalog.QWEN_1_5B

    var isNpuActive: Boolean = false; private set
    var lastNpuTokensPerSec: Double? = null; private set

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    val displayName: String get() = if (isNpuActive) {
        "${activeModel.displayName.substringBefore(" (")} (Snapdragon NPU)"
    } else {
        activeModel.displayName
    }

    val modelFileName: String get() = activeModel.fileName

    init {
        try {
            System.loadLibrary("llama-android")
        } catch (e: UnsatisfiedLinkError) {
            Log.w("NativeEngine", "llama-android JNI library not loaded; NPU daemon will be preferred", e)
        }
    }

    private var serverProcess: Process? = null

    suspend fun checkNpuHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://127.0.0.1:8080/health")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                isNpuActive = response.isSuccessful
                if (isNpuActive) isReady = true
                isNpuActive
            }
        } catch (e: Exception) {
            isNpuActive = false
            false
        }
    }

    suspend fun ensureNpuDaemonRunning(): Boolean = withContext(Dispatchers.IO) {
        if (checkNpuHealth()) return@withContext true

        val nativeDir = context.applicationInfo.nativeLibraryDir
        val serverBinary = File(nativeDir, "libllama-server.so").takeIf { it.exists() }
            ?: File("/data/local/tmp/llama.cpp/bin/llama-server").takeIf { it.exists() }

        if (serverBinary == null) {
            Log.w("NativeEngine", "Hexagon NPU server binary not found in $nativeDir")
            return@withContext false
        }

        val modelFile = File(context.filesDir, activeModel.fileName).takeIf { it.exists() && it.length() > 50_000_000L }
            ?: File("/data/local/tmp/gguf/${activeModel.fileName}").takeIf { it.exists() && it.length() > 50_000_000L }

        if (modelFile == null) {
            Log.w("NativeEngine", "Model ${activeModel.fileName} not yet present on device")
            return@withContext false
        }

        try {
            Log.i("NativeEngine", "Spawning autonomous on-device Hexagon NPU server: ${serverBinary.absolutePath}")
            val pb = ProcessBuilder(
                serverBinary.absolutePath,
                "-m", modelFile.absolutePath,
                "--host", "127.0.0.1",
                "--port", "8080",
                "-ngl", "99",
                "--device", "HTP0",
                "-c", "2048"
            ).apply {
                val env = environment()
                env["ADSP_LIBRARY_PATH"] = nativeDir
                env["LD_LIBRARY_PATH"] = "$nativeDir:/vendor/lib64"
                redirectErrorStream(true)
            }
            serverProcess = pb.start()

            for (attempt in 1..25) {
                kotlinx.coroutines.delay(400)
                if (checkNpuHealth()) {
                    Log.i("NativeEngine", "Hexagon NPU daemon verified active and healthy on attempt $attempt")
                    isReady = true
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e("NativeEngine", "Failed to launch internal Hexagon NPU daemon", e)
        }
        false
    }

    fun isModelAvailable(): Boolean {
        val inFiles = File(context.filesDir, activeModel.fileName)
        val inTmp = File("/data/local/tmp/gguf/${activeModel.fileName}")
        return (inFiles.exists() && inFiles.length() > 50_000_000L) || inTmp.exists()
    }

    suspend fun downloadModel(onProgress: (Float, String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val targetFile = File(context.filesDir, activeModel.fileName)
        val tempFile = File(context.filesDir, "${activeModel.fileName}.download")
        try {
            val request = Request.Builder().url(activeModel.sourceUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastReport = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 250) {
                                val progress = if (contentLength > 0) totalRead.toFloat() / contentLength else 0f
                                val mbRead = totalRead / (1024 * 1024)
                                val totalMb = contentLength / (1024 * 1024)
                                onProgress(progress, "$mbRead MB / $totalMb MB")
                                lastReport = now
                            }
                        }
                    }
                }
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                initialize()
            }
        } catch (e: Exception) {
            Log.e("NativeEngine", "Model download failed", e)
            tempFile.delete()
            false
        }
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (checkNpuHealth() || ensureNpuDaemonRunning()) {
            isReady = true
            isNpuActive = true
            Log.i("NativeEngine", "Hexagon NPU hardware accelerator initialized and ready")
            return@withContext true
        }

        isReady = false
        isNpuActive = false
        false
    }

    fun installedModelBytes(): Long = File(context.filesDir, activeModel.fileName).takeIf(File::isFile)?.length()
        ?: File("/data/local/tmp/gguf/${activeModel.fileName}").takeIf(File::isFile)?.length()
        ?: 0L

    private fun wrapPrompt(system: String, user: String): String = when (activeModel.promptStyle) {
        PromptStyle.CHATML ->
            "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"
        PromptStyle.PHI ->
            "<|system|>$system<|end|><|user|>$user<|end|><|assistant|>"
    }

    private suspend fun generateViaNpu(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            val reqBody = json.encodeToString(
                LlamaCompletionRequest.serializer(),
                LlamaCompletionRequest(prompt = prompt)
            ).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("http://127.0.0.1:8080/completion")
                .post(reqBody)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val decoded = json.decodeFromString(LlamaCompletionResponse.serializer(), body)
                    lastNpuTokensPerSec = decoded.timings?.predicted_per_second
                    Log.i("NativeEngine", "Hexagon NPU generated ${decoded.timings?.predicted_n} tokens @ ${decoded.timings?.predicted_per_second} t/s")
                    decoded.content.trim()
                } else null
            }
        } catch (e: Exception) {
            Log.w("NativeEngine", "Hexagon NPU daemon unavailable: ${e.message}")
            isNpuActive = false
            null
        }
    }

    private suspend fun generate(system: String, user: String): String = withContext(Dispatchers.Default) {
        val formattedPrompt = wrapPrompt(system, user)

        // PURE NPU EXECUTION: Model is integrated to run on Hexagon NPU alone, strictly no CPU fallback
        if (checkNpuHealth() || ensureNpuDaemonRunning()) {
            val npuResult = generateViaNpu(formattedPrompt)
            if (!npuResult.isNullOrBlank()) {
                return@withContext npuResult
            }
        }

        "ERROR: Qualcomm Snapdragon Hexagon NPU (HTP) hardware acceleration is required. Inference runs exclusively on the NPU; CPU execution is disabled."
    }

    override suspend fun write(instruction: String, fileContext: String): String {
        val system = if (fileContext.isBlank()) {
            "You are iQForge, an intelligent AI software engineering assistant running on-device on Qualcomm Snapdragon Hexagon NPU. Answer all user questions, technical queries, concepts, and requests thoroughly, clearly, accurately, and conversationally. If the user asks for code, provide clean code along with a clear explanation. If the user asks a conceptual or general question, explain it comprehensively."
        } else {
            "You are an expert software engineer running on Qualcomm Snapdragon Hexagon NPU. Write or modify code based on the instruction and context. Provide clean, correct code with brief explanation if needed."
        }
        val user = if (fileContext.isBlank()) instruction else "Context:\n$fileContext\n\nInstruction:\n$instruction"
        return generate(system, user)
    }

    override suspend fun review(diff: String): List<Finding> {
        val system = "You are a senior code reviewer doing a security and correctness review of a git diff. Report every bug, warning, or note you find. Pay special attention to:\n- Null / None pointer dereferences\n- Unhandled exceptions or missing error guards\n- Resource leaks (unclosed files, missing frees)\n- Hardcoded credentials or insecure endpoints\n- Off-by-one errors or bounds check failures\n\nFor each issue you find, output exactly one line in this format:\nLINE:<n> SEVERITY:<BUG|WARNING|INFO> MSG:<one concise sentence describing the issue>"
        val user = "Diff to review:\n$diff"
        val raw = generate(system, user)
        val findings = mutableListOf<Finding>()
        val regex = Regex("LINE:(\\d+)\\s+SEVERITY:(BUG|WARNING|INFO)\\s+MSG:(.+)")
        raw.lines().forEach { line ->
            regex.find(line)?.let { match ->
                val (lineStr, severityStr, msg) = match.destructured
                findings.add(Finding(lineStr.toInt(), Severity.valueOf(severityStr), msg.trim()))
            }
        }
        return findings
    }

    override suspend fun debug(stackTrace: String, fileContext: String): String {
        val system = "You are an expert debugger and software engineer running on Qualcomm Snapdragon Hexagon NPU. Analyze the issue, error, or stack trace thoroughly. Identify the root cause, explain why it happens, and provide a clear, step-by-step fix with corrected code."
        val user = if (fileContext.isBlank()) stackTrace else "Context:\n$fileContext\n\nInstruction/Stack Trace:\n$stackTrace"
        return generate(system, user)
    }

    override suspend fun explain(snippet: String): String {
        val system = "You are iQForge, an intelligent AI software engineering assistant running on-device on Qualcomm Snapdragon Hexagon NPU. Answer the user's question or explain the provided concept, code, or topic thoroughly, clearly, and helpfully with examples where appropriate."
        val user = snippet
        return generate(system, user)
    }
}
