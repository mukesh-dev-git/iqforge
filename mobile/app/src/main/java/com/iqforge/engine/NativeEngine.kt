package com.iqforge.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import android.util.Log

/**
 * Runs GGUF inference via llama.cpp JNI. Which model it loads is decided once, at
 * [initialize] time, by [ModelCatalog.detectAvailable] — whichever catalog entry's file is
 * actually present (assets, or already copied to `filesDir` from a previous run) wins, checked
 * in catalog order. Ship only `ModelCatalog.QWEN_1_5B` in assets/ and this behaves exactly as
 * before; drop in `qwen2.5-coder-3b-instruct-q4_k_m.gguf` or `phi-4-mini-instruct-q4_k_m.gguf`
 * (matching filenames from ModelCatalog.kt) and the app picks it up with no code change.
 */
class NativeEngine(private val context: Context) : CodeEngine {
    private var isReady = false
    private var modelPath: String = ""
    private var activeModel: ModelInfo = ModelCatalog.QWEN_1_5B

    val displayName: String get() = activeModel.displayName
    val modelFileName: String get() = activeModel.fileName

    init {
        System.loadLibrary("llama-android")
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
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

    /** Wraps a raw instruction+context prompt in the active model's expected chat template. */
    private fun wrapPrompt(system: String, user: String): String = when (activeModel.promptStyle) {
        PromptStyle.CHATML ->
            "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"
        PromptStyle.PHI ->
            "<|system|>$system<|end|><|user|>$user<|end|><|assistant|>"
    }

    private suspend fun generate(system: String, user: String): String = withContext(Dispatchers.Default) {
        if (!isReady && !initialize()) {
            return@withContext "ERROR: NativeEngine not ready (no model file found — see ModelCatalog.ALL)."
        }
        val result = nativeGenerate(modelPath, wrapPrompt(system, user))
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
