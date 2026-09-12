package com.iqforge.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import android.util.Log

class NativeEngine(private val context: Context) : CodeEngine {
    val modelFileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
    val displayName = "Qwen2.5 Coder 1.5B (on-device)"
    private var isReady = false
    private var modelPath: String = ""

    init {
        System.loadLibrary("llama-android")
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isReady) return@withContext true
        try {
            val modelFile = File(context.filesDir, modelFileName)
            if (!modelFile.exists()) {
                Log.i("NativeEngine", "Copying model from assets...")
                context.assets.open(modelFileName).use { input ->
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
            true
        } catch (e: FileNotFoundException) {
            Log.e("NativeEngine", "Model file not found in assets: $modelFileName. Please place the GGUF file in mobile/app/src/main/assets/")
            false
        } catch (e: Exception) {
            Log.e("NativeEngine", "Failed to initialize model", e)
            false
        }
    }

    fun installedModelBytes(): Long = File(context.filesDir, modelFileName).takeIf(File::isFile)?.length() ?: 0L

    private external fun nativeGenerate(modelPath: String, prompt: String): String

    private suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        if (!isReady && !initialize()) {
            return@withContext "ERROR: NativeEngine not ready (model missing)."
        }
        val result = nativeGenerate(modelPath, prompt)
        if (result.startsWith("ERROR:")) {
            return@withContext result
        }
        result
    }

    override suspend fun write(instruction: String, fileContext: String): String {
        val prompt = "You are an expert software engineer. Write or modify code based on the instruction and context.\nOutput only the resulting code snippet or diff, without any markdown formatting or explanations unless requested.\n\nContext:\n$fileContext\n\nInstruction:\n$instruction"
        return generate(prompt)
    }

    override suspend fun review(diff: String): List<Finding> {
        val prompt = "You are a senior code reviewer doing a security and correctness review of a git diff.\nReport every bug, warning, or note you find. Pay special attention to:\n- Null / None pointer dereferences\n- Unhandled exceptions or missing error guards\n- Resource leaks (unclosed files, missing frees)\n- Hardcoded credentials or insecure endpoints\n- Off-by-one errors or bounds check failures\n\nFor each issue you find, output exactly one line in this format:\nLINE:<n> SEVERITY:<BUG|WARNING|INFO> MSG:<one concise sentence describing the issue>\n\nDiff to review:\n$diff"
        val raw = generate(prompt)
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
        val prompt = "You are an expert debugger. Find the root cause of the problem described by the instruction or stack trace based on the context.\nProvide a concise explanation of the root cause and a proposed fix.\n\nContext:\n$fileContext\n\nInstruction/Stack Trace:\n$stackTrace"
        return generate(prompt)
    }

    override suspend fun explain(snippet: String): String {
        val prompt = "You are an expert code explainer. Explain the provided code context clearly and concisely based on the instruction.\n\nContext:\n$snippet\n\nInstruction:\nExplain this"
        return generate(prompt)
    }
}
