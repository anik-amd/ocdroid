# OCDroid v0.3.7

**Made with OpenCode, for OpenCode.** OCDroid is an independent Android shell for the official
OpenCode web client. It is not affiliated with, endorsed by, or maintained by OpenCode or
Anomaly.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. Installing over any older
v0.3.x keeps your server settings. Verify `SHA256SUMS` before installing if you downloaded from
a mirror.

## What changed since v0.3.6

- **Command palette works on phones.** The ⌘ button in the top-right of the titlebar was never
  visible on phones: the official mobile layout keeps its closed bottom drawer in the DOM as a
  `role="dialog"` element, and the palette patch thought a dialog was permanently open. The
  button now shows and opens the official command palette. With a hardware keyboard,
  `Ctrl+K` / `Ctrl+Shift+P` still work as before.
- **The offline mirror now actually ships.** The Android asset packager silently drops files in
  directories starting with an underscore, so the mirror's `/_assets/` JS chunks were missing
  from every APK since v0.3.4 and the app always fell back to the live server. The chunks are
  stored under a packageable directory now; the bundled copy works in this release.

## Good to know

- Pairing is unchanged since v0.3.6: scan the `opencode pair` (or `opencode pair --remote`) QR
  on the welcome screen and you are signed in — the code is redeemed and handed to the official
  web client.
- The welcome screen from v0.3.5 with the copy-paste instruction for your OpenCode agent is
  unchanged.

## Full changelog

See [`CHANGELOG.md`](https://github.com/anik-amd/ocdroid/blob/main/CHANGELOG.md).
