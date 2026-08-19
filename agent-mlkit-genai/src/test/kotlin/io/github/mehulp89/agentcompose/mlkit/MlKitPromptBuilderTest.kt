package io.github.mehulp89.agentcompose.mlkit

import io.github.mehulp89.agentcompose.core.AgentMessage
import io.github.mehulp89.agentcompose.core.AgentRequest
import io.github.mehulp89.agentcompose.core.AgentRole
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitPromptBuilderTest {
    @Test
    fun `keeps roles and newest prompt in bounded context`() {
        val request = AgentRequest(
            conversationId = "conversation-1",
            messages = listOf(
                AgentMessage.text(AgentRole.SYSTEM, "Be concise"),
                AgentMessage.text(AgentRole.USER, "Hello Nano"),
            ),
        )

        val prompt = DefaultMlKitPromptBuilder.build(request)

        assertTrue(prompt.contains("SYSTEM: Be concise"))
        assertTrue(prompt.endsWith("USER: Hello Nano"))
        assertTrue(prompt.length <= 12_000)
    }
}
