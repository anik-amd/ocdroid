# OCDroid v0.3.8

**Made with OpenCode, for OpenCode.** OCDroid is an independent Android shell for the official
OpenCode web client. It is not affiliated with, endorsed by, or maintained by OpenCode or
Anomaly.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. Installing over any older
v0.3.x keeps your server settings. Verify `SHA256SUMS` before installing if you downloaded from
a mirror.

## What changed since v0.3.7

- **The welcome screen now teaches the two connection modes.** It asks where your phone is and
  shows clearly separated paths: at home — `opencode pair` over the same Wi-Fi; away —
  `opencode pair --remote` through OpenCode's built-in HTTPS tunnel (add `--yes` when running
  from a script; the first run can take a couple of minutes). It also documents the rules that
  bit people in practice: pairing links are single-use and expire after five minutes, and the
  remote exposure is session-scoped — re-run the command and re-pair with one scan after an
  OpenCode restart.
- **One pairing, both networks.** The remote QR carries both the LAN and the tunnel address, so
  a phone paired this way keeps working when it moves between them.
- The agent handoff instruction now asks the agent to check where the phone is before running
  the matching pairing command, and the help dialog matches the current pairing flows (the
  removed "Find on Wi-Fi" discovery button is no longer referenced).
- Docs (README, AI_AGENTS, ARCHITECTURE) describe the same two-path guidance.

Nothing else changed — pairing, the ⌘ command-palette button and the bundled mirror behave as in
v0.3.7.

## Full changelog

See [`CHANGELOG.md`](https://github.com/anik-amd/ocdroid/blob/main/CHANGELOG.md).
