# Production Pack

AgentCompose 0.2.0 adds four optional artifacts around the small provider-neutral core. Apps can
combine them or replace any one of them with their own implementation.

| Artifact | Minimum Android | Purpose |
| --- | ---: | --- |
| `agent-firebase-ai` | 23 | Firebase AI Logic streaming, usage, and function-call mapping |
| `agent-mlkit-genai` | 26 | On-device Gemini Nano through the ML Kit Prompt API |
| `agent-persistence-room` | 23 | Local conversation storage and observation |
| `agent-testing` | JVM | Deterministic engine and tool test doubles |

## Firebase AI Logic

Add the adapter and follow Firebase's Android setup to add your app to a Firebase project and place
`google-services.json` in the application module. That file identifies the Firebase project; do not
put unrelated server API keys in the APK.

```kotlin
dependencies {
    implementation("io.github.mehulp89.agentcompose:agent-firebase-ai:0.2.0")
}
```

Construct the Firebase model in the host application. This leaves backend selection, model name,
safety settings, generation config, and function declarations under app control:

```kotlin
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeBackend
import com.google.firebase.ai.ai
import io.github.mehulp89.agentcompose.firebaseai.FirebaseAiAgentEngine

val model = Firebase.ai(
    backend = GenerativeBackend.googleAI(),
).generativeModel(modelName = "gemini-2.5-flash")

val engine = FirebaseAiAgentEngine(model)
```

The default mapper supports:

- text and prior conversation roles;
- Firebase Cloud Storage files with a `gs://` URI and MIME type;
- token usage through `AgentEvent.UsageUpdated`;
- Firebase function calls through approval-gated `ToolCallPart` values;
- matching function results on the follow-up request.

AgentCompose's generic `ImagePart` holds a URI, while Firebase image input requires loaded bytes or
a bitmap. Resolve local image URIs in the host app and supply a custom `FirebaseAiContentMapper` if
the model needs those pixels. The default mapper sends a safe text description instead.

## On-device ML Kit GenAI

The Prompt API requires API 26 and a supported device with Android AI Core. Availability is a
runtime property, so keep a cloud or non-AI fallback. The ML Kit Prompt API dependency is currently
beta and inherits Google's ML Kit terms.

```kotlin
dependencies {
    implementation("io.github.mehulp89.agentcompose:agent-mlkit-genai:0.2.0")
}
```

Prepare the model before showing the composer:

```kotlin
val manager = MlKitGenAiModelManager()

lifecycleScope.launch {
    manager.prepare().collect { state ->
        when (state) {
            MlKitGenAiPreparation.Ready -> showChat()
            is MlKitGenAiPreparation.DownloadProgress -> showBytes(state.totalBytesDownloaded)
            is MlKitGenAiPreparation.Failed -> showFallback(state.message)
            else -> showPreparing()
        }
    }
}
```

Then use the engine like any other provider:

```kotlin
val engine = MlKitGenAiAgentEngine()
```

The default prompt builder retains the newest 12,000 characters to stay conservatively below the
Prompt API input limit. It is text-only and does not claim tool-calling support. Pass a custom
`MlKitPromptBuilder` for a different conversation template or truncation strategy.

## Room persistence

```kotlin
dependencies {
    implementation("io.github.mehulp89.agentcompose:agent-persistence-room:0.2.0")
}
```

Keep one store in the app's dependency container:

```kotlin
val conversationStore = RoomConversationStore.create(applicationContext)
```

Save snapshots at product-appropriate boundaries, such as after a completed response or in a
debounced collector:

```kotlin
controller.state
    .filterNot { it.isGenerating }
    .onEach(conversationStore::save)
    .launchIn(viewModelScope)
```

Restore a state before creating a controller, or observe summaries for a history screen:

```kotlin
val previous = conversationStore.load(conversationId)
val summaries: Flow<List<StoredConversationSummary>> =
    conversationStore.observeConversations()
```

The schema normalizes conversations, messages, and message parts. Deleting a conversation cascades
to its messages and parts. Versioned schema JSON is exported to `agent-persistence-room/schemas`
during builds so future migrations can be reviewed.

## Testing utilities

```kotlin
testImplementation("io.github.mehulp89.agentcompose:agent-testing:0.2.0")
```

Script multiple turns, record requests, and avoid timing sleeps:

```kotlin
@Test
fun streamsResponse() = runTest {
    val engine = ScriptedAgentEngine(
        AgentScript.text("Hello world", chunkSize = 3),
    )
    val controller = AgentConversationController(engine, this)

    controller.send("Hi")
    val complete = controller.state.awaitState { !it.isGenerating && it.messages.size == 2 }

    assertEquals("Hello world", complete.assistantText())
    assertEquals("Hi", engine.requests.single().messages.single().text)
}
```

`RecordingAgentEngine` decorates any engine, while `RecordingToolHandler` records approval-gated
tool execution and can return a custom result.

## Advanced Markdown

`AgentMarkdown` remains dependency-light and streaming-safe. Version 0.2.0 supports GitHub-style
tables, ordered and nested lists, dividers, fenced-code copy actions, and lightweight syntax color
for Kotlin, Java, JSON, XML, and YAML. Supply `onCopyCode` to connect custom analytics or clipboard
behavior; without it, AgentCompose copies through Compose's local clipboard manager.
