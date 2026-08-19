package io.github.mehulp89.agentcompose.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mehulp89.agentcompose.core.AgentConversationState
import io.github.mehulp89.agentcompose.core.AgentMessage
import io.github.mehulp89.agentcompose.core.AgentRole
import io.github.mehulp89.agentcompose.core.CitationPart
import io.github.mehulp89.agentcompose.core.FilePart
import io.github.mehulp89.agentcompose.core.ImagePart
import io.github.mehulp89.agentcompose.core.TextPart
import io.github.mehulp89.agentcompose.core.ToolCallPart
import io.github.mehulp89.agentcompose.core.ToolCallStatus
import io.github.mehulp89.agentcompose.core.ToolResultPart

/** Ready-to-use Material 3 agent conversation UI. */
@Composable
public fun AgentChat(
    state: AgentChatState,
    modifier: Modifier = Modifier,
    colors: AgentChatColors = AgentChatDefaults.colors(),
    strings: AgentChatStrings = AgentChatStrings(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    onOpenUrl: (String) -> Unit = {},
) {
    AgentChatLayout(
        state = state,
        modifier = modifier,
        colors = colors,
        contentPadding = contentPadding,
        emptyContent = {
            DefaultEmptyContent(strings)
        },
        messageContent = { message ->
            AgentMessageCard(
                message = message,
                colors = colors,
                strings = strings,
                onToolDecision = state::decideTool,
                onOpenUrl = onOpenUrl,
                onRetry = state::retryLast,
            )
        },
        composer = { conversation ->
            AgentComposer(
                value = conversation.draft,
                isGenerating = conversation.isGenerating,
                strings = strings,
                onValueChange = state::updateDraft,
                onSend = state::sendDraft,
                onStop = state::cancel,
            )
        },
    )
}

/**
 * Slot-based layout for advanced applications that need custom messages, empty state, or composer.
 */
@Composable
public fun AgentChatLayout(
    state: AgentChatState,
    modifier: Modifier = Modifier,
    colors: AgentChatColors = AgentChatDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    emptyContent: @Composable () -> Unit,
    messageContent: @Composable (AgentMessage) -> Unit,
    composer: @Composable (AgentConversationState) -> Unit,
) {
    val conversation by state.conversation.collectAsState()
    val listState = rememberLazyListState()
    val lastMessage = conversation.messages.lastOrNull()

    LaunchedEffect(conversation.messages.size, lastMessage?.text?.length, lastMessage?.parts?.size) {
        if (conversation.messages.isNotEmpty()) {
            listState.animateScrollToItem(conversation.messages.lastIndex)
        }
    }

    Surface(
        modifier = modifier,
        color = colors.background,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val horizontalPadding = if (maxWidth >= 840.dp) 32.dp else 0.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .widthIn(max = 960.dp),
                ) {
                    if (conversation.messages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            emptyContent()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = contentPadding,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(
                                items = conversation.messages,
                                key = AgentMessage::id,
                            ) { message ->
                                messageContent(message)
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp)
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    composer(conversation)
                }
            }
        }
    }
}

/** Default adaptive message card. Each [AgentMessage] part is rendered in order. */
@Composable
public fun AgentMessageCard(
    message: AgentMessage,
    modifier: Modifier = Modifier,
    colors: AgentChatColors = AgentChatDefaults.colors(),
    strings: AgentChatStrings = AgentChatStrings(),
    onToolDecision: (toolCallId: String, approved: Boolean) -> Unit = { _, _ -> },
    onOpenUrl: (String) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val isUser = message.role == AgentRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val containerColor = when (message.role) {
        AgentRole.USER -> colors.userContainer
        AgentRole.TOOL -> colors.toolContainer
        else -> colors.assistantContainer
    }
    val contentColor = when (message.role) {
        AgentRole.USER -> colors.userContent
        AgentRole.TOOL -> colors.toolContent
        else -> colors.assistantContent
    }
    val roleLabel = when (message.role) {
        AgentRole.SYSTEM -> "System message"
        AgentRole.USER -> "Your message"
        AgentRole.ASSISTANT -> "Agent response"
        AgentRole.TOOL -> "Tool result"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = roleLabel },
        contentAlignment = alignment,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 720.dp),
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp,
            ),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                message.parts.forEach { part ->
                    when (part) {
                        is TextPart -> AgentMarkdown(
                            markdown = part.text,
                            color = contentColor,
                            codeContainerColor = colors.codeContainer,
                        )
                        is CitationPart -> CitationCard(
                            citation = part,
                            label = strings.source,
                            onOpenUrl = onOpenUrl,
                        )
                        is ToolCallPart -> ToolCallCard(
                            call = part,
                            strings = strings,
                            onDecision = onToolDecision,
                        )
                        is ToolResultPart -> ToolResultContent(part)
                        is FilePart -> AttachmentChip(
                            title = part.name,
                            subtitle = part.mimeType ?: strings.attachedFile,
                        )
                        is ImagePart -> AttachmentChip(
                            title = part.contentDescription ?: strings.attachedImage,
                            subtitle = part.mimeType ?: part.uri,
                        )
                    }
                }
                if (message.isStreaming) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(7.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            content = {},
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "Generating…",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                message.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onRetry) {
                        Text(strings.retry)
                    }
                }
            }
        }
    }
}

/** Default input field. Apps can replace it through [AgentChatLayout]. */
@Composable
public fun AgentComposer(
    value: String,
    isGenerating: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    strings: AgentChatStrings = AgentChatStrings(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = !isGenerating,
            placeholder = { Text(strings.inputPlaceholder) },
            minLines = 1,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (value.isNotBlank() && !isGenerating) onSend()
                },
            ),
            shape = RoundedCornerShape(20.dp),
        )
        if (isGenerating) {
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.height(56.dp),
            ) {
                Text(strings.stop)
            }
        } else {
            Button(
                onClick = onSend,
                modifier = Modifier.height(56.dp),
                enabled = value.isNotBlank(),
            ) {
                Text(strings.send)
            }
        }
    }
}

@Composable
private fun DefaultEmptyContent(strings: AgentChatStrings) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = strings.emptyTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = strings.emptyDescription,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ToolCallCard(
    call: ToolCallPart,
    strings: AgentChatStrings,
    onDecision: (String, Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = strings.toolRequest,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = call.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            call.explanation?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (call.arguments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    call.arguments.forEach { (key, value) ->
                        Text(
                            text = "$key: $value",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (call.status == ToolCallStatus.AWAITING_APPROVAL) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDecision(call.id, true) }) {
                        Text(strings.approve)
                    }
                    OutlinedButton(onClick = { onDecision(call.id, false) }) {
                        Text(strings.deny)
                    }
                }
            } else {
                Text(
                    text = call.status.readableName(),
                    color = statusColor(call.status),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun CitationCard(
    citation: CitationPart,
    label: String,
    onOpenUrl: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.clickable { onOpenUrl(citation.url) },
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = citation.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            citation.snippet?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ToolResultContent(result: ToolResultPart) {
    Text(
        text = result.content,
        color = if (result.isError) MaterialTheme.colorScheme.error else Color.Unspecified,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun AttachmentChip(title: String, subtitle: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun ToolCallStatus.readableName(): String = name
    .lowercase()
    .replace('_', ' ')
    .replaceFirstChar { it.uppercaseChar() }

@Composable
private fun statusColor(status: ToolCallStatus): Color = when (status) {
    ToolCallStatus.FAILED,
    ToolCallStatus.DENIED,
    -> MaterialTheme.colorScheme.error
    ToolCallStatus.SUCCEEDED -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
