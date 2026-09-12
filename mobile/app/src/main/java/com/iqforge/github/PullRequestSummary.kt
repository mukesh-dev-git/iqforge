package com.iqforge.github

fun pullRequestSummaryPrompt(
    title: String,
    body: String?,
    files: List<GitHubPullRequestFileDto>
): String = buildString {
    appendLine("Write a concise plain-English summary of this pull request for a busy human reviewer.")
    appendLine("Use exactly two or three short sentences. Explain what changed, why it matters, and what the reviewer should verify.")
    appendLine("Do not use Markdown, headings, bullets, symbols, code blocks, or developer jargon. Do not invent information.")
    appendLine()
    appendLine("Title: $title")
    body?.takeIf { it.isNotBlank() }?.let { appendLine("Author description: $it") }
    appendLine("Changed files:")
    files.forEach { file ->
        appendLine("${file.filename}: ${file.status}, ${file.additions} additions, ${file.deletions} deletions")
        file.patch?.let { appendLine(it.take(4_000)) }
    }
}

fun fallbackPullRequestSummary(
    title: String,
    files: List<GitHubPullRequestFileDto>
): String {
    val additions = files.sumOf { it.additions }
    val deletions = files.sumOf { it.deletions }
    val fileLabel = if (files.size == 1) "one file" else "${files.size} files"
    return "This pull request, titled ${title.trim()}, changes $fileLabel with $additions additions and $deletions deletions. " +
        "Review the changed behavior and confirm that the automated checks pass before merging."
}

fun cleanPullRequestSummary(raw: String, fallback: String): String {
    if (raw.isBlank() || raw.startsWith("ERROR:", ignoreCase = true)) return fallback
    val cleaned = raw
        .replace("```", "")
        .replace(Regex("(?m)^\\s{0,3}[#*•-]+\\s*"), "")
        .replace(Regex("(?im)^\\s*(summary|overview|plain-English summary)\\s*:?\\s*"), "")
        .replace('`', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(700)
    return cleaned.takeIf { it.length >= 30 } ?: fallback
}
