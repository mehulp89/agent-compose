package io.github.mehulp89.agentcompose.firebaseai

import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FileDataPart
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Part
import com.google.firebase.ai.type.TextPart as FirebaseTextPart
import io.github.mehulp89.agentcompose.core.AgentEngine
import io.github.mehulp89.agentcompose.core.AgentEvent
import io.github.mehulp89.agentcompose.core.AgentMessage
import io.github.mehulp89.agentcompose.core.AgentPart
import io.github.mehulp89.agentcompose.core.AgentRequest
import io.github.mehulp89.agentcompose.core.AgentRole
import io.github.mehulp89.agentcompose.core.AgentUsage
import io.github.mehulp89.agentcompose.core.CitationPart
import io.github.mehulp89.agentcompose.core.FilePart
import io.github.mehulp89.agentcompose.core.ImagePart
import io.github.mehulp89.agentcompose.core.TextPart
import io.github.mehulp89.agentcompose.core.ToolCallPart
import io.github.mehulp89.agentcompose.core.ToolResultPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Maps a provider-neutral request to the Firebase AI Logic content format. */
public fun interface FirebaseAiContentMapper {
    public fun map(request: AgentRequest): List<Content>
}

/**
 * A streaming [AgentEngine] backed by Firebase AI Logic.
 *
 * Construct the Firebase [GenerativeModel] in the host app so model name, safety settings, tools,
 * App Check, and backend selection remain under application control. Function calls emitted by the
 * model become approval-gated AgentCompose tool calls.
 */
public class FirebaseAiAgentEngine(
    private val model: GenerativeModel,
    private val contentMapper: FirebaseAiContentMapper = DefaultFirebaseAiContentMapper,
) : AgentEngine {
    override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started)
        val emittedCalls = mutableSetOf<String>()

        try {
            model.generateContentStream(contentMapper.map(request)).collect { response ->
                response.text?.takeIf(String::isNotEmpty)?.let { emit(AgentEvent.TextDelta(it)) }

                response.functionCalls.forEachIndexed { index, call ->
                    val key = call.id ?: "${call.name}:${call.args}:$index"
                    if (emittedCalls.add(key)) {
                        emit(
                            AgentEvent.ToolCallRequested(
                                ToolCallPart(
                                    id = call.id ?: "firebase-${key.hashCode().toUInt()}",
                                    name = call.name,
                                    arguments = call.args.mapValues { (_, value) ->
                                        (value as? JsonPrimitive)?.contentOrNull ?: value.toString()
                                    },
                                    explanation = "Firebase AI Logic requested this function.",
                                ),
                            ),
                        )
                    }
                }

                response.usageMetadata?.let { usage ->
                    emit(
                        AgentEvent.UsageUpdated(
                            AgentUsage(
                                inputTokens = usage.promptTokenCount.toLong(),
                                outputTokens = usage.candidatesTokenCount?.toLong(),
                                totalTokens = usage.totalTokenCount.toLong(),
                            ),
                        ),
                    )
                }
            }
            emit(AgentEvent.Completed)
        } catch (throwable: Throwable) {
            emit(
                AgentEvent.Failed(
                    message = throwable.message ?: "Firebase AI Logic could not complete the response.",
                    cause = throwable,
                ),
            )
        }
    }
}

/** Default mapping for text, citations, Cloud Storage files, and function calls/results. */
public object DefaultFirebaseAiContentMapper : FirebaseAiContentMapper {
    override fun map(request: AgentRequest): List<Content> {
        val callsById = request.messages
            .flatMap(AgentMessage::parts)
            .filterIsInstance<ToolCallPart>()
            .associateBy(ToolCallPart::id)

        return request.messages
            .mapNotNull { message -> mapMessage(message, callsById) }
            .mergeAdjacentTextRoles()
    }

    private fun mapMessage(
        message: AgentMessage,
        callsById: Map<String, ToolCallPart>,
    ): Content? {
        val role = when (message.role) {
            AgentRole.SYSTEM, AgentRole.USER -> "user"
            AgentRole.ASSISTANT -> "model"
            AgentRole.TOOL -> "function"
        }
        val parts = message.parts.mapNotNull { part -> mapPart(part, message.role, callsById) }
        if (parts.isEmpty()) return null

        val prefixedParts = if (message.role == AgentRole.SYSTEM) {
            listOf(FirebaseTextPart("System instruction:\n")) + parts
        } else {
            parts
        }
        return Content(role, prefixedParts)
    }

    private fun mapPart(
        part: AgentPart,
        role: AgentRole,
        callsById: Map<String, ToolCallPart>,
    ): Part? = when (part) {
        is TextPart -> FirebaseTextPart(part.text)
        is CitationPart -> FirebaseTextPart(
            "Source: ${part.title} (${part.url})" +
                part.snippet?.let { " — $it" }.orEmpty(),
        )
        is FilePart -> if (part.uri.startsWith("gs://") && part.mimeType != null) {
            FileDataPart(part.uri, requireNotNull(part.mimeType))
        } else {
            FirebaseTextPart("Attached file: ${part.name} (${part.uri})")
        }
        is ImagePart -> FirebaseTextPart(
            "Attached image URI: ${part.uri}" +
                part.contentDescription?.let { " ($it)" }.orEmpty(),
        )
        is ToolCallPart -> FunctionCallPart(
            name = part.name,
            args = part.arguments.mapValues { (_, value) -> JsonPrimitive(value) },
            id = part.id,
        )
        is ToolResultPart -> {
            val call = callsById[part.toolCallId]
            if (call == null || role != AgentRole.TOOL) {
                FirebaseTextPart(part.content)
            } else {
                FunctionResponsePart(
                    name = call.name,
                    response = JsonObject(
                        mapOf(
                            "content" to JsonPrimitive(part.content),
                            "isError" to JsonPrimitive(part.isError),
                        ),
                    ),
                    id = call.id,
                )
            }
        }
    }

    private fun List<Content>.mergeAdjacentTextRoles(): List<Content> = fold(emptyList()) { result, item ->
        val previous = result.lastOrNull()
        val mergeable = item.role != "function" && previous?.role == item.role
        if (mergeable && previous != null) {
            result.dropLast(1) + Content(item.role, previous.parts + item.parts)
        } else {
            result + item
        }
    }
}
