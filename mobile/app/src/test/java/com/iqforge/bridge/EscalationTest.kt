package com.iqforge.bridge

import com.iqforge.AgentViewModel
import com.iqforge.FeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for escalation logic in [AgentViewModel].
 *
 * Uses a [FakeBridgeClient] to avoid any real network calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EscalationTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    // ------------------------------------------------------------------
    // inferTask — keyword mapping
    // ------------------------------------------------------------------

    @Test fun `inferTask maps review keyword to REVIEW`() {
        val vm = AgentViewModel(com.iqforge.engine.OfflineEngine(), FakeBridgeClient())
        assertEquals(BridgeTask.REVIEW, vm.inferTask("please review this diff"))
    }

    @Test fun `inferTask maps debug keyword to DEBUG`() {
        val vm = AgentViewModel(com.iqforge.engine.OfflineEngine(), FakeBridgeClient())
        assertEquals(BridgeTask.DEBUG, vm.inferTask("debug the crash"))
    }

    @Test fun `inferTask maps crash keyword to DEBUG`() {
        val vm = AgentViewModel(com.iqforge.engine.OfflineEngine(), FakeBridgeClient())
        assertEquals(BridgeTask.DEBUG, vm.inferTask("app crash on launch"))
    }

    @Test fun `inferTask maps explain keyword to EXPLAIN`() {
        val vm = AgentViewModel(com.iqforge.engine.OfflineEngine(), FakeBridgeClient())
        assertEquals(BridgeTask.EXPLAIN, vm.inferTask("explain this function"))
    }

    @Test fun `inferTask falls back to WRITE for unrecognised prompts`() {
        val vm = AgentViewModel(com.iqforge.engine.OfflineEngine(), FakeBridgeClient())
        assertEquals(BridgeTask.WRITE, vm.inferTask("add a null check here"))
    }

    // ------------------------------------------------------------------
    // escalate() — blank bridge URL guard
    // ------------------------------------------------------------------

    @Test fun `escalate with blank URL produces EscalateError immediately without network call`() = runTest {
        val fake = FakeBridgeClient()
        val vm = AgentViewModel(com.iqforge.engine.OfflineEngine(), fake)
        vm.updateBridgeUrl("")

        // Put a prompt card in the feed first so replaceLast has a target.
        vm.forceEscalatePrompt("fix null", "", BridgeTask.WRITE)

        vm.escalate("fix null", "", BridgeTask.WRITE)
        testDispatcher.scheduler.advanceUntilIdle()

        val last = vm.feed.last()
        assertTrue("Expected EscalateError, got $last", last is FeedItem.EscalateError)
        assertEquals(0, fake.callCount)
    }

    // ------------------------------------------------------------------
    // escalate() — success path
    // ------------------------------------------------------------------

    @Test fun `escalate success replaces EscalatePrompt with LaptopReply`() = runTest {
        val fake = FakeBridgeClient(answer = "Laptop says: looks good")
        val vm = AgentViewModel(com.iqforge.engine.OfflineEngine(), fake)
        vm.updateBridgeUrl("http://192.168.1.50:8000")
        vm.forceEscalatePrompt("explain this", "fun greet() = 1", BridgeTask.EXPLAIN)

        vm.escalate("explain this", "fun greet() = 1", BridgeTask.EXPLAIN)
        testDispatcher.scheduler.advanceUntilIdle()

        val last = vm.feed.last()
        assertTrue("Expected LaptopReply, got $last", last is FeedItem.LaptopReply)
        assertEquals("Laptop says: looks good", (last as FeedItem.LaptopReply).text)
        assertEquals(1, fake.callCount)
    }

    // ------------------------------------------------------------------
    // escalate() — failure path; offline reply preserved
    // ------------------------------------------------------------------

    @Test fun `escalate failure leaves offline Reply intact and adds EscalateError`() = runTest {
        val fake = FakeBridgeClient(throws = IOException("Connection refused"))
        val vm = AgentViewModel(com.iqforge.engine.OfflineEngine(), fake)
        vm.updateBridgeUrl("http://192.168.1.50:8000")

        // Simulate: offline reply already in feed, then prompt card.
        vm.forceReply("Offline: no issues found")
        vm.forceEscalatePrompt("review", "", BridgeTask.REVIEW)

        vm.escalate("review", "", BridgeTask.REVIEW)
        testDispatcher.scheduler.advanceUntilIdle()

        // Offline reply is still present.
        assertTrue(vm.feed.any { it is FeedItem.Reply && (it as FeedItem.Reply).text == "Offline: no issues found" })
        // Last card is an error.
        val last = vm.feed.last()
        assertTrue("Expected EscalateError, got $last", last is FeedItem.EscalateError)
        assertTrue((last as FeedItem.EscalateError).message.contains("Connection refused"))
    }

    // ------------------------------------------------------------------
    // retry from EscalateError
    // ------------------------------------------------------------------

    @Test fun `retry from EscalateError re-issues escalation and produces LaptopReply on success`() = runTest {
        val fake = FakeBridgeClient(answer = "Retried and got result")
        val vm = AgentViewModel(com.iqforge.engine.OfflineEngine(), fake)
        vm.updateBridgeUrl("http://192.168.1.50:8000")
        vm.forceEscalateError("Connection refused", "write null guard", "", BridgeTask.WRITE)

        // User taps Retry — escalate called with the same payload.
        vm.escalate("write null guard", "", BridgeTask.WRITE)
        testDispatcher.scheduler.advanceUntilIdle()

        val last = vm.feed.last()
        assertTrue("Expected LaptopReply after retry, got $last", last is FeedItem.LaptopReply)
        assertEquals("Retried and got result", (last as FeedItem.LaptopReply).text)
    }

    // ------------------------------------------------------------------
    // Test helpers to directly prime the feed (bypasses OfflineEngine)
    // ------------------------------------------------------------------

    private fun AgentViewModel.forceReply(text: String) {
        feed = feed + FeedItem.Reply(text)
    }

    private fun AgentViewModel.forceEscalatePrompt(prompt: String, context: String, task: BridgeTask) {
        feed = feed + FeedItem.EscalatePrompt(prompt = prompt, context = context, task = task)
    }

    private fun AgentViewModel.forceEscalateError(message: String, prompt: String, context: String, task: BridgeTask) {
        feed = feed + FeedItem.EscalateError(message = message, prompt = prompt, context = context, task = task)
    }
}

// ---------------------------------------------------------------------------
// Fake bridge client — no real network calls in tests
// ---------------------------------------------------------------------------

class FakeBridgeClient(
    private val answer: String = "ok",
    private val throws: Throwable? = null
) : LaptopBridgeClient() {

    var callCount = 0; private set

    override suspend fun escalate(
        laptopUrl: String,
        task: BridgeTask,
        context: String,
        instruction: String
    ): String {
        callCount++
        if (throws != null) throw throws
        return answer
    }
}
