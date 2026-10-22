# OCDroid v0.3.0

**Made with OpenCode, for OpenCode.** OCDroid is an independent Android shell for the official
OpenCode web client. It is not affiliated with, endorsed by, or maintained by OpenCode or
Anomaly.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. Installing over v0.2.x keeps
your server settings. Verify `SHA256SUMS` before installing if you downloaded from a mirror.

## Highlights

- **Command palette on a phone.** A small command button now sits in the official web UI's
  titlebar. Tap it to open the official command palette without a hardware keyboard. It uses the
  official icon-button styles and theme variables, so it looks native to the web client. The
  existing `Ctrl+K` / `Ctrl+Shift+P` shortcuts still work.
- **Safer setup and networking.** The hardcoded tunnel URL is gone. LAN discovery no longer sends
  your password/token to every address on the network. WebView security now compares full
  origins, disables mixed content and third-party cookies, scopes download credentials to the
  server, and rejects unsafe external schemes.
- **Safer credential storage.** If Android Keystore/EncryptedSharedPreferences is unavailable,
  the password stays in memory instead of being written to a plaintext preference file. Older
  plaintext fallback data is migrated and removed.
- **Safer bundled web client.** The mirror script requires `OC_SERVER_URL`, only fetches known
  static paths, follows same-origin redirects only, blocks path traversal and enforces size
  limits. It no longer prints the server URL.
- **Private notifications.** Task and attention notifications no longer expose full session
  titles on the lock screen.
- **Hardened releases.** CI now runs JVM tests in a separate job before the signed release job,
  uses read-only workflow permissions by default, pins Actions to commit SHAs, disables persisted
  checkout credentials, publishes `SHA256SUMS`, and removes signing material after the build.
- **Real documentation.** New contribution, security, code-of-conduct, AI-agent, architecture and
  release guides; the public README now explains why the app exists, how to pair, where Settings
  hides, and what is not affiliated.

## Settings

Settings is still deliberately tucked away:

- Long-press the OCDroid launcher icon → **OCDroid settings**
- Or open the ongoing **OCDroid connected** notification → **Settings**

## Pairing

Start OpenCode on your computer with a password, then use **Find on Wi-Fi** or **Scan pairing QR**
on the phone. The official OpenCode login screen handles the actual pairing and token exchange.
If the official QR scanner cannot use the camera on an HTTP LAN origin, use an HTTPS tunnel or
paste the pairing link.

## Notes

- The HTTP LAN mode remains available. Use HTTPS for any server reachable outside your trusted
  network.
- Camera, notification sound/vibration and WebView rendering still need a real-device smoke test.
- The command-palette button depends on the official titlebar slot; if a future OpenCode release
  changes it, the keyboard shortcuts remain the fallback.

## Full changelog

See [`CHANGELOG.md`](https://github.com/anik-amd/ocdroid/blob/main/CHANGELOG.md).
