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
    private val mutableState = mutableStateOf(WorkspaceUiState())
    val state: State<WorkspaceUiState> = mutableState

    fun updateRepoUrl(value: String) = update { copy(repoUrl = value, error = null) }
    fun updateUsername(value: String) = update { copy(githubUsername = value, error = null) }
    fun updateToken(value: String) = update { copy(githubToken = value, error = null) }
    fun updateCommitMessage(value: String) = update { copy(commitMessage = value, error = null) }
    fun updateEditor(value: String) = update { copy(editorText = value, editorDirty = true) }

    fun cloneRepository() = runOperation("Cloning repository…") {
        applyCredentials()
        val snapshot = mutableState.value
        val name = RepositoryPaths.repositoryName(snapshot.repoUrl)
        val destination = RepositoryPaths.uniqueCloneDirectory(workspaceRoot, name)
        val repo = repoManager.clone(snapshot.repoUrl.trim(), destination)
        withContext(Dispatchers.Main) {
            mutableState.value = mutableState.value.copy(
                repo = repo,
                githubToken = "",
                entries = files.visibleEntries(repo.root, emptySet()),
                message = "Cloned ${repo.name}",
                error = null
            )
        }
    }

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

    fun commit() {
        val repo = mutableState.value.repo ?: return
        runOperation("Creating commit…") {
            repoManager.commit(repo, mutableState.value.commitMessage, emptyList())
            withContext(Dispatchers.Main) {
                mutableState.value = mutableState.value.copy(
                    commitMessage = "",
                    message = "Commit created on the phone",
                    error = null
                )
            }
        }
    }

    fun push() {
        val repo = mutableState.value.repo ?: return
        runOperation("Pushing to remote…") {
            applyCredentials()
            repoManager.push(repo)
            withContext(Dispatchers.Main) {
                mutableState.value = mutableState.value.copy(
                    githubToken = "",
                    message = "Push completed",
                    error = null
                )
            }
        }
    }

    private fun applyCredentials() {
        val snapshot = mutableState.value
        repoManager.updateCredentials(snapshot.githubUsername, snapshot.githubToken)
    }

    private suspend fun refreshFiles(repo: Repo) = withContext(Dispatchers.Main) {
        mutableState.value = mutableState.value.copy(
            entries = files.visibleEntries(repo.root, mutableState.value.expandedDirectories)
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
