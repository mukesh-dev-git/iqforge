package com.iqforge.git

import java.io.File

data class Repo(val root: File, val name: String)

interface RepoManager {
    suspend fun clone(url: String, into: File): Repo
    suspend fun pull(repo: Repo)
    suspend fun commit(repo: Repo, message: String, paths: List<String>)
    suspend fun push(repo: Repo)
}
