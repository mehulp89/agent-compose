# Contributing to AgentCompose

Thank you for helping make Android agent experiences safer and easier to build. Contributions from
first-time open-source developers and experienced maintainers are equally welcome.

By participating, you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md) and license your
contribution under Apache-2.0.

## Find work

- `good first issue`: small, guided changes suitable for a first contribution
- `help wanted`: scoped work where maintainers would value community help
- `bug`: incorrect behavior with reproduction information
- `proposal`: API or architecture discussion before implementation

Comment on an issue before starting a large change. This prevents two contributors from doing the
same work and gives maintainers a chance to clarify compatibility requirements.

## Development setup

1. Fork `mehulp89/agent-compose` on GitHub.
2. Clone your fork.
3. Open the repository root in Android Studio.
4. Run `./gradlew test :agent-compose:lint :sample:assembleDebug`.
5. Create a focused branch such as `fix/retry-state` or `feature/copy-code`.

The sample engine is offline. No provider account or secret is required.

## Code style

- Use Kotlin and Kotlin DSL for new source and build files.
- Prefer immutable public models.
- Keep provider code outside `agent-core` and `agent-compose`.
- Add KDoc to new public APIs.
- Avoid wildcard imports.
- Keep UI labels in `AgentChatStrings` so they can be localized.
- Do not add dependencies for functionality that can remain small and maintainable.
- Never commit credentials, signing files, personal data, or model conversation logs.

Android Studio's default Kotlin formatter with this repository's `.editorconfig` is authoritative.

## Tests

Every behavior change should include the smallest useful automated test. At minimum run:

```bash
./gradlew test :agent-compose:lint :sample:assembleDebug
```

For UI changes, also run the sample in light mode, dark mode, and at a tablet width. Check TalkBack
labels and a large font scale when the change affects interaction.

## Public API changes

Public APIs affect every consumer. Open an issue first when a change:

- adds or removes an `AgentPart` or `AgentEvent` subtype;
- changes controller state behavior;
- adds a runtime dependency;
- changes `minSdk`, Java, Kotlin, Compose, or AGP requirements;
- removes or renames an existing public declaration.

Include before/after usage and a migration path in the proposal.

## Pull requests

Keep a pull request focused on one problem. Complete the template, link its issue, explain testing,
and attach screenshots or a short recording for visible changes. Maintainers may request smaller
commits or follow-up work, but perfect English is never a requirement.

Use one of these commit styles:

```text
Fix retry after a partial response
Add a citation customization slot
Document Firebase adapter security
```

## Documentation-only contributions

Documentation fixes are valuable. You can edit Markdown directly on GitHub for small corrections.
Code examples should compile conceptually, avoid secret keys, and identify pseudocode clearly.

## Reporting security issues

Do not open public issues for vulnerabilities. Follow [SECURITY.md](SECURITY.md).
