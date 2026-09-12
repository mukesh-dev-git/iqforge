package com.iqforge.github

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubApiClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `loads PR patch and check runs with saved token`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"filename":"src/Payment.kt","status":"modified","additions":4,"deletions":1,"changes":5,"patch":"@@ -1 +1 @@\n-old\n+new"}]"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"total_count":1,"check_runs":[{"id":9,"name":"unit-tests","status":"completed","conclusion":"success"}]}"""
            )
        )
        val client = GitHubApiClient(
            tokenProvider = { "test-token" },
            baseUrl = server.url("api/v3/").toString()
        )

        val files = client.getPullRequestFiles("acme", "payments", 42)
        val checks = client.getCheckRuns("acme", "payments", "abc123")

        assertEquals("src/Payment.kt", files.single().filename)
        assertTrue(files.single().patch!!.contains("+new"))
        assertEquals("unit-tests", checks.checkRuns.single().name)

        val filesRequest = server.takeRequest()
        assertEquals("/api/v3/repos/acme/payments/pulls/42/files?per_page=100", filesRequest.path)
        assertEquals("Bearer test-token", filesRequest.getHeader("Authorization"))
        val checksRequest = server.takeRequest()
        assertEquals("/api/v3/repos/acme/payments/commits/abc123/check-runs", checksRequest.path)
    }

    @Test
    fun `readiness blocks conflicts and failed checks`() {
        val pullRequest = pullRequest(mergeable = false)
        val files = listOf(fileWithPatch())
        val checks = listOf(
            GitHubCheckRunDto(1, "unit-tests", "completed", "failure")
        )

        val readiness = PullRequestReadiness.from(pullRequest, files, checks)

        assertEquals(true, readiness.hasConflicts)
        assertEquals(ChecksState.FAILED, readiness.checksState)
        assertFalse(readiness.isReadyForReview)
    }

    @Test
    fun `readiness accepts clean PR with successful checks and complete patch`() {
        val readiness = PullRequestReadiness.from(
            pullRequest(mergeable = true),
            listOf(fileWithPatch()),
            listOf(GitHubCheckRunDto(1, "unit-tests", "completed", "success"))
        )

        assertEquals(false, readiness.hasConflicts)
        assertEquals(ChecksState.PASSED, readiness.checksState)
        assertEquals(5, readiness.changedLines)
        assertTrue(readiness.isReadyForReview)
    }

    private fun pullRequest(mergeable: Boolean?) = GitHubPullRequestDetailDto(
        number = 42,
        title = "Prevent duplicate refund",
        state = "open",
        createdAt = "2026-09-13T00:00:00Z",
        updatedAt = "2026-09-13T00:00:00Z",
        htmlUrl = "https://github.com/acme/payments/pull/42",
        mergeable = mergeable,
        base = GitHubBranchRefDto("main", "base123"),
        head = GitHubBranchRefDto("fix/refund", "abc123")
    )

    private fun fileWithPatch() = GitHubPullRequestFileDto(
        filename = "src/Payment.kt",
        status = "modified",
        additions = 4,
        deletions = 1,
        changes = 5,
        patch = "@@ -1 +1 @@\n-old\n+new"
    )
}
