package io.github.mehulp89.agentcompose.testing

import io.github.mehulp89.agentcompose.core.AgentConversationController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentTestingTest {
    @Test
    fun `scripted engine streams deterministic chunks and records request`() = runTest {
        val engine = ScriptedAgentEngine(AgentScript.text("Hello world", chunkSize = 3))
        val controller = AgentConversationController(engine, this)

        controller.send("Hi")
        advanceUntilIdle()

        assertEquals("Hello world", controller.state.value.assistantText())
        assertEquals("Hi", engine.requests.single().messages.single().text)
        assertEquals(0, engine.remainingTurnCount)
    }
}
