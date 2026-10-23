# OCDroid v0.3.4

**Made with OpenCode, for OpenCode.** OCDroid is an independent Android shell for the official
OpenCode web client. It is not affiliated with, endorsed by, or maintained by OpenCode or
Anomaly.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. Installing over v0.3.3
keeps your server settings. Verify `SHA256SUMS` before installing if you downloaded from a
mirror.

## What changed since v0.3.3

- **No more custom login page.** Everything except the WebView is gone. When no server is
  configured yet, OCDroid opens the official web client's own *"Connect to a server"* page
  straight from the APK — paste your pairing link there, or tap **Scan QR code** to open the
  camera and scan the code shown by `opencode pair`.
- **Black screen after entering a login code is fixed for real.** The bundled web-client mirror
  shipped only statically referenced files; every lazily-loaded chunk (the app shell after
  login, dialogs, settings, and the QR scanner itself) was missing. The mirroring script now
  crawls JS import statements and Vite preload arrays, so all 383 files are bundled.
- **QR scanning works.** The complete mirror includes the official camera scanner chunk, so the
  login page's scanner opens the camera, scans the pairing code and signs you in.
- If no server address is entered yet and you prefer typing it manually, the hidden Settings
  still works (long-press the launcher icon → **OCDroid settings**).

## Command palette

Once the official web UI is open, tap the **command icon** in the top-right titlebar to open
the official palette. A hardware keyboard can still use `Ctrl+K` / `Ctrl+Shift+P`. The button is
hidden while a dialog is open.

## Settings

Settings stays tucked away:

- Long-press the OCDroid launcher icon → **OCDroid settings**
- Or open the ongoing **OCDroid** notification → **Settings**

## Notes

- This release includes the complete offline mirror of the official web client (383 files).
- The HTTP LAN mode remains available. Use HTTPS for any server reachable outside your trusted
  network; the web client's QR scanner needs a secure origin.
- Camera, notification sound/vibration, WebView rendering, and the command-palette button still
  need a real-device smoke test.

## Full changelog

See [`CHANGELOG.md`](https://github.com/anik-amd/ocdroid/blob/main/CHANGELOG.md).
