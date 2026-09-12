package com.iqforge.github

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request

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
    private val json: Json = Json { ignoreUnknownKeys = true }
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

    open suspend fun listIssues(owner: String, repo: String): List<GitHubIssueDto> =
        json.decodeFromString(
            ListSerializer(GitHubIssueDto.serializer()),
            get("repos/$owner/$repo/issues?state=open&per_page=30")
        ).filter { it.pullRequest == null }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/$path")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "iQForge-Android")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 403) {
                val remaining = response.header("X-RateLimit-Remaining")
                throw IOException(
                    if (remaining == "0")
                        "GitHub rate limit reached (anonymous access allows 60 requests/hour). Try again later."
                    else "GitHub API request was forbidden."
                )
            }
            if (response.code == 404) throw IOException("Repository not found or not public.")
            if (!response.isSuccessful) throw IOException("GitHub API returned HTTP ${response.code}: ${body.take(240)}")
            body
        }
    }

    private companion object {
        const val BASE_URL = "https://api.github.com"
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
data class GitHubBranchRefDto(val ref: String)

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
    val base: GitHubBranchRefDto,
    val head: GitHubBranchRefDto
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
