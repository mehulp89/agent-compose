# Getting started

This guide takes you from a clean checkout to a working custom agent UI.

## Requirements

- Latest stable Android Studio
- JDK 17 (Android Studio's bundled JDK is supported)
- Android SDK 37 for building this repository
- A device or emulator running API 23+

## Import the repository

1. Choose **File → Open** in Android Studio.
2. Select the folder containing `settings.gradle.kts`.
3. Trust the project when Android Studio asks.
4. Allow Gradle sync to finish.
5. If SDK 37 is missing, accept Android Studio's installation suggestion.

Run `sample` before changing library code. It deliberately has no network or account setup.

## Use the source from another project

For active development, a composite build provides the shortest edit-test loop:

```kotlin
// settings.gradle.kts in your application
includeBuild("../agent-compose")
```

Then add the normal coordinate to the consuming module. Gradle substitutes the included project:

```kotlin
dependencies {
    implementation("io.github.mehulp89.agentcompose:agent-compose:0.1.0")
}
```

You can also copy `agent-core` and `agent-compose` into an existing repository and include the two
modules in its `settings.gradle.kts`.

## Define an engine

An engine converts its provider's stream into AgentCompose events:

```kotlin
class MyEngine(
    private val api: MyBackendApi,
) : AgentEngine {
    override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started)
        api.stream(request.messages).collect { token ->
            emit(AgentEvent.TextDelta(token))
        }
        emit(AgentEvent.Completed)
    }
}
```

Translate provider exceptions into `AgentEvent.Failed` or let them escape. The controller safely
turns either form into visible retry state.

## Use a ViewModel for durable state

```kotlin
class ChatViewModel(
    engine: AgentEngine,
    toolHandler: AgentToolHandler,
) : ViewModel() {
    val controller = AgentConversationController(
        engine = engine,
        scope = viewModelScope,
        toolHandler = toolHandler,
    )
}

@Composable
fun ChatRoute(viewModel: ChatViewModel) {
    val state = rememberAgentChatState(viewModel.controller)
    AgentChat(state)
}
```

Persist domain messages outside the UI module if conversations must survive process death. Restore
them through `initialMessages` when constructing the controller.

## Next steps

- Read [Adapters](ADAPTERS.md) before connecting a production provider.
- Read [Architecture](ARCHITECTURE.md) before changing core state behavior.
- Use `AgentChatLayout` if the default visual design is not appropriate.
