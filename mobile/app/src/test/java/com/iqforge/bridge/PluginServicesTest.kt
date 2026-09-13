package com.iqforge.bridge

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginServicesTest {
    private lateinit var server: MockWebServer
    private lateinit var client: LaptopBridgeClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = LaptopBridgeClient()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `search sends the bridge contract and parses sources`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"query":"Compose","results":[{"title":"Compose docs","url":"https://example.dev","snippet":"UI toolkit"}]}"""
        ))

        val results = client.search(server.url("/").toString(), "Compose", 3)

        assertEquals(1, results.size)
        assertEquals("Compose docs", results.single().title)
        val request = server.takeRequest()
        assertEquals("/search", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("\"max_results\":3"))
    }

    @Test fun `health verifies a connector without invoking the model`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"status":"ok","backend":"ollama","model":"qwen"}"""
        ))

        val health = client.health(server.url("/").toString())

        assertEquals("ok", health.status)
        assertEquals("ollama", health.backend)
        assertEquals("qwen", health.model)
        assertEquals("GET", server.takeRequest().method)
    }

    @Test fun `models are populated only from bridge discovery`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"models":[{"id":"qwen2.5vl:7b","parameter_size":"8.3B","quantization":"Q4_K_M","selected":true}]}"""
        ))

        val models = client.models(server.url("/").toString())

        assertEquals(1, models.size)
        assertEquals("qwen2.5vl:7b", models.single().id)
        assertTrue(models.single().selected)
        assertEquals("/models", server.takeRequest().path)
    }

    @Test fun `connector state comes from bridge response`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"connectors":[{"id":"github","name":"GitHub","status":"Connected","detail":"mukesh-dev-git/iqforge","connected":true}]}"""
        ))

        val connectors = client.connectors(server.url("/").toString())

        assertEquals("GitHub", connectors.single().name)
        assertTrue(connectors.single().connected)
        assertEquals("/connectors", server.takeRequest().path)
    }

    @Test fun `deployment status includes gated stage evidence and logs`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"deploy_id":"run1","stage":"build","stage_label":"Building","percent":25,"done":false,"stage_complete":true,"logs":["Build succeeded."],"repo":"iqforge","commit_sha":"abc123","message":"Staging"}"""
        ))

        val status = client.deployStatus(server.url("/").toString())

        assertEquals("run1", status.deployId)
        assertTrue(status.stageComplete)
        assertEquals(listOf("Build succeeded."), status.logs)
        assertEquals("/deploy/status", server.takeRequest().path)
    }

    @Test fun `advancing a deployment names the exact next stage`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"deploy_id":"run1","stage":"test","stage_label":"Running tests","percent":50,"done":false,"stage_complete":false,"logs":[]}"""
        ))

        client.advanceDeploy(server.url("/").toString(), "run1", "test")

        val request = server.takeRequest()
        assertEquals("/deploy/advance", request.path)
        assertTrue(request.body.readUtf8().contains("\"deploy_id\":\"run1\""))
    }
}
