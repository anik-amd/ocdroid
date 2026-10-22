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
│       ├── ServerDiscovery ── mDNS + /24 probe (no creds)     │
│       ├── Pairing ── QR / pairing-link parsing               │
│       └── MonitorService ── SSE ── Android notifications     │
└──────────────────────────────────────────────────────────────┘
```

## Components

| Component | Responsibility |
| --- | --- |
| `MainActivity` | WebView lifecycle, setup UI, origin policy, camera/file chooser, downloads, bridge |
| `WebViewPatches` | Two small injected scripts: Enter-to-newline and the command-palette button |
| `WebBundle` | Serves the optional packaged `index.html` and static assets; live-version check |
| `SettingsStore` | Encrypted settings, Keystore fallback policy, server config |
| `ServerDiscovery` | mDNS/DNS-SD and bounded LAN probing, unauthenticated |
| `Pairing` | Parses pairing links/QR payloads and chooses a reachable URL |
| `MonitorService` | Foreground SSE connection and notification decisions |
| `NotificationRules` | Pure classification of OpenCode events (JVM-tested) |
| `EventParser` | Lightweight SSE payload parsing without Android dependencies |
| `SessionRoutes` | Extracts the session id from the official web route |
| `ActiveSessionStore` | Shared in-process foreground/session state |
| `ServerOrigin` | Scheme + host + port normalization for origin checks |
| `AssetPaths` | Path-traversal-safe mapping into `assets/web` |

## Startup and connection

1. `MainActivity` reads `SettingsStore`.
2. If configured, it loads the server root in the WebView. Otherwise it shows the setup panel and
   starts unauthenticated discovery.
3. The official web client handles login and stores its own server entry in `localStorage`.
4. `WebViewPatches.nativeBridge` reads that entry and reports the URL/token to
   `OCDroidNative.onServerConnected`. OCDroid stores the token as the Basic-auth password so the
   native monitor can use it.
5. The monitor connects to `/api/event` (v2), `/global/event` or `/event` (v1) and translates
   events into notifications.

Pairing is deliberately delegated to the official login screen. The native side may parse a QR
payload, but it does not redeem the code itself: doing that consumed the code before the web
client could create its own signed-in server entry.

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

`scripts/fetch-web-assets.mjs` mirrors the static shell at build time:

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

- credentials never go to discovery probes
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
