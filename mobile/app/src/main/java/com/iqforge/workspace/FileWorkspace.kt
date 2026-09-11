package com.iqforge.workspace

import com.iqforge.git.RepositoryPaths
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class WorkspaceEntry(
    val file: File,
    val relativePath: String,
    val name: String,
    val depth: Int,
    val directory: Boolean,
    val expanded: Boolean
)

class FileWorkspace(private val maxTextBytes: Long = 1_048_576) {
    fun artifactFiles(root: File, limit: Int = 100): List<WorkspaceEntry> = root
        .walkTopDown()
        .onEnter { it.name != ".git" }
        .filter { file ->
            file.isFile && file.length() <= maxTextBytes && file.extension.lowercase() in TEXT_EXTENSIONS
        }
        .take(limit)
        .map { file ->
            val safe = RepositoryPaths.requireInside(root, file)
            WorkspaceEntry(
                file = safe,
                relativePath = safe.relativeTo(root).invariantSeparatorsPath,
                name = safe.name,
                depth = 0,
                directory = false,
                expanded = false
            )
        }
        .toList()

    fun visibleEntries(root: File, expanded: Set<String>): List<WorkspaceEntry> {
        val entries = mutableListOf<WorkspaceEntry>()
        appendChildren(root.canonicalFile, root.canonicalFile, 0, expanded, entries)
        return entries
    }

    fun readText(root: File, file: File): String {
        val safe = RepositoryPaths.requireInside(root, file)
        require(safe.isFile) { "Selected path is not a file" }
        require(safe.length() <= maxTextBytes) { "File is larger than 1 MB" }
        val bytes = safe.readBytes()
        require(bytes.none { it == 0.toByte() }) { "Binary files cannot be opened in the text editor" }
        return bytes.toString(Charsets.UTF_8)
    }

    fun writeText(root: File, file: File, content: String) {
        val safe = RepositoryPaths.requireInside(root, file)
        require(safe.isFile) { "Selected path is not a file" }
        val temporary = File(safe.parentFile, ".${safe.name}.iqforge.tmp")
        RepositoryPaths.requireInside(root, temporary)
        temporary.writeText(content, Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                safe.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), safe.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("Could not replace ${safe.name}: ${error.message}", error)
        }
    }

    private fun appendChildren(
        root: File,
        directory: File,
        depth: Int,
        expanded: Set<String>,
        output: MutableList<WorkspaceEntry>
    ) {
        val children = directory.listFiles()
            ?.filterNot { it.name == ".git" }
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .orEmpty()

        for (child in children) {
            val safe = runCatching { RepositoryPaths.requireInside(root, child) }.getOrNull() ?: continue
            val relative = safe.relativeTo(root).invariantSeparatorsPath
            val isExpanded = safe.isDirectory && relative in expanded
            output += WorkspaceEntry(safe, relative, safe.name, depth, safe.isDirectory, isExpanded)
            if (isExpanded) appendChildren(root, safe, depth + 1, expanded, output)
        }
    }

    private companion object {
        val TEXT_EXTENSIONS = setOf(
            "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "json", "xml", "md",
            "txt", "yaml", "yml", "toml", "gradle", "properties", "c", "cc", "cpp", "h", "hpp"
        )
    }
}
