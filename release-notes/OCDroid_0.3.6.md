# OCDroid v0.3.6

**Made with OpenCode, for OpenCode.** OCDroid is an independent Android shell for the official
OpenCode web client. It is not affiliated with, endorsed by, or maintained by OpenCode or
Anomaly.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. Installing over v0.3.5
keeps your server settings. Verify `SHA256SUMS` before installing if you downloaded from a
mirror.

## What changed since v0.3.5

- **Pairing QR scan now signs you in.** Scanning the QR from `opencode pair` (or `opencode pair
  --remote`) redeems the pairing code immediately and hands the session token to the official
  web client, which adopts the signed-in server entry at boot. In v0.3.5 the scan only saved
  the server address, so the official login page still asked for the pairing link afterwards.

## Notes

- The welcome screen from v0.3.5 is unchanged: setup steps, the copy-paste instruction for your
  OpenCode agent, the native QR scanner, and manual address + password entry all in one place.
- Pairing links are single-use and expire after five minutes — run `opencode pair` again for a
  fresh one whenever a scan fails.
- This release includes the complete offline mirror of the official web client (383 files).

## Full changelog

See [`CHANGELOG.md`](https://github.com/anik-amd/ocdroid/blob/main/CHANGELOG.md).
