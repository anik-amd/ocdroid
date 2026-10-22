# OCDroid v0.1.0

Android shell for the **official OpenCode web client**, with server discovery, QR pairing,
encrypted credentials and background task notifications. The UI is the real OpenCode web app,
not a reimplementation.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. You may need to allow
installing apps from unknown sources for your browser or file manager.

## Highlights

- **Enter now inserts a newline** in the composer instead of sending the prompt. Use the send
  button (or `Ctrl/Cmd+Enter`) to submit.
- **Task notifications are more reliable.** The monitor listens to the v2 global event stream,
  notices completion even if it missed the busy event, and alerts for permissions and forms that
  need an answer.
- **New high-importance notification channels** for task completion and attention-required
  events, with vibration and heads-up display where Android allows it.
- **QR pairing now gives visible feedback.** Failures show a dialog instead of silently doing
  nothing. If the QR only contains `localhost`, OCDroid explains how to run
  `opencode pair --remote`.
- **Settings diagnostics** show the live monitor state, last event and notification count. A
  **Send test notification** button separates an Android notification problem from a server
  connection problem.
- The official web client is mirrored into the APK at build time, while `/api/*` still talks to
  your own OpenCode server.
- Credentials are stored with Android Keystore-backed encrypted preferences.

## Pairing

On the computer running OpenCode:

```bash
opencode pair --remote
```

Scan the QR code from the app, or paste the printed link into the server field. A QR that only
contains `127.0.0.1` or `localhost` cannot work from the phone; use `--remote` or start the
server with a LAN-reachable hostname.

## Notes

- Android 13+ requires notification permission. If task alerts do not appear, open
  **OCDroid settings → Send test notification** and check the diagnostics panel.
- Battery optimisation can delay background work on some phones. The foreground monitoring
  notification should remain visible while monitoring is enabled.
- This is an unofficial personal client and is not affiliated with OpenCode or Anomaly.
