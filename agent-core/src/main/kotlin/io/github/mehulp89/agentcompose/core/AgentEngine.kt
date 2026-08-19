package io.github.mehulp89.agentcompose.core

import kotlinx.coroutines.flow.Flow

/**
 * Provider-neutral boundary for an AI model or agent backend.
 *
 * Implementations may use Gemini Nano, Firebase AI Logic, an HTTP backend, a local test double, or
 * any other source. Never embed a private cloud API key in an Android application.
 */
public fun interface AgentEngine {
    public fun stream(request: AgentRequest): Flow<AgentEvent>
}

/** Executes approved tools inside the host application. */
public fun interface AgentToolHandler {
    public suspend fun execute(call: ToolCallPart): ToolResultPart
}
