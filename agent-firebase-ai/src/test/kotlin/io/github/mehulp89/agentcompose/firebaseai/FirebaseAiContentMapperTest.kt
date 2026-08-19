package io.github.mehulp89.agentcompose.firebaseai

import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import io.github.mehulp89.agentcompose.core.AgentMessage
import io.github.mehulp89.agentcompose.core.AgentRequest
import io.github.mehulp89.agentcompose.core.AgentRole
import io.github.mehulp89.agentcompose.core.ToolCallPart
import io.github.mehulp89.agentcompose.core.ToolResultPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseAiContentMapperTest {
    @Test
    fun `maps function call and result with matching id`() {
        val call = ToolCallPart("call-1", "save_note", mapOf("title" to "Ideas"))
        val request = AgentRequest(
            conversationId = "conversation-1",
            messages = listOf(
                AgentMessage.text(AgentRole.USER, "Save this"),
                AgentMessage(role = AgentRole.ASSISTANT, parts = listOf(call)),
                AgentMessage(
                    role = AgentRole.TOOL,
                    parts = listOf(ToolResultPart("call-1", "Saved")),
                ),
            ),
        )

        val content = DefaultFirebaseAiContentMapper.map(request)

        assertEquals(listOf("user", "model", "function"), content.map { it.role })
        assertEquals("call-1", content[1].parts.filterIsInstance<FunctionCallPart>().single().id)
        assertEquals("call-1", content[2].parts.filterIsInstance<FunctionResponsePart>().single().id)
        assertTrue(content[2].parts.single() is FunctionResponsePart)
    }
}
