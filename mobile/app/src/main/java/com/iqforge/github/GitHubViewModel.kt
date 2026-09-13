package com.iqforge.github

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iqforge.git.JGitRepoManager
import com.iqforge.git.Repo
import kotlinx.coroutines.launch

/**
 * Read-only, phone-direct GitHub PR/Issues viewer. Deliberately independent of AgentViewModel
 * and the laptop bridge — this must work standalone with no laptop connected, the same way
 * repo cloning already works standalone via JGit.
 */
class GitHubViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("iqforge_workspace", android.content.Context.MODE_PRIVATE)
    private val client: GitHubApiClient = GitHubApiClient(
        tokenProvider = { preferences.getString("github_token", "") }
    )
    private val repoManager = JGitRepoManager(application.filesDir.resolve("repositories"))

    var repoRef by mutableStateOf<GitHubRepoRef?>(null); private set
    var pullRequests by mutableStateOf<List<GitHubPullRequestSummaryDto>>(emptyList()); private set
    var issues by mutableStateOf<List<GitHubIssueDto>>(emptyList()); private set
    var selectedPullRequest by mutableStateOf<GitHubPullRequestDetailDto?>(null); private set
    var selectedPullRequestFiles by mutableStateOf<List<GitHubPullRequestFileDto>>(emptyList()); private set
    var selectedCheckRuns by mutableStateOf<List<GitHubCheckRunDto>>(emptyList()); private set
    var reviewReadiness by mutableStateOf<PullRequestReadiness?>(null); private set
    var mergeState by mutableStateOf(MergeState.IDLE); private set
    var mergeMessage by mutableStateOf<String?>(null); private set
    var mergedCommitSha by mutableStateOf<String?>(null); private set
    var selectedIssue by mutableStateOf<GitHubIssueDto?>(null); private set
    var loadingList by mutableStateOf(false); private set
    var loadingDetail by mutableStateOf(false); private set
    var listError by mutableStateOf<String?>(null); private set
    var detailError by mutableStateOf<String?>(null); private set
    private var loadedForRoot: String? = null

    fun loadForRepo(repo: Repo, forceRefresh: Boolean = false) {
        if (!forceRefresh && loadedForRoot == repo.root.absolutePath) return
        loadedForRoot = repo.root.absolutePath
        loadingList = true
        listError = null
        repoRef = null
        viewModelScope.launch {
            try {
                val ref = GitHubRepoRef.parse(repoManager.remoteUrl(repo))
                repoRef = ref
                if (ref == null) {
                    listError = "This project isn't linked to a public GitHub repository."
                } else {
                    pullRequests = client.listPullRequests(ref.owner, ref.repo)
                    issues = client.listIssues(ref.owner, ref.repo)
                }
            } catch (error: Exception) {
                listError = error.message ?: "Could not reach GitHub."
            } finally {
                loadingList = false
            }
        }
    }

    /** Standalone entry point for the Review page: user types a repo, no local clone required. */
    fun loadForInput(input: String) {
        loadedForRoot = null
        val ref = GitHubRepoRef.parseFlexible(input)
        repoRef = ref
        pullRequests = emptyList()
        issues = emptyList()
        if (ref == null) {
            listError = "Enter a valid GitHub repository, e.g. owner/repo or https://github.com/owner/repo"
            return
        }
        loadingList = true
        listError = null
        viewModelScope.launch {
            try {
                pullRequests = client.listPullRequests(ref.owner, ref.repo)
                issues = client.listIssues(ref.owner, ref.repo)
            } catch (error: Exception) {
                listError = error.message ?: "Could not reach GitHub."
            } finally {
                loadingList = false
            }
        }
    }

    fun openPullRequest(number: Int) {
        val ref = repoRef ?: return
        loadingDetail = true
        detailError = null
        selectedPullRequestFiles = emptyList()
        selectedCheckRuns = emptyList()
        reviewReadiness = null
        mergeState = MergeState.IDLE
        mergeMessage = null
        mergedCommitSha = null
        viewModelScope.launch {
            try {
                val pullRequest = client.getPullRequest(ref.owner, ref.repo, number)
                val files = client.getPullRequestFiles(ref.owner, ref.repo, number)
                val checks = if (pullRequest.head.sha.isNotBlank()) {
                    client.getCheckRuns(ref.owner, ref.repo, pullRequest.head.sha).checkRuns
                } else emptyList()
                selectedPullRequest = pullRequest
                selectedPullRequestFiles = files
                selectedCheckRuns = checks
                reviewReadiness = PullRequestReadiness.from(pullRequest, files, checks)
            } catch (error: Exception) {
                detailError = error.message ?: "Could not load pull request."
            } finally {
                loadingDetail = false
            }
        }
    }

    fun openIssue(issue: GitHubIssueDto) {
        selectedIssue = issue
    }

    fun approveAndMerge(number: Int, reviewedHeadSha: String, hasBlockingFindings: Boolean) {
        val ref = repoRef ?: return
        if (mergeState in setOf(MergeState.REVALIDATING, MergeState.APPROVING, MergeState.MERGING)) return
        if (preferences.getString("github_token", "").isNullOrBlank()) {
            mergeState = MergeState.FAILED
            mergeMessage = "Connect a GitHub token with pull-request and contents write access before merging."
            return
        }
        if (hasBlockingFindings) {
            mergeState = MergeState.BLOCKED
            mergeMessage = "Resolve the blocking IQ findings before merging."
            return
        }

        mergeState = MergeState.REVALIDATING
        mergeMessage = "Rechecking the latest commit, conflicts, and automated checks…"
        viewModelScope.launch {
            try {
                val latest = client.getPullRequest(ref.owner, ref.repo, number)
                if (latest.head.sha != reviewedHeadSha) {
                    mergeState = MergeState.BLOCKED
                    mergeMessage = "A new commit was pushed after this review. Review the updated patch before merging."
                    return@launch
                }
                val checks = client.getCheckRuns(ref.owner, ref.repo, latest.head.sha).checkRuns
                val latestReadiness = PullRequestReadiness.from(latest, selectedPullRequestFiles, checks)
                selectedPullRequest = latest
                selectedCheckRuns = checks
                reviewReadiness = latestReadiness
                latestReadiness.mergeBlockReason()?.let { reason ->
                    mergeState = MergeState.BLOCKED
                    mergeMessage = reason
                    return@launch
                }

                val authenticatedUser = runCatching { client.getAuthenticatedUser() }.getOrNull()
                val isOwnPullRequest = authenticatedUser?.login
                    ?.equals(latest.user?.login, ignoreCase = true) == true
                if (isOwnPullRequest) {
                    // GitHub rejects self-approval. Repository owners may still merge their
                    // own clean PR when branch protection permits it, so continue directly
                    // to the guarded exact-SHA merge instead of reporting a false failure.
                    mergeMessage = "You authored this pull request, so GitHub self-approval was skipped. Preparing the verified merge…"
                } else {
                    mergeState = MergeState.APPROVING
                    mergeMessage = "Submitting your approval…"
                    client.submitApproval(ref.owner, ref.repo, number)
                }

                mergeState = MergeState.MERGING
                mergeMessage = "Merging the exact commit you reviewed…"
                val result = client.mergePullRequest(ref.owner, ref.repo, number, reviewedHeadSha)
                if (result.merged) {
                    mergeState = MergeState.MERGED
                    mergedCommitSha = result.sha
                    mergeMessage = result.message.ifBlank { "Pull request merged successfully." }
                } else {
                    mergeState = MergeState.BLOCKED
                    mergeMessage = result.message.ifBlank { "GitHub declined the merge." }
                }
            } catch (error: Exception) {
                mergeState = MergeState.FAILED
                mergeMessage = error.message ?: "GitHub could not complete the approval and merge."
            }
        }
    }

    fun clearSelection() {
        selectedPullRequest = null
        selectedPullRequestFiles = emptyList()
        selectedCheckRuns = emptyList()
        reviewReadiness = null
        mergeState = MergeState.IDLE
        mergeMessage = null
        mergedCommitSha = null
        selectedIssue = null
        detailError = null
    }
}

data class PullRequestReadiness(
    val reviewedHeadSha: String,
    val isDraft: Boolean,
    val hasConflicts: Boolean?,
    val checksState: ChecksState,
    val patchAvailable: Boolean,
    val changedLines: Int
) {
    val isReadyForReview: Boolean
        get() = !isDraft && hasConflicts == false && checksState != ChecksState.FAILED && patchAvailable

    fun mergeBlockReason(): String? = when {
        isDraft -> "This pull request is still a draft."
        hasConflicts == null -> "GitHub is still calculating merge conflicts. Refresh and try again."
        hasConflicts -> "This pull request conflicts with the target branch."
        checksState == ChecksState.PENDING -> "Automated checks are still running."
        checksState == ChecksState.FAILED -> "One or more automated checks failed."
        !patchAvailable -> "The complete patch was not available for review."
        reviewedHeadSha.isBlank() -> "The reviewed commit SHA is unavailable."
        else -> null
    }

    companion object {
        fun from(
            pullRequest: GitHubPullRequestDetailDto,
            files: List<GitHubPullRequestFileDto>,
            checks: List<GitHubCheckRunDto>
        ): PullRequestReadiness {
            val checksState = when {
                checks.isEmpty() -> ChecksState.NONE
                checks.any { it.status != "completed" } -> ChecksState.PENDING
                checks.any { it.conclusion !in SUCCESSFUL_CHECK_CONCLUSIONS } -> ChecksState.FAILED
                else -> ChecksState.PASSED
            }
            return PullRequestReadiness(
                reviewedHeadSha = pullRequest.head.sha,
                isDraft = pullRequest.draft,
                hasConflicts = pullRequest.mergeable?.not(),
                checksState = checksState,
                patchAvailable = files.isNotEmpty() && files.all { !it.patch.isNullOrBlank() },
                changedLines = files.sumOf { it.changes }
            )
        }
    }
}

enum class ChecksState { NONE, PENDING, PASSED, FAILED }

enum class MergeState { IDLE, REVALIDATING, APPROVING, MERGING, MERGED, BLOCKED, FAILED }

private val SUCCESSFUL_CHECK_CONCLUSIONS = setOf("success", "neutral", "skipped")
