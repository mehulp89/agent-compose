package io.github.mehulp89.agentcompose.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import io.github.mehulp89.agentcompose.core.AgentEngine
import io.github.mehulp89.agentcompose.core.AgentEvent
import io.github.mehulp89.agentcompose.core.AgentMessage
import io.github.mehulp89.agentcompose.core.AgentRequest
import io.github.mehulp89.agentcompose.core.AgentRole
import io.github.mehulp89.agentcompose.core.AgentToolHandler
import io.github.mehulp89.agentcompose.core.CitationPart
import io.github.mehulp89.agentcompose.core.ToolCallPart
import io.github.mehulp89.agentcompose.core.ToolResultPart
import io.github.mehulp89.agentcompose.ui.AgentChat
import io.github.mehulp89.agentcompose.ui.rememberAgentChatState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SampleTheme {
                SampleChat()
            }
        }
    }
}

@Composable
private fun SampleChat() {
    val engine = remember { SampleAgentEngine() }
    val toolHandler = remember {
        AgentToolHandler { call ->
            delay(500)
            ToolResultPart(
                toolCallId = call.id,
                content = "Created local note '${call.arguments["title"] ?: "Untitled"}'.",
            )
        }
    }
    val welcome = remember {
        listOf(
            AgentMessage.text(
                role = AgentRole.ASSISTANT,
                text = """
                    # AgentCompose 0.2.0

                    The **Production Pack** is ready. Try **Show me Kotlin code** or
                    **Save a note called Ideas**.
                """.trimIndent(),
            ),
        )
    }
    val chatState = rememberAgentChatState(
        engine = engine,
        toolHandler = toolHandler,
        initialMessages = welcome,
    )
    val uriHandler = LocalUriHandler.current

    AgentChat(
        state = chatState,
        onOpenUrl = uriHandler::openUri,
    )
}

/**
 * A deterministic offline engine that demonstrates streaming, Markdown, citations, and tools.
 * Replace this class with a production adapter implementing [AgentEngine].
 */
private class SampleAgentEngine : AgentEngine {
    override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started)
        val latest = request.messages.lastOrNull()

        if (latest?.role == AgentRole.TOOL) {
            val result = latest.parts.filterIsInstance<ToolResultPart>().firstOrNull()
            val response = if (result?.isError == true) {
                "Okay — I did not run that action. You remain in control."
            } else {
                "Done. The approved tool completed successfully: `${result?.content}`"
            }
            streamText(response)
            emit(AgentEvent.Completed)
            return@flow
        }

        val prompt = latest?.text.orEmpty()
        when {
            prompt.contains("save", ignoreCase = true) -> {
                emit(
                    AgentEvent.ToolCallRequested(
                        ToolCallPart(
                            id = "save-${System.currentTimeMillis()}",
                            name = "save_note",
                            arguments = mapOf("title" to extractTitle(prompt)),
                            explanation = "Create a note on this device. Approval is required.",
                        ),
                    ),
                )
            }
            prompt.contains("code", ignoreCase = true) -> {
                streamText(
                    """
                    ## A small Kotlin example

                    ```kotlin
                    val engine = FirebaseAiAgentEngine(model)
                    val state = rememberAgentChatState(engine)

                    AgentChat(state = state)
                    ```

                    Tap **Copy** in the code header. Syntax color remains stable while tokens stream.
                    """.trimIndent(),
                )
            }
            else -> {
                streamText(
                    """
                    AgentCompose is **provider-neutral**. Your adapter only needs to return a
                    `Flow<AgentEvent>`.

                    | Module | Purpose |
                    | --- | --- |
                    | Firebase AI | Cloud inference + functions |
                    | ML Kit GenAI | Private on-device inference |
                    | Room | Durable conversations |
                    | Testing | Deterministic agent scripts |

                    1. Pick only the modules your app needs.
                    2. Keep tool execution behind user approval.
                """.trimIndent(),
                )
                emit(
                    AgentEvent.CitationAdded(
                        CitationPart(
                            title = "AgentCompose on GitHub",
                            url = "https://github.com/mehulp89/agent-compose",
                            snippet = "Documentation, examples, and contribution guide.",
                        ),
                    ),
                )
            }
        }
        emit(AgentEvent.Completed)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.streamText(text: String) {
        for (chunk in text.chunked(10)) {
            emit(AgentEvent.TextDelta(chunk))
            delay(35)
        }
    }

    private fun extractTitle(prompt: String): String = prompt
        .substringAfter("called", missingDelimiterValue = "Ideas")
        .trim()
        .ifBlank { "Ideas" }
}

@Composable
private fun SampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
