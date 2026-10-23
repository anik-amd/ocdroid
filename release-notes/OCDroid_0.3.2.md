# OCDroid v0.3.2

**Made with OpenCode, for OpenCode.** OCDroid is an independent Android shell for the official
OpenCode web client. It is not affiliated with, endorsed by, or maintained by OpenCode or
Anomaly.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. Installing over v0.3.1 keeps
your server settings. Verify `SHA256SUMS` before installing if you downloaded from a mirror.

## What changed since v0.3.1

- **Bundled web client.** The release APK now includes the official OpenCode web client mirror,
  fetched from the configured server during the build. OCDroid still falls back to the live web
  client automatically when a newer server version is detected.
- **Monitoring notification.** The persistent notification now says "Connected with Opencode", uses
  the app's colored icon, and expands to show **Disconnect** and **Settings**. **Disconnect** stops
  monitoring and exits the app without clearing saved server settings.

## Command palette

Once the official web UI is open, tap the **command icon** (?) in the top-right titlebar to open
the official palette. A hardware keyboard can still use `Ctrl+K` / `Ctrl+Shift+P`. The button is
hidden while a dialog is open.

## Settings

Settings stays tucked away:

- Long-press the OCDroid launcher icon → **OCDroid settings**
- Or open the ongoing **OCDroid** notification → **Settings**

## Notes

- This release includes the optional bundled web-client mirror.
- The HTTP LAN mode remains available. Use HTTPS for any server reachable outside your trusted
  network.
- Camera, notification sound/vibration, WebView rendering, and the command-palette button still
  need a real-device smoke test.

## Full changelog

See [`CHANGELOG.md`](https://github.com/anik-amd/ocdroid/blob/main/CHANGELOG.md).