package com.iqforge.engine

/** The stable interface shared by heuristic and native on-device code engines. */
interface CodeEngine {
    suspend fun write(instruction: String, fileContext: String): String
    suspend fun review(diff: String): List<Finding>
    suspend fun debug(stackTrace: String, fileContext: String): String
    suspend fun explain(snippet: String): String
}

data class Finding(val line: Int, val severity: Severity, val message: String)

enum class Severity { INFO, WARNING, BUG }
