package com.iqforge.engine

/**
 * Fast, private fallback for the first app release. It makes conservative, explainable
 * observations without downloading a model or modifying a file on the user's behalf.
 */
class OfflineEngine : CodeEngine {
    override suspend fun write(instruction: String, fileContext: String): String {
        val normalizedInstruction = instruction.lowercase()
        val unsafeAccess = Regex("\\b([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)")
            .find(fileContext)?.value
        return when {
            ("null" in normalizedInstruction || "nullable" in normalizedInstruction) && unsafeAccess != null -> {
                "Offline suggestion: guard `$unsafeAccess` before using it. In Kotlin, consider " +
                    "`${unsafeAccess.substringBefore('.') }?.${unsafeAccess.substringAfter('.')} ?: defaultValue`. " +
                    "Review the correct fallback value before applying the edit."
            }
            fileContext.isBlank() -> "Offline suggestion: open a file first so iQForge can make a context-aware edit suggestion."
            else -> "Offline suggestion: I can outline a safe edit for “$instruction”, but a local model or laptop bridge is needed to generate a complete patch."
        }
    }

    override suspend fun review(diff: String): List<Finding> {
        val findings = mutableListOf<Finding>()
        var newLine = 1
        diff.lineSequence().forEach { line ->
            if (line.startsWith("@@")) {
                Regex("\\+(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { newLine = it }
                return@forEach
            }
            if (line.startsWith("+") && !line.startsWith("+++")) {
                val code = line.drop(1)
                when {
                    "!!" in code -> findings += Finding(newLine, Severity.BUG, "Avoid the non-null assertion `!!`; it can crash at runtime.")
                    Regex("(?i)(password|api[_-]?key|secret)\\s*=").containsMatchIn(code) -> findings += Finding(newLine, Severity.BUG, "Possible credential in source; move it to secure configuration.")
                    "println(" in code || "console.log(" in code -> findings += Finding(newLine, Severity.WARNING, "Debug logging should not be left in production code.")
                    "catch (Exception" in code || "catch(Exception" in code -> findings += Finding(newLine, Severity.WARNING, "Catching every exception can hide the actual failure.")
                    "TODO" in code -> findings += Finding(newLine, Severity.INFO, "This change leaves a TODO to resolve before release.")
                }
                newLine++
            } else if (!line.startsWith("-")) {
                newLine++
            }
        }
        return findings
    }

    override suspend fun debug(stackTrace: String, fileContext: String): String = when {
        "NullPointerException" in stackTrace || "NullPointer" in stackTrace ->
            "Offline diagnosis: a nullable value was used as non-null. Check the first application stack-frame, then add a null guard or provide a meaningful fallback."
        "IndexOutOfBoundsException" in stackTrace ->
            "Offline diagnosis: an index is outside a collection's valid range. Validate the index against `0 until collection.size` before reading it."
        "Unresolved reference" in stackTrace ->
            "Offline diagnosis: the symbol is not available in this file. Check spelling, imports, package name, and whether the dependency is declared."
        fileContext.isBlank() -> "Offline diagnosis: add the failing file or stack trace for a more specific local diagnosis."
        else -> "Offline diagnosis: no known signature matched. Use the laptop bridge for a deeper stack-trace analysis."
    }

    override suspend fun explain(snippet: String): String {
        if (snippet.isBlank()) return "Offline explanation: select code to explain first."
        val lines = snippet.lineSequence().filter { it.isNotBlank() }.count()
        val kind = when {
            "class " in snippet -> "a class declaration"
            "fun " in snippet -> "a function"
            "if (" in snippet || "when (" in snippet -> "conditional logic"
            else -> "a code fragment"
        }
        return "Offline explanation: this is $kind spanning $lines non-empty line${if (lines == 1) "" else "s"}. Use the laptop bridge when you need a line-by-line explanation."
    }
}
