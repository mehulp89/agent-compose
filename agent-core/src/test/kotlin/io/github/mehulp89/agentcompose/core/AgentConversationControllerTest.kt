package io.github.mehulp89.agentcompose.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentConversationControllerTest {
    @Test
    fun `streaming deltas become one assistant message`() = runTest {
        val engine = AgentEngine {
            flow {
                emit(AgentEvent.Started)
                emit(AgentEvent.TextDelta("Hello"))
                emit(AgentEvent.TextDelta(" world"))
                emit(AgentEvent.Completed)
            }
        }
        val controller = AgentConversationController(engine, this)

        controller.send("Hi")
        advanceUntilIdle()

        assertEquals(2, controller.state.value.messages.size)
        assertEquals("Hi", controller.state.value.messages[0].text)
        assertEquals("Hello world", controller.state.value.messages[1].text)
        assertFalse(controller.state.value.isGenerating)
    }

    @Test
    fun `approved tool result is returned to the engine`() = runTest {
        var calls = 0
        val engine = AgentEngine { request ->
            flow {
                calls++
                if (request.messages.last().role == AgentRole.TOOL) {
                    emit(AgentEvent.TextDelta("Saved successfully."))
                } else {
                    emit(
                        AgentEvent.ToolCallRequested(
                            ToolCallPart(
                                id = "save-1",
                                name = "save_note",
                                arguments = mapOf("title" to "Ideas"),
                            ),
                        ),
                    )
                }
                emit(AgentEvent.Completed)
            }
        }
        val handler = AgentToolHandler { call ->
            ToolResultPart(call.id, "note-id-42")
        }
        val controller = AgentConversationController(engine, this, handler)

        controller.send("Save an idea")
        advanceUntilIdle()
        assertTrue(controller.state.value.pendingToolCalls.isNotEmpty())

        controller.decideTool("save-1", approved = true)
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals("Saved successfully.", controller.state.value.messages.last().text)
        assertTrue(controller.state.value.pendingToolCalls.isEmpty())
    }

    @Test
    fun `failed provider event is exposed without losing partial text`() = runTest {
        val engine = AgentEngine {
            flow {
                emit(AgentEvent.TextDelta("Partial"))
                emit(AgentEvent.Failed("Network unavailable"))
            }
        }
        val controller = AgentConversationController(engine, this)

        controller.send("Hello")
        advanceUntilIdle()

        assertEquals("Partial", controller.state.value.messages.last().text)
        assertEquals("Network unavailable", controller.state.value.errorMessage)
        assertTrue(controller.state.value.canRetry)
    }
}
