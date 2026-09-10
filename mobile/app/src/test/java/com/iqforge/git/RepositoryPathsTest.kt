package com.iqforge.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RepositoryPathsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun repositoryNameSupportsHttpsAndSshStyleNames() {
        assertEquals("iqforge", RepositoryPaths.repositoryName("https://github.com/team/iqforge.git"))
        assertEquals("iqforge", RepositoryPaths.repositoryName("git@github.com:team/iqforge.git"))
    }

    @Test
    fun uniqueCloneDirectoryDoesNotOverwriteExistingClone() {
        val root = temporaryFolder.newFolder("repos")
        File(root, "iqforge").mkdir()
        assertEquals("iqforge-2", RepositoryPaths.uniqueCloneDirectory(root, "iqforge").name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireInsideRejectsTraversal() {
        val root = temporaryFolder.newFolder("repos")
        RepositoryPaths.requireInside(root, File(root, "../outside"))
    }

    @Test
    fun requireInsideAcceptsDescendant() {
        val root = temporaryFolder.newFolder("repos")
        val child = File(root, "project/src/Main.kt")
        assertTrue(RepositoryPaths.requireInside(root, child).path.endsWith("project${File.separator}src${File.separator}Main.kt"))
    }
}
