<p align="center">
  <img src="docs/banner.png" alt="OCDroid — an independent Android shell for the OpenCode web client">
</p>

<p align="center">
  <a href="https://github.com/anik-amd/ocdroid/actions/workflows/build.yml"><img alt="CI" src="https://github.com/anik-amd/ocdroid/actions/workflows/build.yml/badge.svg"></a>
  <a href="https://github.com/anik-amd/ocdroid/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/anik-amd/ocdroid?display_name=release"></a>
  <a href="LICENSE"><img alt="MIT" src="https://img.shields.io/badge/license-MIT-7c9cff.svg"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white">
</p>

> [!IMPORTANT]
> **Made with OpenCode, for OpenCode.** OCDroid is a community project by OpenCode users. It is
> **not affiliated with, endorsed by, sponsored by, or maintained by OpenCode or Anomaly.**
> “OpenCode” is used descriptively. See [Trademarks](TRADEMARKS.md).

---

## Why OCDroid?

Your agent keeps working when you leave the desk. OCDroid lets you stay in the loop without
opening a laptop:

- 👀 **See whether work is running, finished, failed or waiting for permission.**
- 🙋 **Answer permission prompts and questions** from the couch, the train or the kitchen.
- 🔔 **Get notified when an agent needs you**, with the conversation you are already reading
  suppressed so you are not pinged twice.
- 🧭 **Use the full official client** — sessions, projects, files, diffs, terminal and whatever
  else your OpenCode server exposes.
- ⌘ **Open the command palette with your thumb**, not a hardware keyboard.

The point is not to replace OpenCode. The point is to make the official client usable and
reliable on a phone.

## What it is — and what it is not

**It is** an Android WebView shell around the official OpenCode web client, with notifications,
background monitoring, encrypted credentials and a few small compatibility patches.

**It is not** a native chat client, a fork of the OpenCode UI, or an official OpenCode product.
There is no custom composer, no re-skinned sidebar, and no second implementation of the chat
protocol. If the official web client changes, OCDroid follows it.

## Screenshots

The UI is the OpenCode web client straight from your own server — in both themes.

<table>
  <tr>
    <td align="center" width="50%">
      <strong>Session chat — light</strong><br><br>
      <img src="docs/screenshots/light-chat.jpg" width="280" alt="Session chat, light theme">
    </td>
    <td align="center" width="50%">
      <strong>Session chat — dark</strong><br><br>
      <img src="docs/screenshots/dark-chat.jpg" width="280" alt="Session chat, dark theme">
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <strong>Session tabs — light</strong><br><br>
      <img src="docs/screenshots/light-tabs.jpg" width="280" alt="Session tabs, light theme">
    </td>
    <td align="center" width="50%">
      <strong>Session tabs — dark</strong><br><br>
      <img src="docs/screenshots/dark-tabs.jpg" width="280" alt="Session tabs, dark theme">
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <strong>Files — light</strong><br><br>
      <img src="docs/screenshots/light-files.jpg" width="280" alt="File browser, light theme">
    </td>
    <td align="center" width="50%">
      <strong>Files — dark</strong><br><br>
      <img src="docs/screenshots/dark-files.jpg" width="280" alt="File browser, dark theme">
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <strong>Command palette — light</strong><br><br>
      <img src="docs/screenshots/light-cmd.jpg" width="280" alt="Command palette opened from the titlebar button, light theme">
    </td>
    <td align="center" width="50%">
      <strong>Command palette — dark</strong><br><br>
      <img src="docs/screenshots/dark-cmd.jpg" width="280" alt="Command palette opened from the titlebar button, dark theme">
    </td>
  </tr>
</table>

## Features

| | Feature |
| --- | --- |
| 📱 | **Exact official UI** — renders the OpenCode web client from your own server |
| 🔗 | **Official login + pairing** — the official client's own login page, QR camera scanning, token handoff |
| 🔔 | **Native notifications** — finished, failed, interrupted, permission and question events |
| 🤫 | **Active-conversation suppression** — no alert for the session you are looking at |
| ⌘ | **Command-palette button** — injected into the official titlebar using official icon-button styles |
| 🔐 | **Encrypted credentials** — Android Keystore-backed storage; no plaintext password fallback |
| 📦 | **Optional bundled shell** — static web assets can be mirrored into the APK for faster startup |
| 🔄 | **Live fallback** — if the server's web client changed, OCDroid stops using the bundled copy |
| 🛡️ | **Origin-pinned WebView** — full scheme/host/port checks, no mixed content, scoped downloads |
| 🧪 | **JVM tests + CI** — tests run before every signed release build |
| 🚫 | **No telemetry** — the app talks to your server, not to us |

## Requirements

- Android **8.0 (API 26)** or newer
- An OpenCode server you can reach from the phone
- A password on the server is strongly recommended
- LAN: phone and computer on the same Wi-Fi
- Remote: OpenCode's built-in tunnel (`opencode pair --remote`) or your own HTTPS reverse proxy — **not** plain HTTP over the internet

## Quick start

### 1. Download

Get `OCDroid.apk` from the [latest release](https://github.com/anik-amd/ocdroid/releases/latest).
Verify `SHA256SUMS` if you downloaded from a mirror.

```bash
sha256sum -c SHA256SUMS
```

You may need to allow your browser or file manager to install unknown apps.

### 2. Start OpenCode on your computer

```bash
export OPENCODE_SERVER_PASSWORD='choose-a-strong-password'
opencode web --mdns
```

Or use a fixed LAN address:

```bash
opencode web --hostname 0.0.0.0 --port 4096
```

Use `OPENCODE_SERVER_PASSWORD` before starting the server. If you skip it, anyone who can reach
the port may be able to use your agent.

### 3. Connect the phone

Open OCDroid. A **welcome screen** explains the whole setup — no server configured yet. It asks
one question first: **where is your phone right now?** Then:

**🏠 At home — phone on the same Wi-Fi:**

1. On the computer running OpenCode, run `opencode pair` in a terminal.
2. In the app, tap **Scan pairing QR** and scan the code — OCDroid redeems the pairing, hands
   the session token to the official web client, and you land in your OpenCode UI.

**🌍 Away — mobile data or a different network:**

1. On the computer, run `opencode pair --remote` (add `--yes` when running from a script, so the
   expose-the-server confirmation is skipped). The first run opens a secure tunnel through
   OpenCode's own relay and can take a couple of minutes.
2. In the app, turn Wi-Fi off and tap **Scan pairing QR**.

The remote QR carries **both** the LAN and the tunnel address — the app redeems it over whichever
is reachable, so a phone paired this way keeps working when you come home or leave.

No terminal at hand? Tap **Copy instruction for my agent** on the welcome screen and paste the
message into a new OpenCode session on the computer: the AI agent starts the web server, asks
where your phone is, runs the matching pairing command, and shows a QR code — you just scan.

Alternatives on the same welcome screen:

- paste the pairing link (or a server address) and tap **Connect**, or
- change the server, username and password in hidden Settings (long-press the launcher icon).

Whether the server runs locally (`http://192.168.x.x:4096`) or through a tunnel, the official
web UI itself handles every login step after that — including its own camera QR scanner.

### 4. Allow notifications

Android may ask for notification permission. Say yes if you want task and permission alerts.
You can test sound and vibration from Settings.

### 5. Remote access details

`opencode pair --remote` exposes the server through OpenCode's built-in HTTPS tunnel — no port
forwarding, no third-party proxy config. Important to know:

- Pairing links are **single-use** and valid for five minutes; run the command again for a fresh one.
- The exposure is **session-scoped**: if the phone can't reach the server after OpenCode restarts,
  run `opencode pair --remote` once more and re-pair (one scan).
- Do not expose a plain HTTP OpenCode port to the public internet instead. Prefer the tunnel or
  your own HTTPS reverse proxy.
- LAN pairing (`opencode pair`) never exposes the server to the internet — the QR only carries
  addresses reachable on your own network.

## ⌘ Command palette

The official client already has a command palette bound to `Ctrl+K` / `Ctrl+Shift+P`. Phones do
not have a Ctrl key, so OCDroid adds a small command button to the official titlebar:

- Look for the **⌘ icon** in the top-right titlebar while the web UI is open.
- Tap it to open the official palette.
- With a hardware keyboard, use `Ctrl+K` or `Ctrl+Shift+P`.
- The button uses the official `icon-button-v2` component and theme variables, so it follows the
  app's light/dark theme instead of looking bolted on.
- It hides while a dialog is open and does not open a second palette on top of one.

The button is a compatibility patch, not a new UI. If a future OpenCode release changes the
titlebar, the keyboard shortcut remains the fallback. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## ⚙️ Hidden Settings

Settings is deliberately not a big obvious button in the official UI. To keep the page
pixel-identical, it lives in Android's app surface:

- **Long-press the OCDroid launcher icon** → **OCDroid settings**
- Or open the ongoing **OCDroid connected** notification → **Settings**

There you can change the server, username and password, toggle task notifications and background
monitoring, test the notification channel, disable the bundled UI, and clear cached web content.

Some launchers hide app shortcuts. Keep background monitoring on to keep the notification action
available.

## 🔐 Security and privacy

- Credentials are stored with **Android Keystore-backed `EncryptedSharedPreferences`**.
- If Keystore is unavailable, the password stays **in memory** for the current process; it is not
  written to a plaintext fallback file. Older plaintext fallback data is migrated and removed.
- LAN discovery was removed; the app never probes hosts on your network.
- The WebView compares **full origins** (scheme + host + port) for the bridge, camera, auth,
  bundled assets and downloads.
- Mixed content and third-party cookies are disabled.
- External links are limited to `http`, `https`, `mailto` and `tel`; other schemes are cancelled.
- Download credentials and cookies are attached **only** for your configured server origin.
- The bundled-asset mirror only fetches known static paths, follows same-origin redirects and
  rejects path traversal.
- Notifications use private lock-screen visibility.
- Releases include `SHA256SUMS`.

**HTTP LAN tradeoff:** OCDroid supports `http://192.168.x.x:4096` because that is how many local
OpenCode servers run. Traffic on that LAN can be observed or modified by others on the network.
Use HTTPS for remote access and trusted Wi-Fi for HTTP. See [`SECURITY.md`](SECURITY.md).

## 🧰 Troubleshooting

| Problem | Try this |
| --- | --- |
| Nothing loads at all | The server may be down or the tunnel went away. OCDroid shows the welcome screen with the dead address — unplug/replug, restart OpenCode, then **Retry**, or just re-pair (one scan). Manual entry is on the same screen. |
| 401 / password prompt | Set `OPENCODE_SERVER_PASSWORD` before starting the server, then enter the same password. Pairing links/tokens also work. |
| Camera won't open in the official QR scanner | WebView usually requires a secure origin for camera. Use an HTTPS tunnel, or paste the pairing link instead. |
| No notifications | Allow notifications, keep background monitoring on, and exempt OCDroid from battery optimization. Use **Send test notification**. |
| Command button is missing | The official titlebar may have changed. Use `Ctrl+K` / `Ctrl+Shift+P` with a hardware keyboard, and check for an OCDroid update. |
| Can't install over an old build | The old APK was signed with another key. Uninstalling deletes app settings — ask before doing it. |
| Settings shortcut disappeared | Some launchers hide shortcuts. Use the **Settings** action on the ongoing monitoring notification. |
| The UI is stale | Settings → **Clear cached web content**, or turn off **Bundled web UI** to always load live. |

More in [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md).

## 🏗️ Build from source

You do **not** need Android Studio to contribute. CI builds and tests the app.

```bash
git clone https://github.com/anik-amd/ocdroid.git
cd ocdroid
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

On Windows, use `gradle` (Gradle 8.6) or run the shell script through Git Bash/WSL.

Requirements for a local build: JDK 17, Android SDK 34, and Node 20+ only if you want to mirror
the web client.

### Optional bundled web shell

`app/src/main/assets/web/` is generated and gitignored. To mirror the official client:

```bash
OC_SERVER_URL="https://your-server.example" \
OC_SERVER_USERNAME="opencode" \
OC_SERVER_PASSWORD="your-password" \
node scripts/fetch-web-assets.mjs
```

`OC_SERVER_URL` is required. The script never logs it and only crawls static shell paths. In CI
the value comes from repository secrets.

### CI and releases

GitHub Actions runs JVM tests in a separate job, builds a debug APK for pull requests, and only
then builds a signed release for `main`. Releases are normal versioned GitHub releases with
`OCDroid.apk` and `SHA256SUMS`.

- Workflow: [`.github/workflows/build.yml`](.github/workflows/build.yml)
- Release guide: [`docs/RELEASING.md`](docs/RELEASING.md)
- Latest release: <https://github.com/anik-amd/ocdroid/releases/latest>

## 📚 Documentation

| Document | What it covers |
| --- | --- |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | dev setup, tests, PR checklist, security invariants |
| [`SECURITY.md`](SECURITY.md) | threat model, reporting, secret handling, release verification |
| [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) | community standards |
| [`docs/AI_AGENTS.md`](docs/AI_AGENTS.md) | safe install instructions and contribution workflow for AI agents |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | WebView, bridge, discovery, bundle, notifications, palette |
| [`docs/RELEASING.md`](docs/RELEASING.md) | versioning, secrets, release checklist |
| [`CHANGELOG.md`](CHANGELOG.md) | release history |
| [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) | current limitations |
| [`TRADEMARKS.md`](TRADEMARKS.md) | naming and non-affiliation policy |

## 🤖 For AI agents

If you are an AI agent installing OCDroid or contributing changes, read
[`docs/AI_AGENTS.md`](docs/AI_AGENTS.md) first. The short version:

- Ask before installing or uninstalling an APK on someone's device.
- Download only from the official releases page and verify `SHA256SUMS`.
- Never ask for or print passwords, pairing tokens or tunnel hostnames.
- Do not add a native chat UI or bypass origin/mixed-content/TLS checks.
- Do not commit generated web assets, keystores or secrets.
- Disclose AI assistance in the pull request.

## 🙏 Credits and license

OCDroid is **MIT licensed**. Current OCDroid contributors are credited in [`LICENSE`](LICENSE).

The app is a clean WebView shell around the web client shipped by
[anomalyco/opencode](https://github.com/anomalyco/opencode).

**Made with OpenCode, for OpenCode.** Independent project; not affiliated with OpenCode or
Anomaly.
