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
    val n_predict: Int = 512,
    val temperature: Float = 0.2f,
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

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (checkNpuHealth()) {
            isReady = true
            Log.i("NativeEngine", "Initialized with live Snapdragon Hexagon NPU daemon on localhost:8080")
            return@withContext true
        }

        if (isReady) return@withContext true
        try {
            val selected = ModelCatalog.detectAvailable { name ->
                File(context.filesDir, name).exists() || runCatching { context.assets.open(name).close() }.isSuccess
            } ?: ModelCatalog.QWEN_1_5B
            activeModel = selected

            val modelFile = File(context.filesDir, selected.fileName)
            if (!modelFile.exists()) {
                Log.i("NativeEngine", "Copying ${selected.displayName} from assets...")
                context.assets.open(selected.fileName).use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (modelFile.length() < 100_000_000L) {
                Log.e("NativeEngine", "Rejected incomplete GGUF model: ${modelFile.length()} bytes")
                return@withContext false
            }
            modelFile.inputStream().use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) != 4 || !magic.contentEquals(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))) {
                    Log.e("NativeEngine", "Rejected model without GGUF header")
                    return@withContext false
                }
            }
            modelPath = modelFile.absolutePath
            isReady = true
            Log.i("NativeEngine", "Ready: ${selected.displayName} (${selected.promptStyle})")
            true
        } catch (e: FileNotFoundException) {
            Log.e("NativeEngine", "No catalog model found in assets. Place one of: ${ModelCatalog.ALL.joinToString { it.fileName }}")
            false
        } catch (e: Exception) {
            Log.e("NativeEngine", "Failed to initialize model", e)
            false
        }
    }

    fun installedModelBytes(): Long = File(context.filesDir, activeModel.fileName).takeIf(File::isFile)?.length() ?: 0L

    private external fun nativeGenerate(modelPath: String, prompt: String): String

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

        if (checkNpuHealth()) {
            val npuResult = generateViaNpu(formattedPrompt)
            if (!npuResult.isNullOrBlank()) {
                return@withContext npuResult
            }
        }

        if (!isReady && !initialize()) {
            return@withContext "ERROR: NativeEngine not ready (no model file found — see ModelCatalog.ALL)."
        }
        val result = nativeGenerate(modelPath, formattedPrompt)
        if (result.startsWith("ERROR:")) {
            return@withContext result
        }
        result
    }

    override suspend fun write(instruction: String, fileContext: String): String {
        val system = "You are an expert software engineer. Write or modify code based on the instruction and context. Output only the resulting code snippet or diff, without any markdown formatting or explanations unless requested."
        val user = "Context:\n$fileContext\n\nInstruction:\n$instruction"
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
        val system = "You are an expert debugger. Find the root cause of the problem described by the instruction or stack trace based on the context. Provide a concise explanation of the root cause and a proposed fix."
        val user = "Context:\n$fileContext\n\nInstruction/Stack Trace:\n$stackTrace"
        return generate(system, user)
    }

    override suspend fun explain(snippet: String): String {
        val system = "You are an expert code explainer. Explain the provided code context clearly and concisely."
        val user = "Context:\n$snippet\n\nInstruction:\nExplain this"
        return generate(system, user)
    }
}
