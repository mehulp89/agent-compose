package io.github.mehulp89.agentcompose.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns a conversation, consumes streaming events, and exposes immutable state to any UI.
 *
 * The controller is lifecycle-agnostic. Pass a scope owned by your ViewModel, presenter, or the
 * `rememberAgentChatState` helper in the Compose artifact.
 */
public class AgentConversationController(
    private val engine: AgentEngine,
    private val scope: CoroutineScope,
    private val toolHandler: AgentToolHandler? = null,
    initialMessages: List<AgentMessage> = emptyList(),
    conversationId: String = AgentIds.next("conversation"),
) : Closeable {
    private val _state = MutableStateFlow(
        AgentConversationState(
            conversationId = conversationId,
            messages = initialMessages,
        ),
    )
    private val operationSequence = AtomicLong(0)
    private var activeJob: Job? = null

    public val state: StateFlow<AgentConversationState> = _state.asStateFlow()

    public fun updateDraft(value: String) {
        _state.update { it.copy(draft = value) }
    }

    public fun sendDraft() {
        val draft = state.value.draft
        if (draft.isNotBlank()) send(draft)
    }

    /** Sends a user message. Attachment parts can contain [ImagePart] or [FilePart]. */
    public fun send(
        text: String,
        attachments: List<AgentPart> = emptyList(),
    ) {
        if (state.value.isGenerating || (text.isBlank() && attachments.isEmpty())) return

        val parts = buildList {
            if (text.isNotBlank()) add(TextPart(text.trim()))
            addAll(attachments)
        }
        sendParts(parts)
    }

    /** Stops the current provider stream and keeps the partial response visible. */
    public fun cancel() {
        operationSequence.incrementAndGet()
        activeJob?.cancel()
        activeJob = null
        _state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.isStreaming) message.copy(isStreaming = false) else message
                },
                isGenerating = false,
            )
        }
    }

    /** Retries from the most recent user message, removing the failed/partial turn after it. */
    public fun retryLast() {
        if (state.value.isGenerating) return
        val current = state.value
        val userIndex = current.messages.indexOfLast { it.role == AgentRole.USER }
        if (userIndex < 0) return

        val userMessage = current.messages[userIndex]
        _state.update {
            it.copy(
                messages = it.messages.take(userIndex),
                errorMessage = null,
            )
        }
        sendParts(userMessage.parts)
    }

    /** Approves or denies a pending tool call and resumes the agent with a tool-result message. */
    public fun decideTool(
        toolCallId: String,
        approved: Boolean,
    ) {
        if (state.value.isGenerating) return
        val call = state.value.messages
            .asSequence()
            .flatMap { it.parts.asSequence() }
            .filterIsInstance<ToolCallPart>()
            .firstOrNull { it.id == toolCallId && it.status == ToolCallStatus.AWAITING_APPROVAL }
            ?: return

        launchOperation { operationId ->
            updateToolCall(
                id = toolCallId,
                status = if (approved) ToolCallStatus.RUNNING else ToolCallStatus.DENIED,
            )
            _state.update { it.copy(isGenerating = true, errorMessage = null) }

            val result = if (!approved) {
                ToolResultPart(
                    toolCallId = call.id,
                    content = "The user denied this tool call.",
                    isError = true,
                )
            } else {
                runCatching {
                    toolHandler?.execute(call)
                        ?: ToolResultPart(
                            toolCallId = call.id,
                            content = "No AgentToolHandler was configured for ${call.name}.",
                            isError = true,
                        )
                }.getOrElse { throwable ->
                    ToolResultPart(
                        toolCallId = call.id,
                        content = throwable.message ?: "Tool execution failed.",
                        isError = true,
                    )
                }
            }

            if (approved) {
                updateToolCall(
                    id = toolCallId,
                    status = if (result.isError) ToolCallStatus.FAILED else ToolCallStatus.SUCCEEDED,
                )
            }

            val toolMessage = AgentMessage(
                role = AgentRole.TOOL,
                parts = listOf(result),
            )
            _state.update { it.copy(messages = it.messages + toolMessage) }
            runAgentTurn(operationId, state.value.messages)
        }
    }

    /** Clears every message while retaining the same controller instance. */
    public fun clear() {
        cancel()
        _state.update {
            AgentConversationState(conversationId = AgentIds.next("conversation"))
        }
    }

    override fun close() {
        cancel()
    }

    private fun sendParts(parts: List<AgentPart>) {
        val userMessage = AgentMessage(
            role = AgentRole.USER,
            parts = parts,
        )
        _state.update { current ->
            current.copy(
                messages = current.messages + userMessage,
                draft = "",
                errorMessage = null,
            )
        }
        launchOperation { operationId ->
            runAgentTurn(operationId, state.value.messages)
        }
    }

    private fun launchOperation(block: suspend (Long) -> Unit) {
        activeJob?.cancel()
        val operationId = operationSequence.incrementAndGet()
        activeJob = scope.launch {
            try {
                block(operationId)
            } finally {
                if (operationSequence.get() == operationId) {
                    _state.update { it.copy(isGenerating = false) }
                    activeJob = null
                }
            }
        }
    }

    private suspend fun runAgentTurn(
        operationId: Long,
        requestMessages: List<AgentMessage>,
    ) {
        if (operationSequence.get() != operationId) return

        val assistantId = AgentIds.next("assistant")
        val assistant = AgentMessage(
            id = assistantId,
            role = AgentRole.ASSISTANT,
            parts = emptyList(),
            isStreaming = true,
        )
        _state.update {
            it.copy(
                messages = it.messages + assistant,
                isGenerating = true,
                errorMessage = null,
            )
        }

        val request = AgentRequest(
            messages = requestMessages,
            conversationId = state.value.conversationId,
        )

        try {
            engine.stream(request).collect { event ->
                if (operationSequence.get() != operationId) return@collect
                when (event) {
                    AgentEvent.Started -> Unit
                    is AgentEvent.TextDelta -> appendText(assistantId, event.text)
                    is AgentEvent.ReplaceText -> replaceText(assistantId, event.text)
                    is AgentEvent.CitationAdded -> appendPart(assistantId, event.citation)
                    is AgentEvent.ToolCallRequested -> appendPart(
                        assistantId,
                        event.call.copy(status = ToolCallStatus.AWAITING_APPROVAL),
                    )
                    is AgentEvent.UsageUpdated -> _state.update {
                        it.copy(lastUsage = event.usage)
                    }
                    AgentEvent.Completed -> finishAssistant(assistantId)
                    is AgentEvent.Failed -> throw AgentStreamFailure(event.message, event.cause)
                }
            }
            finishAssistant(assistantId)
        } catch (cancelled: CancellationException) {
            finishAssistant(assistantId)
            throw cancelled
        } catch (throwable: Throwable) {
            val safeMessage = throwable.message ?: "The agent could not complete this response."
            _state.update { current ->
                current.copy(
                    messages = current.messages.map { message ->
                        if (message.id == assistantId) {
                            message.copy(isStreaming = false, errorMessage = safeMessage)
                        } else {
                            message
                        }
                    },
                    errorMessage = safeMessage,
                )
            }
        }
    }

    private fun appendText(messageId: String, delta: String) {
        if (delta.isEmpty()) return
        _state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id != messageId) return@map message
                    val last = message.parts.lastOrNull()
                    if (last is TextPart) {
                        message.copy(parts = message.parts.dropLast(1) + last.copy(text = last.text + delta))
                    } else {
                        message.copy(parts = message.parts + TextPart(delta))
                    }
                },
            )
        }
    }

    private fun replaceText(messageId: String, text: String) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id != messageId) return@map message
                    message.copy(parts = message.parts.filterNot { it is TextPart } + TextPart(text))
                },
            )
        }
    }

    private fun appendPart(messageId: String, part: AgentPart) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id == messageId) message.copy(parts = message.parts + part) else message
                },
            )
        }
    }

    private fun finishAssistant(messageId: String) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id == messageId) message.copy(isStreaming = false) else message
                },
            )
        }
    }

    private fun updateToolCall(id: String, status: ToolCallStatus) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    message.copy(
                        parts = message.parts.map { part ->
                            if (part is ToolCallPart && part.id == id) part.copy(status = status) else part
                        },
                    )
                },
            )
        }
    }

    private class AgentStreamFailure(
        message: String,
        cause: Throwable?,
    ) : RuntimeException(message, cause)
}
