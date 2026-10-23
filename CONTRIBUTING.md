# Contributing to OCDroid

Thanks for helping make OCDroid a better Android home for OpenCode. 🧡

OCDroid is an independent community project. It is **not** an official OpenCode client, and we
do not reimplement or re-skin the chat UI. The product is a careful Android shell around the
official OpenCode web client.

## Project principles

1. **The official web UI stays official.** Do not add a native chat surface, replace the
   composer, or restyle the page. Small, clearly scoped compatibility patches are welcome.
2. **Security fixes first.** Credentials, pairing tokens, server URLs and LAN discovery are
   sensitive. Never log a token or commit a real server URL.
3. **Cloud-first testing.** The maintainer does not require a local Android SDK. GitHub Actions
   runs JVM tests and builds APKs.
4. **Honest documentation.** If something only works on a real device, say so.
5. **No affiliation claims.** Use "Made with OpenCode, for OpenCode", never "official".

## Ways to contribute

- 🐛 Report a reproducible bug with the issue template.
- 🧪 Add a JVM test for URL, pairing, event, notification or asset-path logic.
- 📱 Smoke-test a release on a physical Android device.
- 📝 Improve setup, tunnel, accessibility or troubleshooting docs.
- 🎨 Suggest a better command-palette entry point that still uses the official titlebar.
- 🔒 Report a security issue privately — see [`SECURITY.md`](SECURITY.md).

## Development setup

### Option A: GitHub Codespaces / dev container (recommended)

Open the repository in a Codespace. The container installs JDK 17, Android SDK 34 and Node.
Optionally set `OC_SERVER_URL` (and `OC_SERVER_USERNAME` / `OC_SERVER_PASSWORD`) in the
container environment before running the mirror script.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

### Option B: local machine

You need:

- JDK 17
- Android SDK 34 (compile SDK)
- Node.js 20+ only if you want to mirror the web client
- Git

```bash
git clone https://github.com/anik-amd/ocdroid.git
cd ocdroid
./gradlew :app:testDebugUnitTest
```

On Windows, use `gradle` (Gradle 8.6) or run the shell script through Git Bash/WSL.

If Gradle cannot find the SDK, create `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Do **not** commit `local.properties`.

### Optional: bundle the official web client

`app/src/main/assets/web/` is generated and gitignored. Without it, the app loads the official
client from your server. To bundle it for offline/startup speed:

```bash
OC_SERVER_URL="https://your-server.example" \
OC_SERVER_USERNAME="opencode" \
OC_SERVER_PASSWORD="your-password" \
node scripts/fetch-web-assets.mjs
```

The mirror script only crawls static shell paths (`/_assets/`, `/assets/`, `/icons/`,
`/fonts/`), follows same-origin redirects only, and refuses paths that escape the output
directory. Never commit the generated assets or the server URL.

## Tests

Fast JVM tests cover:

- server URL normalization and origin comparison
- pairing-link and QR parsing
- OpenCode SSE event parsing
- notification classification and suppression
- session route parsing
- bundled-asset path safety
- command-palette patch invariants

```bash
./gradlew :app:testDebugUnitTest --stacktrace
```

CI runs these tests before any signed release build. A pull request also gets a debug build.

Every push to `main` builds a **signed APK artifact** (`OCDroid-apk`) that can be installed for
device testing. Published releases are deliberate: bump the version and publish
(`gh workflow run "Build OCDroid APK"`) only after the build was smoke-tested on a physical
device and the maintainer confirmed it.

### Manual smoke test for device-only behavior

Before announcing a release, verify on a physical phone:

- [ ] first launch discovery, manual URL and QR pairing
- [ ] official login screen and token handoff
- [ ] official QR scanner camera permission
- [ ] command-palette button appears in the titlebar, opens the palette, and hides behind dialogs
- [ ] notification sound and vibration; active conversation is suppressed
- [ ] hidden Settings shortcut and monitoring notification **Settings** action
- [ ] background reconnect after screen-off
- [ ] clear cache and bundled-UI fallback

## Pull requests

1. Fork the repository and create a branch: `fix/...`, `feat/...`, `docs/...`.
2. Keep the change focused. Separate refactors from behavior changes.
3. Add or update JVM tests for logic that can be tested off-device.
4. Update `README.md`, `KNOWN_ISSUES.md`, or `docs/` when behavior changes.
5. Run `./gradlew :app:testDebugUnitTest`.
6. Open a PR using the template and describe device testing.

Maintainers may ask for a smaller patch if a change reintroduces a native UI or breaks the
official-page contract.

## Security invariants

Please preserve these unless the PR explains and tests a safer alternative:

- Never send credentials during LAN/mDNS discovery probes.
- Compare full origins (scheme + host + port), not just hostnames.
- Do not enable mixed content or third-party cookies.
- Do not inject a JavaScript bridge into non-server origins.
- Do not attach the server token or cookies to external download hosts.
- Never persist a password in plaintext if Android Keystore fails.
- Do not hardcode a tunnel URL, token, keystore or signing password.
- Keep the `usesCleartextTraffic` tradeoff documented.
- Keep release artifacts checksummed.

## Commit style

Use Conventional Commits when practical:

```text
feat: add command palette entry point to titlebar
fix: do not send credentials during LAN discovery
docs: explain hidden settings shortcut
chore: pin GitHub Actions
```

## AI-assisted contributions

AI-assisted changes are welcome. Disclose the tool in the PR description and follow
[`docs/AI_AGENTS.md`](docs/AI_AGENTS.md). An AI agent must not install an APK on a user's device,
rotate secrets, rewrite history, or publish a release without explicit human approval.

## License

By contributing, you agree that your contribution is licensed under the repository's MIT
license. See [`LICENSE`](LICENSE).
