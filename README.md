# AgentCompose

[![Build](https://github.com/mehulp89/agent-compose/actions/workflows/build.yml/badge.svg)](https://github.com/mehulp89/agent-compose/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-23-brightgreen)](agent-compose/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg)](https://kotlinlang.org)

An accessible, adaptive, provider-neutral AI agent UI toolkit for Jetpack Compose.

AgentCompose gives Android developers streaming conversations, Markdown, code blocks, citations,
attachments, tool approvals, errors, retries, and Material 3 components without locking an app to
one AI provider.

> **Project status:** `0.1.0` source release. The repository and sample are ready to import. The
> Maven Central coordinate below becomes available after the maintainer completes the first signed
> release described in [Releasing](docs/RELEASING.md).

## Why AgentCompose?

Model SDKs answer prompts. Production apps still need to manage partial tokens, cancellation,
conversation state, tool permissions, errors, accessibility, and responsive UI. AgentCompose keeps
those concerns in small, testable modules:

- `agent-core` — pure Kotlin models, streaming events, conversation state, and safe tool execution.
- `agent-compose` — Material 3 UI, streaming Markdown, adaptive layout, and customization slots.
- `sample` — a completely offline demonstration that runs without an account or API key.

## Features

- Provider-neutral `AgentEngine` interface based on `Flow<AgentEvent>`
- Incremental streaming, cancellation, retry, and partial-response recovery
- Markdown headings, lists, quotes, inline code, links, and fenced code blocks
- Citation, image, and file parts
- Human approval before tools execute
- Material 3 light/dark themes and localized strings
- Phone, tablet, foldable, keyboard, and screen-reader friendly layout
- Beginner API plus slot-based components for advanced teams
- No reflection and no consumer ProGuard rules
- Android API 23+ and Java 17

## Run the sample

1. Download or clone the repository.
2. Open the repository root in the latest stable Android Studio.
3. Let Gradle sync and install Android SDK 37 if Android Studio requests it.
4. Select the `sample` run configuration.
5. Run it on an emulator or device with Android 6.0 or newer.

The sample uses a deterministic offline engine. Try these prompts:

- `Show me Kotlin code`
- `Save a note called Ideas`
- Any other question to see Markdown and a citation

## Installation

After the first Maven Central release:

```kotlin
dependencies {
    implementation("io.github.mehulp89.agentcompose:agent-compose:0.1.0")
}
```

Until then, include the source in another Gradle build:

```kotlin
// settings.gradle.kts in your app
includeBuild("../agent-compose")
```

Or publish it to your local Maven cache:

```bash
./gradlew publishToMavenLocal
```

```kotlin
// Your app's settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

## Five-minute usage

Implement an engine. It can wrap Gemini Nano, Firebase AI Logic, your backend, or a test double:

```kotlin
val engine = AgentEngine { request ->
    flow {
        emit(AgentEvent.Started)
        emit(AgentEvent.TextDelta("Hello "))
        emit(AgentEvent.TextDelta(request.messages.last().text))
        emit(AgentEvent.Completed)
    }
}
```

Create state and render it:

```kotlin
@Composable
fun AssistantScreen() {
    val state = rememberAgentChatState(engine)

    AgentChat(
        state = state,
        modifier = Modifier.fillMaxSize(),
    )
}
```

For conversations that must survive navigation, create `AgentConversationController` in a
`ViewModel` and call `rememberAgentChatState(controller)` in the UI.

## Tool approval

The model requests a tool by emitting `AgentEvent.ToolCallRequested`. AgentCompose displays the
request and does nothing until the user approves or denies it.

```kotlin
val toolHandler = AgentToolHandler { call ->
    when (call.name) {
        "save_note" -> {
            notes.save(call.arguments.getValue("title"))
            ToolResultPart(call.id, "Saved")
        }
        else -> ToolResultPart(call.id, "Unknown tool", isError = true)
    }
}

val state = rememberAgentChatState(
    engine = engine,
    toolHandler = toolHandler,
)
```

Keep authorization, validation, and sensitive business rules inside your application. Treat model
arguments as untrusted input.

## Customize the UI

Use `AgentChat` for a complete default experience. Use `AgentChatLayout` when you need your own
message cards, composer, or empty state. Individual components such as `AgentMessageCard`,
`AgentComposer`, and `AgentMarkdown` can also be used independently.

Colors and labels are explicit:

```kotlin
AgentChat(
    state = state,
    colors = AgentChatDefaults.colors(
        userContainer = MaterialTheme.colorScheme.secondaryContainer,
    ),
    strings = AgentChatStrings(
        inputPlaceholder = "Ask Acme Assistant",
    ),
)
```

## Choose your learning path

| Level | Start here |
| --- | --- |
| Beginner | Run `sample`, then change `SampleAgentEngine` responses. |
| Intermediate | Implement `AgentEngine` for an existing backend and keep the default `AgentChat`. |
| Advanced | Own the controller in a ViewModel, add persistence, implement tools, and use `AgentChatLayout` slots. |
| Library contributor | Read [Architecture](docs/ARCHITECTURE.md) and [Contributing](CONTRIBUTING.md). |

## Security

- Never ship OpenAI, Anthropic, or other private server API keys inside an APK.
- Prefer an authenticated backend, an on-device model, or a client-safe SDK such as Firebase AI
  Logic.
- Validate every tool name and argument before executing it.
- Ask for confirmation again for destructive or financially meaningful operations.
- Avoid logging prompts, attachments, and tool results unless the user has knowingly opted in.

See [SECURITY.md](SECURITY.md) for vulnerability reporting.

## Documentation

- [Getting started](docs/GETTING_STARTED.md)
- [Architecture and data flow](docs/ARCHITECTURE.md)
- [Writing provider adapters](docs/ADAPTERS.md)
- [Creating a release](docs/RELEASING.md)
- [Contributing](CONTRIBUTING.md)

## Roadmap

- Provider adapter examples maintained in separate optional modules
- Richer streaming Markdown tables and syntax highlighting
- Attachment-picker integration helpers
- Conversation persistence adapter
- Screenshot and accessibility regression tests
- Kotlin Multiplatform core evaluation

Roadmap items are proposals, not promises. Please open a feature request before implementing a
large addition.

## Contributing

Contributors of every experience level are welcome. Issues labeled `good first issue` should have a
small scope, reproduction steps, and a suggested starting point. Read [CONTRIBUTING.md](CONTRIBUTING.md)
and our [Code of Conduct](CODE_OF_CONDUCT.md) before opening a pull request.

## License

```
Copyright 2026 Mehul and AgentCompose contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
```

See [LICENSE](LICENSE) for the complete terms.
