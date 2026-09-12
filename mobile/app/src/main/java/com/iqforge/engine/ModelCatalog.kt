package com.iqforge.engine

/** Different model families expect different chat-template wrapping around a raw prompt. */
enum class PromptStyle { CHATML, PHI }

/** One on-device model option. `fileName` must match exactly what's placed in assets/. */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sourceUrl: String,
    val approxSizeBytes: Long,
    val promptStyle: PromptStyle,
)

/**
 * The on-device model catalog. Order matters: [detectAvailable] returns the first entry whose
 * file is actually present, so list the preferred/default model first.
 *
 * Only [QWEN_1_5B] ships by default (see ../assets/MODEL_INFO.md) — the other two are optional
 * drop-in alternatives, not required for the app to work.
 */
object ModelCatalog {

    val QWEN_1_5B = ModelInfo(
        id = "qwen-1.5b",
        displayName = "Qwen2.5 Coder 1.5B (on-device, fast)",
        fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        sourceUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        approxSizeBytes = 1_100_000_000L,
        promptStyle = PromptStyle.CHATML,
    )

    val QWEN_3B = ModelInfo(
        id = "qwen-3b",
        displayName = "Qwen2.5 Coder 3B (on-device, stretch)",
        fileName = "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
        sourceUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
        approxSizeBytes = 2_100_000_000L,
        promptStyle = PromptStyle.CHATML,
    )

    val PHI_4_MINI = ModelInfo(
        id = "phi-4-mini",
        displayName = "Phi-4-mini 3.8B (on-device, alternative)",
        fileName = "phi-4-mini-instruct-q4_k_m.gguf",
        // Verify the exact filename on the repo's Files tab before downloading — quantizers
        // vary casing/naming (e.g. "Phi-4-mini-instruct-Q4_K_M.gguf"). Rename after download
        // to match `fileName` above exactly, since NativeEngine looks it up by that name.
        sourceUrl = "https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf",
        approxSizeBytes = 2_491_874_272L,
        promptStyle = PromptStyle.PHI,
    )

    val ALL = listOf(QWEN_1_5B, QWEN_3B, PHI_4_MINI)

    /** First catalog entry whose file exists, checked via [hasFile] (assets or filesDir). */
    fun detectAvailable(hasFile: (String) -> Boolean): ModelInfo? = ALL.firstOrNull { hasFile(it.fileName) }
}
