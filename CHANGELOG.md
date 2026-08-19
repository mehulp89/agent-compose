# Changelog

Notable changes are documented here. This project follows Semantic Versioning while the public API
is stable; versions below `1.0.0` may contain documented breaking changes.

## Unreleased

## 0.2.0 - 2026-08-19

### Added

- `agent-firebase-ai` adapter with streaming text, token usage, Firebase Cloud Storage parts,
  function calls, and function responses.
- `agent-mlkit-genai` adapter and model preparation API for on-device Gemini Nano.
- `agent-persistence-room` with normalized conversation, message, and part storage.
- `agent-testing` with scripted/recording engines, a tool recorder, and state awaiters.
- GitHub-style Markdown tables, ordered and nested lists, dividers, code-copy actions, and
  lightweight syntax highlighting.
- Reproducible animated demo asset and Maven Central release automation for every library artifact.

### Changed

- Aligned the build with Android Studio versions that support AGP 9.0.0: Gradle 9.1.0,
  Kotlin 2.2.10, Compose BOM 2026.01.01, and compile/target SDK 36.

## 0.1.0 - 2026-08-19

### Added

- Initial provider-neutral core API.
- Streaming conversation controller with cancellation and retry.
- Human-approved tool-call flow.
- Material 3 chat UI with customization slots.
- Streaming-friendly Markdown, citations, and attachment rendering.
- Offline sample app and contributor documentation.
