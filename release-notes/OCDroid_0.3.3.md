# OCDroid v0.3.3

**Made with OpenCode, for OpenCode.** OCDroid is an independent Android shell for the official
OpenCode web client. It is not affiliated with, endorsed by, or maintained by OpenCode or
Anomaly.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. Installing over v0.3.2 keeps
your server settings. Verify `SHA256SUMS` before installing if you downloaded from a mirror.

## What changed since v0.3.2

- **Login fix.** Entering a login code no longer leaves a black screen. OCDroid now lets the
  official web client finish its own login flow instead of reloading the page.
- **Web QR scanning.** The official web client's login-page QR scanner can use the camera when
  Android grants the permission.

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
  network; the web client's QR scanner also needs a secure origin.
- Camera, notification sound/vibration, WebView rendering, and the command-palette button still
  need a real-device smoke test.

## Full changelog

See [`CHANGELOG.md`](https://github.com/anik-amd/ocdroid/blob/main/CHANGELOG.md).