# Architecture

OCDroid is intentionally small: an Android shell around the official OpenCode web client, plus
the platform features a browser tab cannot provide reliably.

```text
┌──────────────────────────────────────────────────────────────┐
│ Android                                                     │
│                                                              │
│  MainActivity ── WebView ── official OpenCode web client     │
│       │              │                                       │
│       │              ├── /api/*  ───────────────► server     │
│       │              ├── /_assets/* ◄── bundled assets       │
│       │              └── injected bridge / palette button    │
│       │                                                      │
│       ├── SettingsStore ── EncryptedSharedPreferences        │
│       ├── Pairing ── QR / pairing-link parsing               │
│       └── MonitorService ── SSE ── Android notifications     │
└──────────────────────────────────────────────────────────────┘
```

## Components

| Component | Responsibility |
| --- | --- |
| `MainActivity` | WebView lifecycle, origin policy, camera/file chooser, downloads, bridge |
| `WebViewPatches` | Injected scripts: Enter-to-newline, command-palette button, the native bridge |
| `WebBundle` | Serves the packaged `index.html` and static assets; live-version check |
| `SettingsStore` | Encrypted settings, Keystore fallback policy, server config |
| `Pairing` | Parses pairing links/QR payloads, reaches out/back, chooses a server URL |
| `MonitorService` | Foreground SSE connection and notification decisions |
| `NotificationRules` | Pure classification of OpenCode events (JVM-tested) |
| `EventParser` | Lightweight SSE payload parsing without Android dependencies |
| `SessionRoutes` | Extracts the session id from the official web route |
| `ActiveSessionStore` | Shared in-process foreground/session state |
| `ServerOrigin` | Scheme + host + port normalization for origin checks |
| `AssetPaths` | Path-traversal-safe mapping into `assets/web` |

## Startup and connection

1. `MainActivity` reads `SettingsStore`.
2. If configured, it loads the server root in the WebView and begins pairing. Otherwise it shows
   a **native welcome screen** — plain onboarding that presents the two connection modes (LAN
   `opencode pair` vs remote `opencode pair --remote` and when each applies), the
   copy-paste instruction the user can hand to their OpenCode AI agent, and connect controls
   (native ZXing QR scanner, pairing-link/address + password form, Settings, help). No server
   WebView is loaded while the welcome screen is visible, so a broken mirror or dead tunnel can
   never render as a blank screen.
3. The official web client handles login (paste a pairing link, scan its QR with the camera, or
   enter address/password) and stores its own server entry in `localStorage`.
4. `WebViewPatches.nativeBridge` reads that entry and reports the URL/token to
   `OCDroidNative.onServerConnected`. On non-server pages the reported server must answer a
   reachability probe first; OCDroid then stores the token as the Basic-auth password and
   opens the real server origin with `?auth_token=`, which the official client consumes itself.
5. If a main-frame load of the saved server fails (server off, tunnel rotated), the welcome
   screen returns with the unreachable address, a **Retry** button and all pairing controls.
5. The monitor connects to `/api/event` (v2), `/global/event` or `/event` (v1) and translates
   events into notifications.

The official client redeems pairing codes itself in its browser. For the phone, the native side
calls `GET /auth/connect/<code>` (`Pairing.redeem`) to obtain the session token, stores it as
the Basic-auth password for the monitor, then opens the server root with `?auth_token=` so the
official web client adopts the signed-in server entry itself (it consumes and strips the query
parameter at boot). The code is only fetched once, server-side redeeming still works for
browsers that open the printed link.

## Origin policy

The configured server is canonicalized to `scheme://host:port` by `normalizeServerUrl`. Every
security boundary uses `ServerOrigin.same`:

- bundled-asset interception
- bridge/camera permission
- HTTP Basic-auth challenge handling
- deciding whether a navigation stays in the WebView
- deciding whether a download receives the server token/cookies

Host-only matching is not used for these decisions. A different port or an `http://` downgrade is
a different origin.

External navigation is limited to `http`, `https`, `mailto` and `tel`. Other schemes are cancelled
rather than handed to an arbitrary Android intent.

## Bundled web client

`scripts/fetch-web-assets.mjs` mirrors the official web client at build time:

- starts at `/` and follows every reference it finds in the fetched files:
  HTML `<script>`/`<link>` tags, CSS `url()`, static/side-effect imports, dynamic
  `import(...)` specifiers and `__vite__mapDeps` preload arrays — so lazily-loaded
  chunks are included, not just the eager shell
- only `/_assets/`, `/assets/`, `/icons/`, `/fonts/` and a short allowlist of root files
- same-origin redirects only
- per-file and total size caps
- path-traversal rejection
- no URL or credential logging

`WebBundle` serves a bundled file only if it exists. API calls, SSE streams and unknown paths go
to the real server. On first launch it checks the live `index.html`; if the main script hash
differs, it disables the bundled UI for future launches so the app never silently runs an old
client against a new API.

The assets are gitignored. A release can be built without them; the app then uses the live web UI.

## Notifications

`MonitorService` is a `dataSync` foreground service with a persistent low-priority notification.
It parses SSE events and classifies them with `NotificationRules`:

- permission/question events → attention
- execution succeeded → task finished
- execution failed → task failed
- interrupted/cancelled → task interrupted

Notifications are suppressed when the user is actively viewing the same conversation and the
screen is interactive. Notification visibility is private on the lock screen.

The notification tap opens the app; it does not deep-link to a session because not every event
contains the directory needed to build the official `/server/<encoded>/session/<id>` route.

## Command palette

The official palette is bound to `Ctrl+K` / `Ctrl+Shift+P`. `WebViewPatches.COMMAND_PALETTE`
injects a button into `#opencode-titlebar-right` using the official `icon-button-v2` component.
On tap it dispatches the same keyboard event on `document`, with a delayed fallback to
`Ctrl+Shift+P`. It never opens when a dialog is already visible. See
[`AI_AGENTS.md`](AI_AGENTS.md) and `WebViewPatchesTest.kt`.

## Security boundaries

- credentials only go to the configured server origin and reachability probes of a server the official client just signed in to
- plaintext password fallback is not used
- mixed content is disabled
- third-party cookies are disabled
- the JS bridge is injected only on the server origin
- downloads to other origins get neither token nor cookies
- release builds are signed only in CI and include `SHA256SUMS`

See [`../SECURITY.md`](../SECURITY.md) for the threat model.

## Tests

The JVM test suite deliberately avoids Android types for the logic that matters:

```bash
./gradlew :app:testDebugUnitTest
```

Device-only behavior (camera, WebView rendering, notification sound/vibration) is listed in
[`../CONTRIBUTING.md`](../CONTRIBUTING.md) as a manual smoke test.
