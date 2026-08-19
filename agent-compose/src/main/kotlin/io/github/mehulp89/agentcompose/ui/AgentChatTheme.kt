package io.github.mehulp89.agentcompose.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** All user-facing labels, making localization straightforward. */
@Immutable
public data class AgentChatStrings(
    val inputPlaceholder: String = "Message the agent",
    val send: String = "Send",
    val stop: String = "Stop",
    val retry: String = "Retry",
    val approve: String = "Approve",
    val deny: String = "Deny",
    val emptyTitle: String = "How can I help?",
    val emptyDescription: String = "Ask a question or describe a task.",
    val toolRequest: String = "Tool request",
    val source: String = "Source",
    val attachedImage: String = "Image",
    val attachedFile: String = "File",
)

/** Color tokens for the default AgentCompose components. */
@Immutable
public data class AgentChatColors(
    val background: Color,
    val userContainer: Color,
    val userContent: Color,
    val assistantContainer: Color,
    val assistantContent: Color,
    val toolContainer: Color,
    val toolContent: Color,
    val error: Color,
    val codeContainer: Color,
)

public object AgentChatDefaults {
    @Composable
    public fun colors(
        background: Color = MaterialTheme.colorScheme.background,
        userContainer: Color = MaterialTheme.colorScheme.primaryContainer,
        userContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
        assistantContainer: Color = MaterialTheme.colorScheme.surfaceContainer,
        assistantContent: Color = MaterialTheme.colorScheme.onSurface,
        toolContainer: Color = MaterialTheme.colorScheme.tertiaryContainer,
        toolContent: Color = MaterialTheme.colorScheme.onTertiaryContainer,
        error: Color = MaterialTheme.colorScheme.error,
        codeContainer: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ): AgentChatColors = AgentChatColors(
        background = background,
        userContainer = userContainer,
        userContent = userContent,
        assistantContainer = assistantContainer,
        assistantContent = assistantContent,
        toolContainer = toolContainer,
        toolContent = toolContent,
        error = error,
        codeContainer = codeContainer,
    )
}
