package io.github.mehulp89.agentcompose.core

import java.util.concurrent.atomic.AtomicLong

/** Identifies who produced a message in an agent conversation. */
public enum class AgentRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

/** One renderable or machine-readable part of an [AgentMessage]. */
public sealed interface AgentPart

/** Plain or Markdown-formatted text. */
public data class TextPart(
    val text: String,
) : AgentPart

/** An image selected by the user or returned by an agent. */
public data class ImagePart(
    val uri: String,
    val contentDescription: String? = null,
    val mimeType: String? = null,
) : AgentPart

/** A generic file attachment. The library never opens the URI by itself. */
public data class FilePart(
    val uri: String,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
) : AgentPart

/** A source cited by an assistant response. */
public data class CitationPart(
    val title: String,
    val url: String,
    val snippet: String? = null,
) : AgentPart

/** Current lifecycle state of a tool call. */
public enum class ToolCallStatus {
    AWAITING_APPROVAL,
    APPROVED,
    RUNNING,
    SUCCEEDED,
    DENIED,
    FAILED,
}

/**
 * A tool that an agent would like the host application to run.
 *
 * Arguments intentionally use strings so the core module has no JSON-library dependency. Provider
 * adapters can serialize richer values into JSON strings when needed.
 */
public data class ToolCallPart(
    val id: String,
    val name: String,
    val arguments: Map<String, String> = emptyMap(),
    val explanation: String? = null,
    val status: ToolCallStatus = ToolCallStatus.AWAITING_APPROVAL,
) : AgentPart

/** The host application's result for a [ToolCallPart]. */
public data class ToolResultPart(
    val toolCallId: String,
    val content: String,
    val isError: Boolean = false,
) : AgentPart

/** Token usage when a provider exposes it. All fields are optional across providers. */
public data class AgentUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
)

/** A single immutable conversation message. */
public data class AgentMessage(
    val id: String = AgentIds.next("message"),
    val role: AgentRole,
    val parts: List<AgentPart>,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val errorMessage: String? = null,
) {
    public val text: String
        get() = parts.filterIsInstance<TextPart>().joinToString(separator = "") { it.text }

    public companion object {
        public fun text(
            role: AgentRole,
            text: String,
            id: String = AgentIds.next("message"),
        ): AgentMessage = AgentMessage(
            id = id,
            role = role,
            parts = listOf(TextPart(text)),
        )
    }
}

/** Complete input passed to an [AgentEngine]. */
public data class AgentRequest(
    val messages: List<AgentMessage>,
    val conversationId: String,
    val metadata: Map<String, String> = emptyMap(),
)

/** Incremental events emitted by a streaming [AgentEngine]. */
public sealed interface AgentEvent {
    public data object Started : AgentEvent

    public data class TextDelta(val text: String) : AgentEvent

    public data class ReplaceText(val text: String) : AgentEvent

    public data class CitationAdded(val citation: CitationPart) : AgentEvent

    public data class ToolCallRequested(val call: ToolCallPart) : AgentEvent

    public data class UsageUpdated(val usage: AgentUsage) : AgentEvent

    public data object Completed : AgentEvent

    public data class Failed(
        val message: String,
        val cause: Throwable? = null,
    ) : AgentEvent
}

/** Immutable state observed by a UI. */
public data class AgentConversationState(
    val conversationId: String = AgentIds.next("conversation"),
    val messages: List<AgentMessage> = emptyList(),
    val draft: String = "",
    val isGenerating: Boolean = false,
    val lastUsage: AgentUsage? = null,
    val errorMessage: String? = null,
) {
    public val pendingToolCalls: List<ToolCallPart>
        get() = messages
            .flatMap { it.parts }
            .filterIsInstance<ToolCallPart>()
            .filter { it.status == ToolCallStatus.AWAITING_APPROVAL }

    public val canRetry: Boolean
        get() = messages.any { it.role == AgentRole.USER } && !isGenerating
}

internal object AgentIds {
    private val sequence = AtomicLong(0)

    fun next(prefix: String): String =
        "$prefix-${System.currentTimeMillis()}-${sequence.incrementAndGet()}"
}
