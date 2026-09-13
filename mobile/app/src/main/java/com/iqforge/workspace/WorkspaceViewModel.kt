package com.iqforge.workspace

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iqforge.git.JGitRepoManager
import com.iqforge.git.Repo
import com.iqforge.git.RepositoryPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkspaceUiState(
    val repoUrl: String = "",
    val githubUsername: String = "",
    val githubToken: String = "",
    val repo: Repo? = null,
    val repositories: List<Repo> = emptyList(),
    val pinnedRepositoryNames: Set<String> = emptySet(),
    val projectMetadata: Map<String, ProjectMetadata> = emptyMap(),
    val showArchivedProjects: Boolean = false,
    val artifacts: List<WorkspaceEntry> = emptyList(),
    val entries: List<WorkspaceEntry> = emptyList(),
    val expandedDirectories: Set<String> = emptySet(),
    val selectedFile: WorkspaceEntry? = null,
    val editorText: String = "",
    val editorDirty: Boolean = false,
    val commitMessage: String = "",
    val busy: Boolean = false,
    val operation: String = "",
    val message: String? = null,
    val error: String? = null
)

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val workspaceRoot = application.filesDir.resolve("repositories")
    private val repoManager = JGitRepoManager(workspaceRoot)
    private val files = FileWorkspace()
    private val preferences = application.getSharedPreferences("iqforge_workspace", android.content.Context.MODE_PRIVATE)
    private val metadataStore = ProjectMetadataStore(preferences)
    private val mutableState = mutableStateOf(
        WorkspaceUiState(
            pinnedRepositoryNames = preferences.getStringSet("pinned_repositories", emptySet()).orEmpty(),
            projectMetadata = metadataStore.load()
        )
    )
    val state: State<WorkspaceUiState> = mutableState

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val repositories = workspaceRoot.listFiles()
                ?.filter { it.isDirectory && it.resolve(".git").isDirectory }
                ?.sortedByDescending { it.resolve(".git/index").lastModified() }
                ?.map { Repo(it, it.name) }
                .orEmpty()
            val existing = repositories.firstOrNull()
            if (existing != null) {
                val entries = files.visibleEntries(existing.root, emptySet())
                withContext(Dispatchers.Main) {
                    mutableState.value = mutableState.value.copy(
                        repo = existing,
                        repositories = repositories,
                        entries = entries,
                        artifacts = files.artifactFiles(existing.root)
                    )
                }
            } else withContext(Dispatchers.Main) {
                mutableState.value = mutableState.value.copy(repositories = repositories)
            }
        }
    }

    fun updateRepoUrl(value: String) = update { copy(repoUrl = value, error = null) }
    fun updateUsername(value: String) = update { copy(githubUsername = value, error = null) }
    fun updateToken(value: String) = update { copy(githubToken = value, error = null) }
    fun updateCommitMessage(value: String) = update { copy(commitMessage = value, error = null) }
    fun updateEditor(value: String) = update { copy(editorText = value, editorDirty = true) }

    fun cloneRepository() = cloneRepository(mutableState.value.repoUrl, mutableState.value.githubUsername, mutableState.value.githubToken, null)

    fun cloneRepository(
        url: String,
        username: String = "",
        token: String = "",
        onComplete: ((Repo) -> Unit)? = null
    ) = runOperation("Cloning repository…") {
        if (token.isNotBlank()) {
            preferences.edit()
                .putString("github_token", token.trim())
                .putString("github_username", username.trim())
                .apply()
            repoManager.updateCredentials(username.trim(), token.trim())
        } else applyCredentials()
        val name = RepositoryPaths.repositoryName(url)
        val destination = RepositoryPaths.uniqueCloneDirectory(workspaceRoot, name)
        val repo = repoManager.clone(url.trim(), destination)
        val metadata = metadataStore.save(ProjectMetadata(repo.name))
        withContext(Dispatchers.Main) {
            mutableState.value = mutableState.value.copy(
                repo = repo,
                repositories = (mutableState.value.repositories.filterNot { it.root == repo.root } + repo)
                    .sortedBy { it.name.lowercase() },
                entries = files.visibleEntries(repo.root, emptySet()),
                artifacts = files.artifactFiles(repo.root),
                projectMetadata = metadata,
                message = "Cloned ${repo.name}",
                error = null
            )
            onComplete?.invoke(repo)
        }
    }

    fun createRepository(name: String, description: String) = createRepository(name, description, null)

    fun createRepository(
        name: String,
        description: String = "",
        onComplete: ((Repo) -> Unit)? = null
    ) = runOperation("Creating project…") {
        val repo = repoManager.create(name, description)
        val metadata = metadataStore.save(ProjectMetadata(repo.name, description = description.trim()))
        withContext(Dispatchers.Main) {
            mutableState.value = mutableState.value.copy(
                repo = repo,
                repositories = (mutableState.value.repositories + repo).sortedBy { it.name.lowercase() },
                entries = files.visibleEntries(repo.root, emptySet()),
                artifacts = files.artifactFiles(repo.root),
                projectMetadata = metadata,
                message = "Created ${repo.name}",
                error = null
            )
            onComplete?.invoke(repo)
        }
    }

    fun selectRepository(name: String) {
        val repo = mutableState.value.repositories.firstOrNull { it.name == name } ?: return
        runOperation("Opening ${repo.name}…") {
            val entries = files.visibleEntries(repo.root, emptySet())
            withContext(Dispatchers.Main) {
                mutableState.value = mutableState.value.copy(
                    repo = repo,
                    entries = entries,
                    artifacts = files.artifactFiles(repo.root),
                    expandedDirectories = emptySet(),
                    selectedFile = null,
                    editorText = "",
                    editorDirty = false,
                    message = "Opened ${repo.name}",
                    error = null
                )
            }
        }
    }

    fun startNewRepository() = update {
        copy(
            repo = null,
            entries = emptyList(),
            artifacts = emptyList(),
            selectedFile = null,
            editorText = "",
            repoUrl = "",
            message = null,
            error = null
        )
    }

    fun togglePinned(name: String) {
        val pins = mutableState.value.pinnedRepositoryNames.toMutableSet().apply {
            if (!add(name)) remove(name)
        }
        preferences.edit().putStringSet("pinned_repositories", pins).apply()
        update { copy(pinnedRepositoryNames = pins) }
    }

    fun setShowArchived(show: Boolean) = update { copy(showArchivedProjects = show) }

    fun updateProjectDetails(name: String, description: String, instructions: String) {
        val current = mutableState.value.projectMetadata[name] ?: ProjectMetadata(name)
        val metadata = metadataStore.save(current.copy(description = description.trim(), instructions = instructions.trim()))
        update { copy(projectMetadata = metadata, message = "Updated $name", error = null) }
    }

    fun toggleArchived(name: String) {
        val current = mutableState.value.projectMetadata[name] ?: ProjectMetadata(name)
        val metadata = metadataStore.save(current.copy(archived = !current.archived))
        update { copy(projectMetadata = metadata, message = if (current.archived) "Restored $name" else "Archived $name") }
    }

    fun deleteRepository(name: String) {
        val repository = mutableState.value.repositories.firstOrNull { it.name == name } ?: return
        runOperation("Deleting $name…") {
            val safeRoot = RepositoryPaths.requireInside(workspaceRoot, repository.root)
            check(safeRoot.deleteRecursively()) { "Could not delete $name" }
            val metadata = metadataStore.remove(name)
            val pins = mutableState.value.pinnedRepositoryNames - name
            preferences.edit().putStringSet("pinned_repositories", pins).apply()
            withContext(Dispatchers.Main) {
                val remaining = mutableState.value.repositories.filterNot { it.name == name }
                val next = remaining.firstOrNull()
                mutableState.value = mutableState.value.copy(
                    repo = next,
                    repositories = remaining,
                    pinnedRepositoryNames = pins,
                    projectMetadata = metadata,
                    entries = next?.let { files.visibleEntries(it.root, emptySet()) }.orEmpty(),
                    artifacts = next?.let { files.artifactFiles(it.root) }.orEmpty(),
                    message = "Deleted $name",
                    error = null
                )
            }
        }
    }

    fun openArtifact(entry: WorkspaceEntry) = openEntry(entry)

    fun openEntry(entry: WorkspaceEntry) {
        val repo = mutableState.value.repo ?: return
        if (entry.directory) {
            val expanded = mutableState.value.expandedDirectories.toMutableSet().apply {
                if (!add(entry.relativePath)) remove(entry.relativePath)
            }
            update {
                copy(
                    expandedDirectories = expanded,
                    entries = files.visibleEntries(repo.root, expanded),
                    error = null
                )
            }
            return
        }
        runOperation("Opening ${entry.name}…") {
            val text = files.readText(repo.root, entry.file)
            withContext(Dispatchers.Main) {
                mutableState.value = mutableState.value.copy(
                    selectedFile = entry,
                    editorText = text,
                    editorDirty = false,
                    message = null,
                    error = null
                )
            }
        }
    }

    fun closeEditor() = update {
        copy(selectedFile = null, editorText = "", editorDirty = false, message = null, error = null)
    }

    fun saveFile() {
        val repo = mutableState.value.repo ?: return
        val selected = mutableState.value.selectedFile ?: return
        runOperation("Saving ${selected.name}…") {
            files.writeText(repo.root, selected.file, mutableState.value.editorText)
            withContext(Dispatchers.Main) {
                mutableState.value = mutableState.value.copy(
                    editorDirty = false,
                    message = "Saved ${selected.relativePath}",
                    error = null
                )
            }
        }
    }

    fun pull() {
        val repo = mutableState.value.repo ?: return
        runOperation("Pulling changes…") {
            applyCredentials()
            repoManager.pull(repo)
            refreshFiles(repo)
            showMessage("Repository is up to date")
        }
    }

    fun commit(customMessage: String = "") {
        val repo = mutableState.value.repo ?: return
        val msg = customMessage.trim().ifBlank { mutableState.value.commitMessage }.ifBlank { "Update from IQForge mobile agent" }
        runOperation("Creating commit…") {
            repoManager.commit(repo, msg, emptyList())
            withContext(Dispatchers.Main) {
                mutableState.value = mutableState.value.copy(
                    commitMessage = "",
                    message = "Commit created on the phone",
                    error = null
                )
            }
        }
    }

    fun push(token: String = "") {
        val repo = mutableState.value.repo ?: return
        runOperation("Pushing to remote…") {
            if (token.isNotBlank()) {
                preferences.edit().putString("github_token", token.trim()).apply()
                val savedUser = preferences.getString("github_username", "").orEmpty()
                repoManager.updateCredentials(savedUser, token.trim())
            } else {
                applyCredentials()
            }
            repoManager.push(repo)
            withContext(Dispatchers.Main) {
                mutableState.value = mutableState.value.copy(
                    message = "Push completed to GitHub",
                    error = null
                )
            }
        }
    }

    fun hasSavedToken(): Boolean = preferences.getString("github_token", "").isNullOrBlank().not()

    fun savedGitHubToken(): String = preferences.getString("github_token", "").orEmpty()

    fun saveGitHubToken(token: String, username: String = "") {
        preferences.edit()
            .putString("github_token", token.trim())
            .putString("github_username", username.trim())
            .apply()
        applyCredentials()
    }

    private fun applyCredentials() {
        val snapshot = mutableState.value
        val username = snapshot.githubUsername.ifBlank { preferences.getString("github_username", "").orEmpty() }
        val token = snapshot.githubToken.ifBlank { preferences.getString("github_token", "").orEmpty() }
        if (token.isNotBlank()) {
            repoManager.updateCredentials(username, token)
        }
    }

    private suspend fun refreshFiles(repo: Repo) = withContext(Dispatchers.Main) {
        mutableState.value = mutableState.value.copy(
            entries = files.visibleEntries(repo.root, mutableState.value.expandedDirectories),
            artifacts = files.artifactFiles(repo.root)
        )
    }

    private suspend fun showMessage(message: String) = withContext(Dispatchers.Main) {
        mutableState.value = mutableState.value.copy(message = message, error = null)
    }

    private fun runOperation(name: String, block: suspend () -> Unit) {
        update { copy(busy = true, operation = name, error = null, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    mutableState.value = mutableState.value.copy(
                        error = error.message ?: "Operation failed",
                        githubToken = ""
                    )
                }
            } finally {
                withContext(Dispatchers.Main) {
                    mutableState.value = mutableState.value.copy(busy = false, operation = "")
                }
            }
        }
    }

    private inline fun update(transform: WorkspaceUiState.() -> WorkspaceUiState) {
        mutableState.value = mutableState.value.transform()
    }
}
