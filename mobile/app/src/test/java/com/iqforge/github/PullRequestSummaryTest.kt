package com.iqforge.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRequestSummaryTest {
    @Test
    fun `cleans markdown and bullet symbols from generated summary`() {
        val cleaned = cleanPullRequestSummary(
            "## Summary\n- Updates the checkout validation.\n* Review the new error handling before merging.",
            "fallback"
        )

        assertEquals("Updates the checkout validation. Review the new error handling before merging.", cleaned)
        assertFalse(cleaned.contains("#"))
        assertFalse(cleaned.contains("*"))
    }

    @Test
    fun `uses readable fallback when inference is unavailable`() {
        val files = listOf(GitHubPullRequestFileDto("Checkout.kt", "modified", 8, 2, 10, "patch"))
        val fallback = fallbackPullRequestSummary("Fix checkout validation", files)

        assertEquals(fallback, cleanPullRequestSummary("ERROR: NPU offline", fallback))
        assertTrue(fallback.contains("one file with 8 additions and 2 deletions"))
    }
}
