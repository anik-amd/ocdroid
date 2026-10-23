# OCDroid v0.3.5

**Made with OpenCode, for OpenCode.** OCDroid is an independent Android shell for the official
OpenCode web client. It is not affiliated with, endorsed by, or maintained by OpenCode or
Anomaly.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. Installing over v0.3.4
keeps your server settings. Verify `SHA256SUMS` before installing if you downloaded from a
mirror.

## What changed since v0.3.4

- **Welcome screen with real setup instructions.** When no server is connected — or the saved
  server cannot be reached (an expired tunnel, a laptop that is off) — OCDroid shows a welcome
  page instead of a blank screen. It explains, in order:
  1. On the computer running OpenCode, run `opencode pair` (or `opencode pair --remote` for
     tunnels). A QR code appears.
  2. Tap **Scan pairing QR** here and scan it — the pairing is redeemed by the official client,
     and OCDroid saves the server automatically.
  3. Anything else (a rotated tunnel, moving to a new network) is one scan away: just pair again.
- **Setup-by-agent.** Don't feel like using a terminal? The welcome screen contains a
  copy-paste instruction for the AI agent itself: ask your OpenCode session to set up the web
  server (`OPENCODE_SERVER_PASSWORD=… opencode web --hostname 0.0.0.0 --port 4096`) and print a
  pairing QR when done. Paste it, let the agent work, then scan.
- Manual entry (server address + password) and hidden Settings remain available on the same
  welcome page.

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
