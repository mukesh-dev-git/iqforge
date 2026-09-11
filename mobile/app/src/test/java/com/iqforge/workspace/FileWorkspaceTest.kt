package com.iqforge.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileWorkspaceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val workspace = FileWorkspace()

    @Test
    fun treeHidesGitMetadataAndExpandsRequestedDirectory() {
        val root = temporaryFolder.newFolder("repo")
        root.resolve(".git").mkdir()
        root.resolve("README.md").writeText("hello")
        root.resolve("src").mkdir()
        root.resolve("src/Main.kt").writeText("fun main() = Unit")

        val collapsed = workspace.visibleEntries(root, emptySet())
        assertFalse(collapsed.any { it.name == ".git" })
        assertTrue(collapsed.any { it.relativePath == "src" })
        assertFalse(collapsed.any { it.relativePath == "src/Main.kt" })

        val expanded = workspace.visibleEntries(root, setOf("src"))
        assertTrue(expanded.any { it.relativePath == "src/Main.kt" && it.depth == 1 })
    }

    @Test
    fun textFileRoundTrip() {
        val root = temporaryFolder.newFolder("repo")
        val file = root.resolve("hello.txt").apply { writeText("before") }
        workspace.writeText(root, file, "after")
        assertEquals("after", workspace.readText(root, file))
    }

    @Test
    fun artifactsContainOnlyRealSupportedFilesOutsideGitMetadata() {
        val root = temporaryFolder.newFolder("artifacts")
        root.resolve(".git").mkdir()
        root.resolve(".git/config").writeText("secret")
        root.resolve("src").mkdir()
        root.resolve("src/Main.kt").writeText("fun main() = Unit")
        root.resolve("README.md").writeText("# Project")
        root.resolve("preview.png").writeBytes(byteArrayOf(1, 2, 3))

        val artifacts = workspace.artifactFiles(root)

        assertEquals(listOf("README.md", "src/Main.kt"), artifacts.map { it.relativePath }.sorted())
        assertTrue(artifacts.all { !it.directory && it.file.exists() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun binaryFileIsRejected() {
        val root = temporaryFolder.newFolder("repo")
        val file = root.resolve("image.bin").apply { writeBytes(byteArrayOf(1, 0, 2)) }
        workspace.readText(root, file)
    }
}
