# Writing provider adapters

AgentCompose intentionally does not choose a model vendor. A provider adapter has one job: map the
current conversation into a provider request and map the provider stream back to `AgentEvent`.

## Minimum adapter

```kotlin
class BackendAgentEngine(
    private val backend: Backend,
) : AgentEngine {
    override fun stream(request: AgentRequest): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started)
        backend.stream(
            conversationId = request.conversationId,
            messages = request.messages,
        ).collect { event ->
            when (event) {
                is BackendEvent.Token -> emit(AgentEvent.TextDelta(event.value))
                is BackendEvent.Source -> emit(
                    AgentEvent.CitationAdded(
                        CitationPart(event.title, event.url, event.snippet),
                    ),
                )
                is BackendEvent.Tool -> emit(
                    AgentEvent.ToolCallRequested(
                        ToolCallPart(event.id, event.name, event.arguments),
                    ),
                )
            }
        }
        emit(AgentEvent.Completed)
    }
}
```

## Map all roles

- `SYSTEM`: developer instructions or application policy
- `USER`: prompts and attachments
- `ASSISTANT`: text, citations, and prior tool calls
- `TOOL`: the result returned after an approval decision

The second request after a tool executes contains the original assistant tool call and the new
`TOOL` message. Preserve both when the provider requires tool-call continuity.

## Structured arguments

`ToolCallPart.arguments` is a `Map<String, String>` to keep `agent-core` JSON-independent. Encode a
number, boolean, array, or object as a JSON string and validate it in the host application's
`AgentToolHandler`.

## Cloud security

Do not put secret provider keys into adapter source, `BuildConfig`, resources, or `local.properties`
for a production APK. APK contents can be extracted. Use one of these patterns:

1. A model running on the device.
2. An authenticated application backend that holds the provider credential.
3. A platform SDK explicitly designed to make client calls without exposing a reusable secret.

## Cancellation

The controller cancels collection when the user presses Stop. Use cancellable suspending APIs in
the adapter. If wrapping callbacks, call the provider's cancel method from `awaitClose`.

## Testing

Use a fake `flow` in unit tests. Verify:

- multiple deltas join in order;
- provider failures preserve partial text;
- cancellation closes the upstream operation;
- tool arguments map correctly;
- tool results are included in the follow-up request;
- prompts and credentials are not logged.
