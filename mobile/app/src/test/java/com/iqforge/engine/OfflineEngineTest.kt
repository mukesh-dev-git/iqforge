package com.iqforge.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineEngineTest {
    private val engine = OfflineEngine()

    @Test fun `review finds unsafe non-null assertion at changed line`() = runBlocking {
        val findings = engine.review("@@ -8,1 +8,1 @@\n+ val name = user!!.name")
        assertEquals(1, findings.size)
        assertEquals(8, findings.single().line)
        assertEquals(Severity.BUG, findings.single().severity)
    }

    @Test fun `review reports debug logging and todo`() = runBlocking {
        val findings = engine.review("@@ -2,0 +2,2 @@\n+ println(\"debug\")\n+ // TODO remove")
        assertEquals(listOf(Severity.WARNING, Severity.INFO), findings.map { it.severity })
    }

    @Test fun `debug recognizes null pointer failures`() = runBlocking {
        assertTrue(engine.debug("java.lang.NullPointerException", "val user: User? = null").contains("null guard"))
    }

    @Test fun `write suggests a nullable guard`() = runBlocking {
        val suggestion = engine.write("fix the null pointer", "return user.name")
        assertTrue(suggestion.contains("user?.name"))
    }

    @Test fun `explain describes a function`() = runBlocking {
        assertTrue(engine.explain("fun greet() = println(\"hi\")").contains("a function"))
    }
}
