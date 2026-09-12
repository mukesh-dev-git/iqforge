package com.iqforge.github

/** owner/repo parsed out of an HTTPS GitHub remote URL, e.g. https://github.com/owner/repo.git */
data class GitHubRepoRef(val owner: String, val repo: String) {
    val fullName: String get() = "$owner/$repo"

    companion object {
        private val HTTPS_PATTERN = Regex("""^https://github\.com/([^/]+)/([^/]+?)(\.git)?/?$""")

        fun parse(remoteUrl: String?): GitHubRepoRef? {
            val url = remoteUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return null
            val match = HTTPS_PATTERN.matchEntire(url) ?: return null
            return GitHubRepoRef(match.groupValues[1], match.groupValues[2])
        }
    }
}
