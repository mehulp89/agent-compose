package io.github.mehulp89.agentcompose.testing

import io.github.mehulp89.agentcompose.core.AgentConversationState
import io.github.mehulp89.agentcompose.core.AgentEngine
import io.github.mehulp89.agentcompose.core.AgentEvent
import io.github.mehulp89.agentcompose.core.AgentRequest
import io.github.mehulp89.agentcompose.core.AgentToolHandler
import io.github.mehulp89.agentcompose.core.TextPart
import io.github.mehulp89.agentcompose.core.ToolCallPart
import io.github.mehulp89.agentcompose.core.ToolResultPart
import java.util.Collections
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

/** One deterministic action in a [ScriptedAgentEngine] turn. */
public sealed interface AgentScriptStep {
    public data class Emit(val event: AgentEvent) : AgentScriptStep
    public data class Wait(val milliseconds: Long) : AgentScriptStep
    public data class Throw(val throwable: Throwable) : AgentScriptStep
}

/** A reusable script for one call to [AgentEngine.stream]. */
public data class AgentScript(
    val steps: List<AgentScriptStep>,
) {
    public companion object {
        public fun text(
            value: String,
            chunkSize: Int = value.length.coerceAtLeast(1),
            delayMillis: Long = 0,
        ): AgentScript = AgentScript(
            buildList {
                add(AgentScriptStep.Emit(AgentEvent.Started))
                value.chunked(chunkSize.coerceAtLeast(1)).forEach { chunk ->
                    add(AgentScriptStep.Emit(AgentEvent.TextDelta(chunk)))
                    if (delayMillis > 0) add(AgentScriptStep.Wait(delayMillis))
                }
                add(AgentScriptStep.Emit(AgentEvent.Completed))
            },
        )
    }
}

/**
 * A deterministic fake engine that consumes one script per request and records every request.
 */
public class ScriptedAgentEngine(
    scripts: List<AgentScript>,
) : AgentEngine {
    private val remainingScripts = ArrayDeque(scripts)
    private val mutableRequests = Collections.synchronizedList(mutableListOf<AgentRequest>())

    public constructor(vararg scripts: AgentScript) : this(scripts.toList())

    public val requests: List<AgentRequest>
        get() = synchronized(mutableRequests) { mutableRequests.toList() }

    public val remainingTurnCount: Int
        get() = synchronized(remainingScripts) { remainingScripts.size }

    override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
        mutableRequests += request
        val script = synchronized(remainingScripts) {
            remainingScripts.removeFirstOrNull()
        } ?: error("No scripted turn remains for conversation ${request.conversationId}.")

        script.steps.forEach { step ->
            when (step) {
                is AgentScriptStep.Emit -> emit(step.event)
                is AgentScriptStep.Wait -> delay(step.milliseconds)
                is AgentScriptStep.Throw -> throw step.throwable
            }
        }
    }
}

/** Decorates a real or fake engine while recording requests and emitted events. */
public class RecordingAgentEngine(
    private val delegate: AgentEngine,
) : AgentEngine {
    private val mutableRequests = Collections.synchronizedList(mutableListOf<AgentRequest>())
    private val mutableEvents = Collections.synchronizedList(mutableListOf<AgentEvent>())

    public val requests: List<AgentRequest>
        get() = synchronized(mutableRequests) { mutableRequests.toList() }

    public val events: List<AgentEvent>
        get() = synchronized(mutableEvents) { mutableEvents.toList() }

    override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
        mutableRequests += request
        delegate.stream(request).collect { event ->
            mutableEvents += event
            emit(event)
        }
    }
}

/** A configurable tool handler that records every approved call. */
public class RecordingToolHandler(
    private val result: suspend (ToolCallPart) -> ToolResultPart = { call ->
        ToolResultPart(call.id, "Recorded ${call.name}")
    },
) : AgentToolHandler {
    private val mutableCalls = Collections.synchronizedList(mutableListOf<ToolCallPart>())

    public val calls: List<ToolCallPart>
        get() = synchronized(mutableCalls) { mutableCalls.toList() }

    override suspend fun execute(call: ToolCallPart): ToolResultPart {
        mutableCalls += call
        return result(call)
    }
}

/** Waits for a state predicate without test-only polling or arbitrary sleeps. */
public suspend fun StateFlow<AgentConversationState>.awaitState(
    timeoutMillis: Long = 5_000,
    predicate: (AgentConversationState) -> Boolean,
): AgentConversationState = withTimeout(timeoutMillis) { first(predicate) }

/** Returns all text produced by assistant messages, useful in concise assertions. */
public fun AgentConversationState.assistantText(): String = messages
    .filter { it.role.name == "ASSISTANT" }
    .flatMap { it.parts }
    .filterIsInstance<TextPart>()
    .joinToString(separator = "") { it.text }
