package com.iqforge.github

/** owner/repo parsed out of an HTTPS GitHub remote URL, e.g. https://github.com/owner/repo.git */
data class GitHubRepoRef(val owner: String, val repo: String) {
    val fullName: String get() = "$owner/$repo"

    companion object {
        private val HTTPS_PATTERN = Regex("""^https://github\.com/([^/]+)/([^/]+?)(\.git)?/?$""")
        private val SHORTHAND_PATTERN = Regex("""^([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+?)(\.git)?$""")

        fun parse(remoteUrl: String?): GitHubRepoRef? {
            val url = remoteUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return null
            val match = HTTPS_PATTERN.matchEntire(url) ?: return null
            return GitHubRepoRef(match.groupValues[1], match.groupValues[2])
        }

        /** Accepts either a full GitHub URL or a bare "owner/repo" shorthand, for user-typed input. */
        fun parseFlexible(input: String?): GitHubRepoRef? {
            val trimmed = input?.trim().takeUnless { it.isNullOrBlank() } ?: return null
            parse(trimmed)?.let { return it }
            val match = SHORTHAND_PATTERN.matchEntire(trimmed) ?: return null
            return GitHubRepoRef(match.groupValues[1], match.groupValues[2])
        }
    }
}
