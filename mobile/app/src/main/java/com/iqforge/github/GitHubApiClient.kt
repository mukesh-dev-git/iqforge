package com.iqforge.github

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Anonymous, read-only client for GitHub's public REST API. Deliberately separate from
 * [com.iqforge.bridge.LaptopBridgeClient] — this never touches the laptop bridge or any LLM
 * backend, it only fetches PR/issue metadata directly from api.github.com over HTTPS, the same
 * way JGitRepoManager clones repos directly without laptop involvement.
 */
open class GitHubApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val tokenProvider: () -> String? = { null },
    private val baseUrl: String = BASE_URL
) {
    open suspend fun listPullRequests(owner: String, repo: String): List<GitHubPullRequestSummaryDto> =
        json.decodeFromString(
            ListSerializer(GitHubPullRequestSummaryDto.serializer()),
            get("repos/$owner/$repo/pulls?state=open&per_page=30")
        )

    open suspend fun getPullRequest(owner: String, repo: String, number: Int): GitHubPullRequestDetailDto =
        json.decodeFromString(
            GitHubPullRequestDetailDto.serializer(),
            get("repos/$owner/$repo/pulls/$number")
        )

    open suspend fun getPullRequestFiles(owner: String, repo: String, number: Int): List<GitHubPullRequestFileDto> =
        json.decodeFromString(
            ListSerializer(GitHubPullRequestFileDto.serializer()),
            get("repos/$owner/$repo/pulls/$number/files?per_page=100")
        )

    open suspend fun getCheckRuns(owner: String, repo: String, ref: String): GitHubCheckRunsResponse =
        json.decodeFromString(
            GitHubCheckRunsResponse.serializer(),
            get("repos/$owner/$repo/commits/$ref/check-runs")
        )

    open suspend fun getAuthenticatedUser(): GitHubUserDto =
        json.decodeFromString(GitHubUserDto.serializer(), get("user"))

    open suspend fun submitApproval(owner: String, repo: String, number: Int): GitHubReviewDto =
        json.decodeFromString(
            GitHubReviewDto.serializer(),
            request(
                path = "repos/$owner/$repo/pulls/$number/reviews",
                method = "POST",
                body = json.encodeToString(GitHubReviewRequest(event = "APPROVE"))
            )
        )

    open suspend fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        expectedHeadSha: String
    ): GitHubMergeResponse = json.decodeFromString(
        GitHubMergeResponse.serializer(),
        request(
            path = "repos/$owner/$repo/pulls/$number/merge",
            method = "PUT",
            body = json.encodeToString(GitHubMergeRequest(sha = expectedHeadSha, mergeMethod = "squash"))
        )
    )

    open suspend fun listIssues(owner: String, repo: String): List<GitHubIssueDto> =
        json.decodeFromString(
            ListSerializer(GitHubIssueDto.serializer()),
            get("repos/$owner/$repo/issues?state=open&per_page=30")
        ).filter { it.pullRequest == null }

    /** Triggers a real workflow_dispatch run. [workflowFile] is the file name under .github/workflows/. */
    open suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowFile: String,
        ref: String,
        inputs: Map<String, String> = emptyMap()
    ) {
        request(
            path = "repos/$owner/$repo/actions/workflows/$workflowFile/dispatches",
            method = "POST",
            body = json.encodeToString(GitHubDispatchRequest.serializer(), GitHubDispatchRequest(ref, inputs))
        )
    }

    /** Most recent runs for one workflow, newest first — used to find the run a dispatch just created. */
    open suspend fun listWorkflowRuns(owner: String, repo: String, workflowFile: String, perPage: Int = 5): List<GitHubWorkflowRunDto> =
        json.decodeFromString(
            GitHubWorkflowRunsResponse.serializer(),
            get("repos/$owner/$repo/actions/workflows/$workflowFile/runs?per_page=$perPage")
        ).workflowRuns

    /** Per-job (and per-step within each job) status for one run — this is what drives the live checklist. */
    open suspend fun listRunJobs(owner: String, repo: String, runId: Long): List<GitHubJobDto> =
        json.decodeFromString(
            GitHubJobsResponse.serializer(),
            get("repos/$owner/$repo/actions/runs/$runId/jobs")
        ).jobs

    /** Raw plaintext logs for one failed job — fed to the on-device model as real failure evidence. */
    open suspend fun getJobLogs(owner: String, repo: String, jobId: Long): String =
        request(path = "repos/$owner/$repo/actions/jobs/$jobId/logs", method = "GET")

    /** Re-runs only the jobs that failed in this run, on the same commit — a real retry, not a fresh dispatch. */
    open suspend fun rerunFailedJobs(owner: String, repo: String, runId: Long) {
        request(path = "repos/$owner/$repo/actions/runs/$runId/rerun-failed-jobs", method = "POST")
    }

    private suspend fun get(path: String): String = request(path, "GET")

    private suspend fun request(path: String, method: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/$path")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "iQForge-Android")
        tokenProvider()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE)
        val request = requestBuilder.method(method, requestBody).build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (response.code == 403) {
                val remaining = response.header("X-RateLimit-Remaining")
                throw IOException(
                    if (remaining == "0")
                        "GitHub rate limit reached (anonymous access allows 60 requests/hour). Try again later."
                    else "GitHub API request was forbidden."
                )
            }
            if (response.code == 404) {
                val message = when {
                    method == "POST" && path.endsWith("/reviews") ->
                        "GitHub could not submit this approval. The saved token needs Pull requests write access, and GitHub does not allow authors to approve their own pull requests."
                    method == "PUT" && path.endsWith("/merge") ->
                        "GitHub could not merge this pull request. The saved token needs Contents write access (public_repo or repo for a classic token)."
                    else ->
                        "GitHub could not find this resource. Check the repository name and make sure the saved token can access it."
                }
                throw IOException(message)
            }
            if (!response.isSuccessful) throw IOException(githubError(response.code, responseBody))
            responseBody
        }
    }

    private fun githubError(code: Int, body: String): String {
        val message = runCatching {
            json.decodeFromString(GitHubErrorResponse.serializer(), body).message
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(240)
        return "GitHub API returned HTTP $code: $message"
    }

    private companion object {
        const val BASE_URL = "https://api.github.com"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
data class GitHubUserDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class GitHubLabelDto(
    val name: String,
    val color: String? = null
)

@Serializable
data class GitHubBranchRefDto(
    val ref: String,
    val sha: String = ""
)

@Serializable
data class GitHubPullRequestSummaryDto(
    val number: Int,
    val title: String,
    val state: String,
    val draft: Boolean = false,
    val user: GitHubUserDto? = null,
    val body: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String
)

@Serializable
data class GitHubPullRequestDetailDto(
    val number: Int,
    val title: String,
    val state: String,
    val draft: Boolean = false,
    val user: GitHubUserDto? = null,
    val body: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    @SerialName("changed_files") val changedFiles: Int = 0,
    val mergeable: Boolean? = null,
    @SerialName("mergeable_state") val mergeableState: String = "unknown",
    val base: GitHubBranchRefDto,
    val head: GitHubBranchRefDto
)

@Serializable
data class GitHubPullRequestFileDto(
    val filename: String,
    val status: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val patch: String? = null
)

@Serializable
data class GitHubCheckRunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("check_runs") val checkRuns: List<GitHubCheckRunDto> = emptyList()
)

@Serializable
data class GitHubCheckRunDto(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
private data class GitHubReviewRequest(val event: String)

@Serializable
data class GitHubReviewDto(
    val id: Long,
    val state: String = ""
)

@Serializable
private data class GitHubMergeRequest(
    val sha: String,
    @SerialName("merge_method") val mergeMethod: String
)

@Serializable
data class GitHubMergeResponse(
    val sha: String? = null,
    val merged: Boolean = false,
    val message: String = ""
)

@Serializable
private data class GitHubErrorResponse(val message: String = "")

@Serializable
private data class GitHubDispatchRequest(
    val ref: String,
    val inputs: Map<String, String> = emptyMap()
)

@Serializable
data class GitHubWorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<GitHubWorkflowRunDto> = emptyList()
)

@Serializable
data class GitHubWorkflowRunDto(
    val id: Long,
    val status: String,
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("head_sha") val headSha: String = ""
)

@Serializable
data class GitHubJobsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val jobs: List<GitHubJobDto> = emptyList()
)

@Serializable
data class GitHubJobDto(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val steps: List<GitHubJobStepDto> = emptyList()
)

@Serializable
data class GitHubJobStepDto(
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val number: Int
)

@Serializable
data class GitHubIssueDto(
    val number: Int,
    val title: String,
    val state: String,
    val user: GitHubUserDto? = null,
    val body: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String,
    val labels: List<GitHubLabelDto> = emptyList(),
    @SerialName("pull_request") val pullRequest: JsonElement? = null
)
