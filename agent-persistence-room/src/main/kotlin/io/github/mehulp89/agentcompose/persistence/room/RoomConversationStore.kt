package io.github.mehulp89.agentcompose.persistence.room

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.withTransaction
import io.github.mehulp89.agentcompose.core.AgentConversationState
import io.github.mehulp89.agentcompose.core.AgentMessage
import io.github.mehulp89.agentcompose.core.AgentPart
import io.github.mehulp89.agentcompose.core.AgentRole
import io.github.mehulp89.agentcompose.core.AgentUsage
import io.github.mehulp89.agentcompose.core.CitationPart
import io.github.mehulp89.agentcompose.core.FilePart
import io.github.mehulp89.agentcompose.core.ImagePart
import io.github.mehulp89.agentcompose.core.TextPart
import io.github.mehulp89.agentcompose.core.ToolCallPart
import io.github.mehulp89.agentcompose.core.ToolCallStatus
import io.github.mehulp89.agentcompose.core.ToolResultPart
import java.io.Closeable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A compact item for conversation-history screens. */
public data class StoredConversationSummary(
    val id: String,
    val updatedAtEpochMillis: Long,
    val messageCount: Int,
)

/**
 * Room-backed storage for complete AgentCompose conversations.
 *
 * The store owns its database instance; call [close] when a custom, short-lived instance is no
 * longer needed. Most apps should keep one instance in their application dependency container.
 */
public class RoomConversationStore private constructor(
    private val database: AgentConversationDatabase,
) : Closeable {
    private val dao: ConversationDao = database.conversationDao()

    public suspend fun save(state: AgentConversationState) {
        database.withTransaction {
            dao.upsertConversation(state.toEntity())
            dao.deleteMessages(state.conversationId)
            state.messages.forEachIndexed { messageIndex, message ->
                dao.insertMessage(message.toEntity(state.conversationId, messageIndex))
                dao.insertParts(message.parts.mapIndexed { partIndex, part ->
                    part.toEntity(message.id, partIndex)
                })
            }
        }
    }

    public suspend fun load(conversationId: String): AgentConversationState? =
        dao.getConversation(conversationId)?.toModel()

    public fun observe(conversationId: String): Flow<AgentConversationState?> =
        dao.observeConversation(conversationId).map { it?.toModel() }

    public fun observeConversations(): Flow<List<StoredConversationSummary>> =
        dao.observeSummaries()

    public suspend fun delete(conversationId: String) {
        dao.deleteConversation(conversationId)
    }

    public suspend fun clear() {
        dao.clearConversations()
    }

    override fun close() {
        database.close()
    }

    public companion object {
        public fun create(
            context: Context,
            databaseName: String = "agent-compose.db",
        ): RoomConversationStore = RoomConversationStore(
            Room.databaseBuilder(
                context.applicationContext,
                AgentConversationDatabase::class.java,
                databaseName,
            ).build(),
        )
    }
}

@Entity(tableName = "agent_conversations")
internal data class ConversationEntity(
    @PrimaryKey val id: String,
    val updatedAtEpochMillis: Long,
    val draft: String,
    val isGenerating: Boolean,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val totalTokens: Long?,
    val errorMessage: String?,
)

@Entity(
    tableName = "agent_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
internal data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val position: Int,
    val role: String,
    val createdAtEpochMillis: Long,
    val isStreaming: Boolean,
    val errorMessage: String?,
)

@Entity(
    tableName = "agent_parts",
    primaryKeys = ["messageId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId")],
)
internal data class PartEntity(
    val messageId: String,
    val position: Int,
    val kind: String,
    val text: String? = null,
    val uri: String? = null,
    val name: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val contentDescription: String? = null,
    val title: String? = null,
    val url: String? = null,
    val snippet: String? = null,
    val toolCallId: String? = null,
    val argumentsJson: String? = null,
    val explanation: String? = null,
    val toolStatus: String? = null,
    val isError: Boolean? = null,
)

internal data class MessageWithParts(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val parts: List<PartEntity>,
)

internal data class ConversationWithMessages(
    @Embedded val conversation: ConversationEntity,
    @Relation(
        entity = MessageEntity::class,
        parentColumn = "id",
        entityColumn = "conversationId",
    )
    val messages: List<MessageWithParts>,
)

@Dao
internal interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<PartEntity>)

    @Query("DELETE FROM agent_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    @Transaction
    @Query("SELECT * FROM agent_conversations WHERE id = :conversationId")
    suspend fun getConversation(conversationId: String): ConversationWithMessages?

    @Transaction
    @Query("SELECT * FROM agent_conversations WHERE id = :conversationId")
    fun observeConversation(conversationId: String): Flow<ConversationWithMessages?>

    @Query(
        """
        SELECT c.id, c.updatedAtEpochMillis, COUNT(m.id) AS messageCount
        FROM agent_conversations c
        LEFT JOIN agent_messages m ON c.id = m.conversationId
        GROUP BY c.id
        ORDER BY c.updatedAtEpochMillis DESC
        """,
    )
    fun observeSummaries(): Flow<List<StoredConversationSummary>>

    @Query("DELETE FROM agent_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM agent_conversations")
    suspend fun clearConversations()
}

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, PartEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class AgentConversationDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
}

private fun AgentConversationState.toEntity(): ConversationEntity = ConversationEntity(
    id = conversationId,
    updatedAtEpochMillis = messages.maxOfOrNull(AgentMessage::createdAtEpochMillis)
        ?: System.currentTimeMillis(),
    draft = draft,
    isGenerating = isGenerating,
    inputTokens = lastUsage?.inputTokens,
    outputTokens = lastUsage?.outputTokens,
    totalTokens = lastUsage?.totalTokens,
    errorMessage = errorMessage,
)

private fun AgentMessage.toEntity(conversationId: String, position: Int): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    position = position,
    role = role.name,
    createdAtEpochMillis = createdAtEpochMillis,
    isStreaming = isStreaming,
    errorMessage = errorMessage,
)

private fun AgentPart.toEntity(messageId: String, position: Int): PartEntity = when (this) {
    is TextPart -> PartEntity(messageId, position, kind = "text", text = text)
    is ImagePart -> PartEntity(
        messageId,
        position,
        kind = "image",
        uri = uri,
        contentDescription = contentDescription,
        mimeType = mimeType,
    )
    is FilePart -> PartEntity(
        messageId,
        position,
        kind = "file",
        uri = uri,
        name = name,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
    )
    is CitationPart -> PartEntity(
        messageId,
        position,
        kind = "citation",
        title = title,
        url = url,
        snippet = snippet,
    )
    is ToolCallPart -> PartEntity(
        messageId,
        position,
        kind = "tool_call",
        toolCallId = id,
        name = name,
        argumentsJson = JsonObject(arguments.mapValues { JsonPrimitive(it.value) }).toString(),
        explanation = explanation,
        toolStatus = status.name,
    )
    is ToolResultPart -> PartEntity(
        messageId,
        position,
        kind = "tool_result",
        toolCallId = toolCallId,
        text = content,
        isError = isError,
    )
}

private fun ConversationWithMessages.toModel(): AgentConversationState = AgentConversationState(
    conversationId = conversation.id,
    messages = messages.sortedBy { it.message.position }.map(MessageWithParts::toModel),
    draft = conversation.draft,
    isGenerating = conversation.isGenerating,
    lastUsage = if (
        conversation.inputTokens != null ||
        conversation.outputTokens != null ||
        conversation.totalTokens != null
    ) {
        AgentUsage(
            inputTokens = conversation.inputTokens,
            outputTokens = conversation.outputTokens,
            totalTokens = conversation.totalTokens,
        )
    } else {
        null
    },
    errorMessage = conversation.errorMessage,
)

private fun MessageWithParts.toModel(): AgentMessage = AgentMessage(
    id = message.id,
    role = runCatching { AgentRole.valueOf(message.role) }.getOrDefault(AgentRole.ASSISTANT),
    parts = parts.sortedBy(PartEntity::position).mapNotNull(PartEntity::toModel),
    createdAtEpochMillis = message.createdAtEpochMillis,
    isStreaming = message.isStreaming,
    errorMessage = message.errorMessage,
)

private fun PartEntity.toModel(): AgentPart? = when (kind) {
    "text" -> TextPart(text.orEmpty())
    "image" -> uri?.let { ImagePart(it, contentDescription, mimeType) }
    "file" -> if (uri != null && name != null) FilePart(uri, name, mimeType, sizeBytes) else null
    "citation" -> if (title != null && url != null) CitationPart(title, url, snippet) else null
    "tool_call" -> if (toolCallId != null && name != null) {
        ToolCallPart(
            id = toolCallId,
            name = name,
            arguments = decodeArguments(argumentsJson),
            explanation = explanation,
            status = runCatching { ToolCallStatus.valueOf(toolStatus.orEmpty()) }
                .getOrDefault(ToolCallStatus.AWAITING_APPROVAL),
        )
    } else {
        null
    }
    "tool_result" -> toolCallId?.let { ToolResultPart(it, text.orEmpty(), isError == true) }
    else -> null
}

private fun decodeArguments(encoded: String?): Map<String, String> = runCatching {
    Json.parseToJsonElement(encoded.orEmpty())
        .let { it as JsonObject }
        .mapValues { (_, value) -> value.jsonPrimitive.contentOrNull ?: value.toString() }
}.getOrDefault(emptyMap())
