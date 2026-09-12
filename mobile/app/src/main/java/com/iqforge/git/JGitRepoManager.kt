package com.iqforge.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class JGitRepoManager(private val workspaceRoot: File) : RepoManager {
    @Volatile
    private var credentials: CredentialsProvider? = null

    fun updateCredentials(username: String, token: String) {
        val user = username.trim().ifBlank { "x-access-token" }
        credentials = if (token.isBlank()) null else UsernamePasswordCredentialsProvider(
            user, token.trim()
        )
    }

    suspend fun create(name: String, description: String): Repo = withContext(Dispatchers.IO) {
        val normalized = name.trim()
        require(normalized.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))) {
            "Use 1-80 letters, numbers, dots, dashes, or underscores for the project name"
        }
        val target = RepositoryPaths.requireInside(workspaceRoot, workspaceRoot.resolve(normalized))
        require(!target.exists()) { "A project named $normalized already exists" }
        target.mkdirs()
        try {
            target.resolve("README.md").writeText(
                buildString {
                    appendLine("# $normalized")
                    if (description.isNotBlank()) {
                        appendLine()
                        appendLine(description.trim())
                    }
                }
            )
            Git.init().setDirectory(target).call().use { git ->
                git.add().addFilepattern("README.md").call()
                git.commit()
                    .setMessage("Initialize $normalized")
                    .setAuthor("iQForge", "iqforge@local")
                    .setCommitter("iQForge", "iqforge@local")
                    .call()
                Repo(target, normalized)
            }
        } catch (error: Exception) {
            target.deleteRecursively()
            throw IllegalStateException(friendlyMessage("Project creation failed", error), error)
        }
    }

    override suspend fun clone(url: String, into: File): Repo = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "Use an HTTPS repository URL" }
        val target = RepositoryPaths.requireInside(workspaceRoot, into)
        require(!target.exists()) { "Clone destination already exists" }
        target.parentFile?.mkdirs()
        try {
            val command = Git.cloneRepository().setURI(url).setDirectory(target)
            credentials?.let(command::setCredentialsProvider)
            command.call().use { git -> Repo(target, git.repository.workTree.name) }
        } catch (error: Exception) {
            target.deleteRecursively()
            throw IllegalStateException(friendlyMessage("Clone failed", error), error)
        }
    }

    override suspend fun pull(repo: Repo): Unit = withContext(Dispatchers.IO) {
        useGit(repo) { git ->
            val command = git.pull()
            credentials?.let(command::setCredentialsProvider)
            val result = command.call()
            check(result.isSuccessful) { "Pull did not complete: ${result.mergeResult ?: result.rebaseResult}" }
        }
    }

    override suspend fun commit(repo: Repo, message: String, paths: List<String>): Unit = withContext(Dispatchers.IO) {
        require(message.isNotBlank()) { "Enter a commit message" }
        useGit(repo) { git ->
            val add = git.add()
            if (paths.isEmpty()) {
                add.addFilepattern(".")
            } else {
                paths.map(::safeGitPath).forEach(add::addFilepattern)
            }
            add.call()
            try {
                git.add().setUpdate(true).addFilepattern(".").call()
            } catch (ignored: Exception) {}

            git.commit()
                .setMessage(message.trim())
                .setAuthor("iQForge", "iqforge@local")
                .setCommitter("iQForge", "iqforge@local")
                .call()
        }
    }

    private fun safeGitPath(path: String): String {
        val normalized = path.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && normalized.split('/').none { it == ".." }) {
            "Invalid path selected for commit"
        }
        return normalized
    }

    /** Reads the `origin` remote URL straight from .git/config; not persisted anywhere else. */
    suspend fun remoteUrl(repo: Repo): String? = withContext(Dispatchers.IO) {
        runCatching { useGit(repo) { git -> git.repository.config.getString("remote", "origin", "url") } }.getOrNull()
    }

    override suspend fun push(repo: Repo): Unit = withContext(Dispatchers.IO) {
        useGit(repo) { git ->
            val command = git.push()
            credentials?.let(command::setCredentialsProvider)
            val updates = command.call()
            val failures = updates.flatMap { it.remoteUpdates }.filter {
                it.status.name !in setOf("OK", "UP_TO_DATE")
            }
            check(failures.isEmpty()) {
                "Push rejected: ${failures.joinToString { "${it.remoteName} (${it.status} - ${it.message.orEmpty()})" }}"
            }
        }
    }

    suspend fun status(repo: Repo): String = withContext(Dispatchers.IO) {
        useGit(repo) { git ->
            val status = git.status().call()
            val branch = git.repository.branch
            val tracking = try {
                BranchTrackingStatus.of(git.repository, branch)
            } catch (e: Exception) {
                null
            }
            buildString {
                appendLine("📦 **Repository**: `${repo.name}`")
                appendLine("🌿 **Branch**: `$branch`")
                if (tracking != null) {
                    if (tracking.aheadCount > 0) appendLine("⬆️ Ahead: **${tracking.aheadCount}** commit(s) (run `/push` or `git push` to upload)")
                    if (tracking.behindCount > 0) appendLine("⬇️ Behind: **${tracking.behindCount}** commit(s) (run `/pull` or `git pull` to update)")
                }
                if (status.isClean) {
                    appendLine("✨ Working tree clean. All changes committed.")
                } else {
                    if (status.modified.isNotEmpty()) appendLine("📝 **Modified**:\n" + status.modified.joinToString("\n") { "   • $it" })
                    if (status.untracked.isNotEmpty()) appendLine("❓ **Untracked**:\n" + status.untracked.joinToString("\n") { "   • $it" })
                    if (status.added.isNotEmpty()) appendLine("➕ **Staged**:\n" + status.added.joinToString("\n") { "   • $it" })
                    if (status.removed.isNotEmpty()) appendLine("🗑️ **Deleted**:\n" + status.removed.joinToString("\n") { "   • $it" })
                    if (status.missing.isNotEmpty()) appendLine("⚠️ **Missing**:\n" + status.missing.joinToString("\n") { "   • $it" })
                    appendLine("\n💡 Tip: Run `/commit <message>` or `git commit -m \"...\"` to commit.")
                }
            }
        }
    }

    suspend fun diff(repo: Repo, path: String? = null): String = withContext(Dispatchers.IO) {
        useGit(repo) { git ->
            val out = ByteArrayOutputStream()
            val cmd = git.diff().setOutputStream(out)
            if (!path.isNullOrBlank()) {
                cmd.setPathFilter(PathFilter.create(safeGitPath(path)))
            }
            cmd.call()
            val unstaged = out.toString(Charsets.UTF_8.name())

            out.reset()
            val cachedCmd = git.diff().setCached(true).setOutputStream(out)
            if (!path.isNullOrBlank()) {
                cachedCmd.setPathFilter(PathFilter.create(safeGitPath(path)))
            }
            cachedCmd.call()
            val staged = out.toString(Charsets.UTF_8.name())

            buildString {
                if (unstaged.isBlank() && staged.isBlank()) {
                    append("✨ No changes detected in ${if (path.isNullOrBlank()) "working tree" else "`$path`"}.")
                } else {
                    if (staged.isNotBlank()) {
                        appendLine("📦 **Staged Changes (Index vs HEAD)**:")
                        appendLine("```diff")
                        appendLine(staged.trim())
                        appendLine("```")
                    }
                    if (unstaged.isNotBlank()) {
                        if (staged.isNotBlank()) appendLine()
                        appendLine("📝 **Unstaged Changes (Working Tree vs Index)**:")
                        appendLine("```diff")
                        appendLine(unstaged.trim())
                        appendLine("```")
                    }
                }
            }
        }
    }

    suspend fun log(repo: Repo, limit: Int = 10): String = withContext(Dispatchers.IO) {
        useGit(repo) { git ->
            val commits = try {
                git.log().setMaxCount(limit.coerceIn(1, 50)).call().toList()
            } catch (e: Exception) {
                emptyList()
            }
            if (commits.isEmpty()) {
                return@useGit "📜 No commits yet in repository **${repo.name}**."
            }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            buildString {
                appendLine("📜 **Commit History (${commits.size} commits)**:")
                appendLine()
                commits.forEach { commit ->
                    val hash = commit.id.name.take(7)
                    val author = commit.authorIdent.name
                    val date = dateFormat.format(commit.authorIdent.`when`)
                    val title = commit.shortMessage
                    appendLine("• `$hash` **$title**")
                    appendLine("  👤 $author | 🕒 $date")
                }
            }
        }
    }

    suspend fun branches(repo: Repo): String = withContext(Dispatchers.IO) {
        useGit(repo) { git ->
            val branchList = git.branchList().call()
            val current = git.repository.branch
            if (branchList.isEmpty()) {
                return@useGit "🌿 No branches found."
            }
            buildString {
                appendLine("🌿 **Branches in ${repo.name}**:")
                branchList.forEach { ref ->
                    val name = ref.name.removePrefix("refs/heads/")
                    if (name == current) {
                        appendLine("  ★ **$name** *(current)*")
                    } else {
                        appendLine("  • $name")
                    }
                }
            }
        }
    }

    suspend fun checkout(repo: Repo, branchOrCommit: String, createBranch: Boolean = false): String = withContext(Dispatchers.IO) {
        useGit(repo) { git ->
            val cmd = git.checkout().setName(branchOrCommit)
            if (createBranch) {
                cmd.setCreateBranch(true)
            }
            cmd.call()
            val cur = git.repository.branch
            "🌿 Switched to branch **$cur**${if (createBranch) " (newly created)" else ""}."
        }
    }

    suspend fun reset(repo: Repo, hard: Boolean = true): String = withContext(Dispatchers.IO) {
        useGit(repo) { git ->
            if (hard) {
                git.reset().setMode(ResetType.HARD).call()
                git.clean().setCleanDirectories(true).setIgnore(false).call()
                "🔄 Working tree and index hard-reset to HEAD. All uncommitted changes discarded."
            } else {
                git.reset().setMode(ResetType.MIXED).call()
                "🔄 Changes unstaged from index (working tree preserved)."
            }
        }
    }

    suspend fun discard(repo: Repo, path: String): String = withContext(Dispatchers.IO) {
        useGit(repo) { git ->
            git.checkout().addPath(safeGitPath(path)).call()
            "🔄 Discarded changes in `$path`."
        }
    }

    suspend fun remotes(repo: Repo): String = withContext(Dispatchers.IO) {
        useGit(repo) { git ->
            val config = git.repository.config
            val remoteNames = config.getSubsections("remote")
            if (remoteNames.isEmpty()) {
                "🔗 No remotes configured."
            } else {
                buildString {
                    appendLine("🔗 **Configured Remotes**:")
                    remoteNames.forEach { name ->
                        val url = config.getString("remote", name, "url") ?: "none"
                        appendLine("  • **$name**: `$url`")
                    }
                }
            }
        }
    }

    private fun <T> useGit(repo: Repo, block: (Git) -> T): T {
        val root = RepositoryPaths.requireInside(workspaceRoot, repo.root)
        try {
            val repository = RepositoryBuilder().setWorkTree(root).findGitDir(root).build()
            return Git(repository).use(block)
        } catch (error: Exception) {
            throw IllegalStateException(friendlyMessage("Git operation failed", error), error)
        }
    }

    private fun friendlyMessage(prefix: String, error: Exception): String {
        val detail = generateSequence<Throwable>(error) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull { it.isNotBlank() }
            ?: error.javaClass.simpleName
        return when {
            detail.contains("Auth", ignoreCase = true) || detail.contains("not authorized", ignoreCase = true) ->
                "$prefix: authentication failed. Check the GitHub username and token."
            detail.contains("not found", ignoreCase = true) ->
                "$prefix: repository or remote was not found. Check the URL and access."
            error is GitAPIException -> "$prefix: $detail"
            else -> "$prefix: $detail"
        }
    }
}
