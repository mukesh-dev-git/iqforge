package com.iqforge.git

import java.io.File

object RepositoryPaths {
    private val unsafeCharacters = Regex("[^A-Za-z0-9._-]")

    fun repositoryName(url: String): String {
        val withoutQuery = url.trim().substringBefore('?').trimEnd('/')
        val raw = withoutQuery.substringAfterLast('/').substringAfterLast(':').removeSuffix(".git")
        val safe = raw.replace(unsafeCharacters, "-").trim('.', '-', '_').take(80)
        require(safe.isNotBlank()) { "Repository URL does not contain a valid repository name" }
        return safe
    }

    fun uniqueCloneDirectory(workspaceRoot: File, repositoryName: String): File {
        workspaceRoot.mkdirs()
        var candidate = File(workspaceRoot, repositoryName)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(workspaceRoot, "$repositoryName-$suffix")
            suffix += 1
        }
        return candidate
    }

    fun requireInside(root: File, target: File): File {
        val canonicalRoot = root.canonicalFile
        val canonicalTarget = target.canonicalFile
        require(canonicalTarget.toPath().startsWith(canonicalRoot.toPath())) {
            "Path escapes the repository workspace"
        }
        return canonicalTarget
    }
}
