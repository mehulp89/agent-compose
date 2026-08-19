package io.github.mehulp89.agentcompose.mlkit

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import io.github.mehulp89.agentcompose.core.AgentEngine
import io.github.mehulp89.agentcompose.core.AgentEvent
import io.github.mehulp89.agentcompose.core.AgentRequest
import io.github.mehulp89.agentcompose.core.AgentRole
import io.github.mehulp89.agentcompose.core.CitationPart
import io.github.mehulp89.agentcompose.core.FilePart
import io.github.mehulp89.agentcompose.core.ImagePart
import io.github.mehulp89.agentcompose.core.TextPart
import io.github.mehulp89.agentcompose.core.ToolCallPart
import io.github.mehulp89.agentcompose.core.ToolResultPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/** Builds a bounded text prompt for Gemini Nano from an AgentCompose conversation. */
public fun interface MlKitPromptBuilder {
    public fun build(request: AgentRequest): String
}

/** High-level availability states suitable for a setup screen. */
public enum class MlKitGenAiAvailability {
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE,
}

/** Download lifecycle emitted by [MlKitGenAiModelManager.prepare]. */
public sealed interface MlKitGenAiPreparation {
    public data object Checking : MlKitGenAiPreparation
    public data object DownloadStarted : MlKitGenAiPreparation
    public data class DownloadProgress(val totalBytesDownloaded: Long) : MlKitGenAiPreparation
    public data object Ready : MlKitGenAiPreparation
    public data class Failed(val message: String, val cause: Throwable? = null) : MlKitGenAiPreparation
}

/** Checks and, when possible, downloads the on-device Gemini Nano model. */
public class MlKitGenAiModelManager(
    private val model: GenerativeModel = Generation.getClient(),
) {
    public suspend fun availability(): MlKitGenAiAvailability = when (model.checkStatus()) {
        FeatureStatus.AVAILABLE -> MlKitGenAiAvailability.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> MlKitGenAiAvailability.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> MlKitGenAiAvailability.DOWNLOADING
        else -> MlKitGenAiAvailability.UNAVAILABLE
    }

    public fun prepare(): Flow<MlKitGenAiPreparation> = flow {
        emit(MlKitGenAiPreparation.Checking)
        try {
            when (availability()) {
                MlKitGenAiAvailability.AVAILABLE -> emit(MlKitGenAiPreparation.Ready)
                MlKitGenAiAvailability.DOWNLOADABLE,
                MlKitGenAiAvailability.DOWNLOADING,
                -> model.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadStarted -> emit(MlKitGenAiPreparation.DownloadStarted)
                        is DownloadStatus.DownloadProgress -> emit(
                            MlKitGenAiPreparation.DownloadProgress(status.totalBytesDownloaded),
                        )
                        DownloadStatus.DownloadCompleted -> emit(MlKitGenAiPreparation.Ready)
                        is DownloadStatus.DownloadFailed -> emit(
                            MlKitGenAiPreparation.Failed(
                                status.e.message ?: "Gemini Nano download failed.",
                                status.e,
                            ),
                        )
                    }
                }
                MlKitGenAiAvailability.UNAVAILABLE -> emit(
                    MlKitGenAiPreparation.Failed("Gemini Nano is unavailable on this device."),
                )
            }
        } catch (throwable: Throwable) {
            emit(
                MlKitGenAiPreparation.Failed(
                    throwable.message ?: "Could not prepare Gemini Nano.",
                    throwable,
                ),
            )
        }
    }
}

/**
 * An on-device, streaming AgentCompose engine backed by ML Kit's Prompt API and Gemini Nano.
 *
 * Call [MlKitGenAiModelManager.prepare] before sending a prompt. This adapter is text-only because
 * Android AI Core's Prompt API does not expose AgentCompose tool calling.
 */
public class MlKitGenAiAgentEngine(
    private val model: GenerativeModel = Generation.getClient(),
    private val promptBuilder: MlKitPromptBuilder = DefaultMlKitPromptBuilder,
) : AgentEngine {
    override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started)
        try {
            if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                emit(
                    AgentEvent.Failed(
                        "Gemini Nano is not ready. Call MlKitGenAiModelManager.prepare() first.",
                    ),
                )
                return@flow
            }

            model.generateContentStream(promptBuilder.build(request)).collect { chunk ->
                chunk.candidates.firstOrNull()?.text
                    ?.takeIf(String::isNotEmpty)
                    ?.let { emit(AgentEvent.TextDelta(it)) }
            }
            emit(AgentEvent.Completed)
        } catch (throwable: Throwable) {
            emit(
                AgentEvent.Failed(
                    throwable.message ?: "On-device generation failed.",
                    throwable,
                ),
            )
        }
    }
}

/** Default text-only prompt mapping, capped conservatively below the Prompt API input limit. */
public object DefaultMlKitPromptBuilder : MlKitPromptBuilder {
    private const val MaxCharacters: Int = 12_000

    override fun build(request: AgentRequest): String = buildString {
        appendLine("Continue this conversation. Answer the latest USER message.")
        request.messages.forEach { message ->
            appendLine()
            append(message.role.name)
            append(": ")
            message.parts.forEach { part ->
                when (part) {
                    is TextPart -> append(part.text)
                    is CitationPart -> append("[Source: ${part.title} ${part.url}]")
                    is FilePart -> append("[File: ${part.name}]")
                    is ImagePart -> append("[Image: ${part.contentDescription ?: part.uri}]")
                    is ToolCallPart -> append("[Tool request: ${part.name} ${part.arguments}]")
                    is ToolResultPart -> append("[Tool result: ${part.content}]")
                }
            }
        }
    }.takeLast(MaxCharacters)
}
