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
            if (response.code == 404) throw IOException("Repository not found or not public.")
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
