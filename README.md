# OCDroid

Android shell for the **official OpenCode web client**. It renders the exact web UI from your
OpenCode server, then adds the things a browser tab cannot give you reliably:

- **Server discovery** — finds OpenCode on the local network with mDNS or a short port-4096 scan,
  and scans the QR code printed by `opencode pair` to connect through OpenTunnel.
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
- Latest release: https://github.com/anik-amd/ocdroid/releases/latest
- Download: `OCDroid.apk` under the newest **OCDroid v…** release

The workflow mirrors the web client from the server, builds with JDK 17, signs with the repo's
keystore secrets, and publishes `OCDroid.apk` to a normal versioned release (for example
`OCDroid v0.1.0`). The version comes from `versionName` in `app/build.gradle.kts`, and the
release body comes from `release-notes/OCDroid_<version>.md`.

You can also open the repository in a Codespace; `.devcontainer/` installs JDK 17, Android SDK 34
and Node, then mirrors the web assets and builds a debug APK.

## Configuration

1. Install `OCDroid.apk` (allow install from unknown sources).
2. On the computer running OpenCode, make the server reachable:
   - Same Wi-Fi: `opencode web --mdns`, or
     `opencode web --hostname 0.0.0.0 --port 4096`
   - From anywhere: `opencode pair --remote`
   - Set `OPENCODE_SERVER_PASSWORD` before starting either command.
3. First launch searches your Wi-Fi automatically. Tap a discovered server, or tap
   **Scan pairing QR** and scan the code printed by `opencode pair`.
4. If nothing is found, tap **How to find my server**; the same instructions are built into the app.
5. The official web UI loads full-screen.
6. Native settings: long-press the app icon → **OCDroid settings**. Change the server, toggle
   notifications/background monitoring, or clear the cache there.

## Tests

Small, fast JVM tests cover URL normalization, pairing-link/QR parsing and OpenCode event
parsing:

```bash
./gradlew :app:testDebugUnitTest
```

CI runs these tests before building the APK, so a failing rule never produces a release.
Inside a Codespace, the same command runs in the dev container.

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
