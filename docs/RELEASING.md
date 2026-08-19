# Releasing

Only maintainers with access to the Maven Central namespace and repository secrets can publish.

## One-time Maven Central setup

1. Sign in to [Maven Central Portal](https://central.sonatype.com/).
2. Verify ownership of the `io.github.mehulp89` namespace.
3. Generate a Central Portal user token.
4. Create a GPG signing key and publish its public key.
5. Add these GitHub Actions secrets:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Portal token username |
| `MAVEN_CENTRAL_PASSWORD` | Portal token password |
| `SIGNING_IN_MEMORY_KEY` | ASCII-armored private GPG key |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | Private-key password |

## Prepare a release

1. Ensure `main` is green and the working tree is clean.
2. Update `VERSION_NAME` in `gradle.properties`.
3. Move entries from `Unreleased` in `CHANGELOG.md` into the new version section.
4. Run:

   ```bash
   ./gradlew clean test lint :sample:assembleDebug publishToMavenLocal
   ```

5. Inspect the POM, AAR, sources JAR, and signatures under each module's `build/publications` and
   `build/outputs` directories.
6. Commit with `Release 0.x.y` and push.
7. Create an annotated tag: `git tag -a v0.x.y -m "v0.x.y"`.
8. Push the tag and create a GitHub Release using the matching changelog section.

Publishing starts when the GitHub Release is marked published. The release workflow signs both
JVM and Android library artifacts and submits them through the Central Portal. For `0.2.0`, confirm
that Central contains all six coordinates: `agent-core`, `agent-compose`, `agent-firebase-ai`,
`agent-mlkit-genai`, `agent-persistence-room`, and `agent-testing`.

Central publication requires an account, verified namespace, user token, and GPG key. The workflow
cannot bypass that one-time maintainer setup. Never place those values in committed Gradle files.

## Recovery

Maven releases are immutable. If a release is faulty, document the problem, fix it on `main`, and
publish a new patch version. Never retag or overwrite a published version.
