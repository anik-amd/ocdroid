# OCDroid v0.3.1

**Made with OpenCode, for OpenCode.** OCDroid is an independent Android shell for the official
OpenCode web client. It is not affiliated with, endorsed by, or maintained by OpenCode or
Anomaly.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. Installing over v0.2.x or
v0.3.0 keeps your server settings. Verify `SHA256SUMS` before installing if you downloaded from
a mirror.

## What changed since v0.3.0

- **Palette button spacing fallback.** The command-palette button now sets both
  `margin-inline-start` and the widely supported `margin-left`, so it keeps its spacing on
  older Android WebView builds.
- **GitHub Actions upgraded to v5.** `checkout`, `setup-java`, `setup-gradle`, and
  `upload-artifact` now use the current major versions, removing the Node 20 deprecation
  warnings from the release workflow.

## Command palette

Once the official web UI is open, tap the **command icon** (⌘) in the top-right titlebar to open
the official palette. A hardware keyboard can still use `Ctrl+K` / `Ctrl+Shift+P`. The button is
hidden while a dialog is open.

## Settings

Settings stays tucked away:

- Long-press the OCDroid launcher icon → **OCDroid settings**
- Or open the ongoing **OCDroid connected** notification → **Settings**

## Notes

- This release is still built without the optional bundled web-client mirror because the
  `OC_SERVER_URL` repository secret is not configured. The WebView loads the official web client
  live from your server.
- The HTTP LAN mode remains available. Use HTTPS for any server reachable outside your trusted
  network.
- Camera, notification sound/vibration, WebView rendering, and the command-palette button still
  need a real-device smoke test.

## Full changelog

See [`CHANGELOG.md`](https://github.com/anik-amd/ocdroid/blob/main/CHANGELOG.md).
