package com.iqforge.git

import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JGitRepoManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test(expected = IllegalArgumentException::class)
    fun createRejectsUnsafeProjectNames() = runTest {
        JGitRepoManager(temporaryFolder.newFolder("repositories")).create("../outside", "")
    }
}
