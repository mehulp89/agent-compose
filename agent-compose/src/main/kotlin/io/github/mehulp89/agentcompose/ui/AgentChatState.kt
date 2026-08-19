package io.github.mehulp89.agentcompose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.mehulp89.agentcompose.core.AgentConversationController
import io.github.mehulp89.agentcompose.core.AgentConversationState
import io.github.mehulp89.agentcompose.core.AgentEngine
import io.github.mehulp89.agentcompose.core.AgentMessage
import io.github.mehulp89.agentcompose.core.AgentPart
import io.github.mehulp89.agentcompose.core.AgentToolHandler
import kotlinx.coroutines.flow.StateFlow

/** Compose-friendly facade over [AgentConversationController]. */
@Stable
public class AgentChatState internal constructor(
    internal val controller: AgentConversationController,
) {
    public val conversation: StateFlow<AgentConversationState>
        get() = controller.state

    public fun updateDraft(value: String) {
        controller.updateDraft(value)
    }

    public fun sendDraft() {
        controller.sendDraft()
    }

    public fun send(text: String, attachments: List<AgentPart> = emptyList()) {
        controller.send(text, attachments)
    }

    public fun cancel() {
        controller.cancel()
    }

    public fun retryLast() {
        controller.retryLast()
    }

    public fun decideTool(toolCallId: String, approved: Boolean) {
        controller.decideTool(toolCallId, approved)
    }

    public fun clear() {
        controller.clear()
    }
}

/**
 * Beginner-friendly state creation. The controller and its coroutine scope are disposed with the
 * composition. Put the controller in a ViewModel instead if the chat must survive navigation.
 */
@Composable
public fun rememberAgentChatState(
    engine: AgentEngine,
    toolHandler: AgentToolHandler? = null,
    initialMessages: List<AgentMessage> = emptyList(),
    conversationId: String? = null,
): AgentChatState {
    val scope = rememberCoroutineScope()
    val controller = remember(engine, toolHandler, initialMessages, conversationId, scope) {
        if (conversationId == null) {
            AgentConversationController(
                engine = engine,
                scope = scope,
                toolHandler = toolHandler,
                initialMessages = initialMessages,
            )
        } else {
            AgentConversationController(
                engine = engine,
                scope = scope,
                toolHandler = toolHandler,
                initialMessages = initialMessages,
                conversationId = conversationId,
            )
        }
    }
    DisposableEffect(controller) {
        onDispose(controller::close)
    }
    return remember(controller) { AgentChatState(controller) }
}

/** Wraps a controller owned by a ViewModel or another lifecycle-aware component. */
@Composable
public fun rememberAgentChatState(
    controller: AgentConversationController,
): AgentChatState = remember(controller) { AgentChatState(controller) }
