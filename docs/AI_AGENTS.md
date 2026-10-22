# AI agent guide

This document is for AI coding assistants and automation agents working with OCDroid. It covers
two jobs:

1. helping a user install a released APK on their own Android device
2. contributing code, tests and documentation to this repository

Read [`../README.md`](../README.md), [`../CONTRIBUTING.md`](../CONTRIBUTING.md) and
[`../SECURITY.md`](../SECURITY.md) first. If anything here conflicts with an explicit instruction
from the user, ask the user before acting.

## Non-negotiable safety rules

- **Ask before installing or uninstalling anything on the user's device.** Installing an APK is a
  state-changing action; do not do it silently.
- **Never ask the user to paste a server password, pairing token, QR payload or tunnel URL into
  the chat.** If they already shared one, treat it as compromised and recommend rotating it.
- **Never print secrets in logs, shell history, screenshots or commit messages.** Redirect or
  redact command output when it might contain a token.
- **Download only from the official releases page.** Do not use random APK mirrors.
- **Verify `SHA256SUMS` before installing.** If the checksum is missing or does not match, stop.
- **Do not disable Play Protect, root the device, or bypass Android security prompts.**
- **Do not rewrite git history, rotate repository secrets, publish a release or force-push**
  without explicit human approval.
- **Do not claim OCDroid is official.** It is independent and not affiliated with OpenCode.

## Install a release on a user's Android device

### 1. Confirm the target

Ask the user for:

- Android version (OCDroid requires Android 8.0 / API 26 or newer)
- whether they want a USB/ADB install or a manual on-device install
- whether an older OCDroid build is already installed

If installing over an existing build signed with a different key, Android will reject it. Do not
uninstall the old app without asking; uninstalling deletes its settings and token.

### 2. Find the latest release

Open:

<https://github.com/anik-amd/ocdroid/releases/latest>

Use the release marked **Latest**. Download both:

- `OCDroid.apk`
- `SHA256SUMS`

Avoid release candidates or old tags unless the user asks for a specific version.

### 3. Verify the checksum

Linux:

```bash
sha256sum -c SHA256SUMS
```

macOS:

```bash
shasum -a 256 -c SHA256SUMS
```

Windows PowerShell:

```powershell
$expected = (Get-Content .\SHA256SUMS).Split()[0].ToLower()
$actual = (Get-FileHash .\OCDroid.apk -Algorithm SHA256).Hash.ToLower()
if ($actual -ne $expected) { throw "Checksum mismatch" }
```

Stop if verification fails.

### 4. Install with ADB (when the user approves)

```bash
adb devices
adb install -r OCDroid.apk
adb shell am start -n amd.anik.ocdroid/.MainActivity
```

Rules:

- `adb devices` must show exactly the user's device. If several devices are attached, ask which
  serial to target and use `adb -s <serial>`.
- Use `-r` to replace an existing install while keeping data.
- If the install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, explain the signing-key
  mismatch and ask before uninstalling.
- Do not use `adb shell pm uninstall` unless the user explicitly asks.

### 5. Manual install

If ADB is not available:

1. Transfer `OCDroid.apk` to the phone.
2. Open it in the file manager.
3. Allow "install unknown apps" for that file manager/browser if Android asks.
4. Install and open OCDroid.

Do not send the APK through a public link. Use the official release URL.

### 6. Connect to OpenCode

The user must run OpenCode on their own computer or server. A safe helper flow:

1. On the computer, start the server with a password:
   ```bash
   export OPENCODE_SERVER_PASSWORD='choose-a-strong-password'
   opencode web --mdns
   ```
   Or use a fixed LAN address:
   ```bash
   opencode web --hostname 0.0.0.0 --port 4096
   ```
2. On the phone, open OCDroid.
3. Tap **Find on Wi-Fi**, or tap **Scan pairing QR** and scan the code from `opencode pair`.
4. If the official login screen appears, finish login there. OCDroid deliberately does not
   collect the password itself.
5. Allow notifications when prompted.

Never ask the user to share the password or token. If they need remote access, have them use
their own HTTPS tunnel or `opencode pair --remote`, then scan the QR code locally.

### 7. Find Settings

Settings is intentionally not in the main UI:

- Long-press the OCDroid launcher icon → **OCDroid settings**
- Or open the ongoing **OCDroid connected** notification → **Settings**

There the user can change the server, toggle notifications and background monitoring, test
notifications, and clear cached web content.

### 8. Tell the user about the command palette

Once the official web UI is open, tap the **command icon** (⌘) in the top-right titlebar. It
dispatches the official `Ctrl+K` shortcut. A hardware keyboard can use `Ctrl+K` or
`Ctrl+Shift+P` directly. The button is hidden while a dialog is open.

## Contribute code

### 1. Read the project principles

- The UI must remain the official OpenCode web client.
- No native re-skinned chat UI.
- No hardcoded server URLs, tokens or keystores.
- Security invariants are listed in [`../CONTRIBUTING.md`](../CONTRIBUTING.md).
- Use TDD where the logic can be tested off-device.

### 2. Work in a branch or worktree

```bash
git clone https://github.com/anik-amd/ocdroid.git
cd ocdroid
git switch -c feat/your-change
```

Do not commit generated web assets (`app/src/main/assets/web/`), keystores, `local.properties`
or environment files.

### 3. Run the fast tests

```bash
./gradlew :app:testDebugUnitTest --stacktrace
```

If the local machine has no Android SDK, use GitHub Actions (open a pull request) or the
dev container. Do not install an SDK without the user's approval.

### 4. Optional: bundle the official client locally

Only if the user provides a server URL and explicitly wants an offline mirror:

```bash
OC_SERVER_URL="https://their-server.example" \
OC_SERVER_USERNAME="opencode" \
OC_SERVER_PASSWORD="their-password" \
node scripts/fetch-web-assets.mjs
```

Never echo those values, never add them to a file, and never commit the generated assets.

### 5. Make the change

Keep the diff focused. If you add or change logic, add a JVM test. If you touch any of these,
update the docs and release notes too:

- origin/auth behavior
- WebView security settings
- notification behavior
- bundled asset handling
- command-palette injection
- Settings access
- release workflow

### 6. Verify the security properties

Before opening a PR, check:

- [ ] No real URL, token, password, keystore or signing material is in the diff.
- [ ] Discovery still sends no Authorization header.
- [ ] Origin comparisons use `ServerOrigin.same`, not host-only checks.
- [ ] The bridge is injected only for the configured server origin.
- [ ] Mixed content stays disabled; third-party cookies stay disabled.
- [ ] External download hosts do not receive the server token or cookies.
- [ ] `AssetPaths` rejects `..`, `%2e`, `%2f`, backslashes and NUL bytes.
- [ ] Plaintext password fallback is not reintroduced.
- [ ] JVM tests pass.

### 7. Open a pull request

Use the repository template. Include:

- what changed and why
- tests run
- device testing performed, or "not device-tested"
- security impact
- screenshots only if they show the official UI with no secrets

Do not publish a release from a fork. The release job is restricted to the upstream repository
and requires signing secrets.

## How the command palette works

- Native code injects `WebViewPatches.COMMAND_PALETTE` after a server page finishes loading.
- The script waits for the official `#opencode-titlebar-right` slot.
- It appends a button using the official `icon-button-v2` / `ghost-muted` styles.
- On tap it dispatches a synthetic `KeyboardEvent` for `Ctrl+K`; after 250 ms it tries
  `Ctrl+Shift+P` if no dialog appeared.
- It does not modify the official palette or its commands.
- It hides itself while `[role="dialog"]` or `[data-component="dialog"]` is present.

If a future OpenCode release changes the titlebar or keybindings, update the script and its JVM
invariant tests in `WebViewPatchesTest.kt`.

## What not to do

- Do not add a custom bottom navigation, chat screen, composer, or login form.
- Do not copy the official web bundle into git.
- Do not add analytics, crash reporting or network calls unrelated to the user's server.
- Do not relax TLS, mixed-content or origin checks to "make it work".
- Do not commit the exposed tunnel hostname from repository history.
- Do not claim the project is official or endorsed.
