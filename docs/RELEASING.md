# Releasing

OCDroid publishes normal, versioned GitHub releases. Each release contains `OCDroid.apk` and
`SHA256SUMS`.

## Versioning

- `0.x` releases are the current WebView shell line.
- Use a minor bump for user-visible features (for example `0.3.0`).
- Use a patch bump for fixes (`0.3.1`).
- Bump `versionCode` by one and `versionName` to the release version in
  `app/build.gradle.kts`.

## Checklist

1. Update `versionCode` and `versionName`.
2. Add `release-notes/OCDroid_<version>.md`.
3. Update `CHANGELOG.md` and `KNOWN_ISSUES.md` if needed.
4. Run the JVM tests locally if possible:
   ```bash
   ./gradlew :app:testDebugUnitTest --stacktrace
   ```
5. Open a PR. CI runs tests and a debug build.
6. Merge to `main`. The release job runs the JVM tests again, mirrors the optional web client,
   signs the APK, writes `SHA256SUMS`, and publishes the GitHub release.
7. Verify the release page has both files and that the checksum matches the APK.
8. Smoke-test on a physical device when possible.

## Repository secrets

| Secret | Required | Purpose |
| --- | --- | --- |
| `KEYSTORE_BASE64` | yes | base64-encoded release keystore |
| `KEYSTORE_ALIAS` | yes | key alias |
| `KEYSTORE_PASSWORD` | yes | keystore/key password |
| `OC_SERVER_URL` | recommended | server origin used to mirror the official web client |
| `OC_SERVER_USERNAME` | optional | Basic-auth username; defaults to `opencode` |
| `OC_SERVER_PASSWORD` | optional | Basic-auth password/token for the mirror request |

`OC_SERVER_URL` must be a secret, not a committed variable. It is part of the maintainer's
private infrastructure. The mirror script never logs it. If it is missing, the release still
builds but does not bundle the web UI.

Never print a secret in a workflow step, and never upload the keystore. The workflow removes
`app/keystore` at the end even when a step fails.

## Actions policy

- Workflow default permission is `contents: read`.
- Only the release job gets `contents: write`.
- Actions are pinned to commit SHAs.
- Checkout uses `persist-credentials: false`.
- Pull requests run tests and a debug build, never a signed release.

## If a release fails

- **Missing signing secrets** — configure all three keystore secrets.
- **Mirror 401** — set `OC_SERVER_PASSWORD` (and username if not `opencode`).
- **Mirror host unreachable** — clear `OC_SERVER_URL` to build without the bundled UI, or fix the
  server/tunnel secret. The URL is never printed.
- **`OC_SERVER_URL did not return an HTML web client`** — the URL points at an API, login page or
  wrong port. Use the server root that renders the official web client.
- **Wrong release body** — add the missing `release-notes/OCDroid_<version>.md`; the release step
  fails when the file is unmatched.
- **Install fails over an old build** — the old APK was signed with another key. Ask the user
  before uninstalling; data is lost.

## After an exposed secret

If a tunnel URL, token, pairing code or keystore password is exposed:

1. Rotate it immediately. Removing it from the current commit is not enough.
2. Remove it from current files and add a regression check if possible.
3. Decide with the owner whether public git history must be rewritten.
4. Publish a security note only after the secret is no longer useful.

See [`../SECURITY.md`](../SECURITY.md).
