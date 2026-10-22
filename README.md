# OCDroid

Android shell for the **official OpenCode web client**. It renders the exact web UI from your
OpenCode server, then adds the things a browser tab cannot give you reliably:

- **Pinned origin + persistent cache** — assets stay cached between launches even though tunnel
  hostnames change.
- **Bundled web client** — the official `index.html`, `/_assets/*`, icons and fonts are mirrored
  into the APK at build time, so startup does not wait on the tunnel.
- **Native notifications** — a foreground service listens to `/api/event` and posts Android
  notifications when an agent succeeds, fails, is interrupted, or asks for permission.
- **Encrypted credentials** — HTTP Basic auth is stored with Android Keystore-backed
  `EncryptedSharedPreferences`.
- **Automatic fallback** — if the server's web client changes, OCDroid stops using the bundled
  copy and loads the live client from the server.

There is no custom chat UI. The interface is the official OpenCode web app.

## Build (cloud only)

GitHub Actions builds a signed APK on every push to `main`:

- Workflow: [`Build OCDroid APK`](.github/workflows/build.yml)
- Latest APK: https://github.com/anik-amd/ocdroid/releases/tag/latest-build

The workflow mirrors the web client from the server, builds with JDK 17, signs with the repo's
keystore secrets, and publishes `OCDroid.apk` to the rolling release.

You can also open the repository in a Codespace; `.devcontainer/` installs JDK 17, Android SDK 34
and Node, then mirrors the web assets and builds a debug APK.

## Configuration

1. Install `OCDroid.apk` (allow install from unknown sources).
2. First launch asks for:
   - server URL (prefilled with the tunnel in `app/build.gradle.kts`)
   - username (`opencode` by default)
   - password (`OPENCODE_SERVER_PASSWORD`)
3. The official web UI loads full-screen.
4. Native settings: long-press the app icon → **OCDroid settings**. Change the server, toggle
   notifications/background monitoring, or clear the cache there.

## Architecture

| Layer | Implementation |
| --- | --- |
| UI | Android `WebView` loading the official web client |
| Static shell | Mirrored by `scripts/fetch-web-assets.mjs` into `app/src/main/assets/web` |
| API | Same-origin requests from the web client to `/api/*` |
| Notifications | `MonitorService` SSE connection to `/api/event` |
| Credentials | `EncryptedSharedPreferences` + Android Keystore |

## Upstream

The web client is part of [anomalyco/opencode](https://github.com/anomalyco/opencode). OCDroid is
an unofficial personal client, not affiliated with OpenCode or Anomaly.

Earlier commits in this repository contain a fork of
[crim50n/oc-remote](https://github.com/crim50n/oc-remote); the current app is a clean WebView
shell because the requirement is pixel-identical UI with the official web client.
