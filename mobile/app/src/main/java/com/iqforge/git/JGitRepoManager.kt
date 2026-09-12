package com.iqforge.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class JGitRepoManager(private val workspaceRoot: File) : RepoManager {
    @Volatile
    private var credentials: CredentialsProvider? = null

    fun updateCredentials(username: String, token: String) {
        credentials = if (token.isBlank()) null else UsernamePasswordCredentialsProvider(
            username.ifBlank { "oauth2" }, token
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

    override suspend fun push(repo: Repo): Unit = withContext(Dispatchers.IO) {
        useGit(repo) { git ->
            val command = git.push()
            credentials?.let(command::setCredentialsProvider)
            val failures = command.call().flatMap { it.remoteUpdates }.filter {
                it.status.name !in setOf("OK", "UP_TO_DATE")
            }
            check(failures.isEmpty()) {
                "Push rejected: ${failures.joinToString { "${it.remoteName} (${it.status})" }}"
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
