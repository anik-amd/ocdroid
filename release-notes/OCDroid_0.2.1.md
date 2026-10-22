# OCDroid v0.2.1

Android shell for the **official OpenCode web client**, with server discovery, QR pairing,
encrypted credentials and background task notifications. The UI is the real OpenCode web app,
not a reimplementation.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. You may need to allow
installing apps from unknown sources for your browser or file manager. Installing over v0.2.0
keeps your server settings.

## What changed

- **Settings is reachable.** The launcher's **Settings** shortcut now opens correctly, and the
  ongoing monitoring notification has a **Settings** action. From there you can enable alerts,
  confirm the monitor is connected, and tap **Send test notification** to hear the sound and feel
  the vibration.
- **Settings is safe to open while paired.** The official client's token stays in the encrypted
  password field; saving settings does not change the server.
- This is the same official-login, camera and notification work as v0.2.0, with easier access to
  the notification test screen.

## Pairing

On the computer running OpenCode:

```bash
opencode pair --remote
```

Scan the QR code from the app, or paste the printed link into the server field. A QR that only
contains `127.0.0.1` or `localhost` cannot work from the phone; use `--remote` or start the
server with a LAN-reachable hostname.

## Notes

- **Android 13+** requires notification permission. If alerts do not appear, open **Settings →
  Send test notification** and check the diagnostics panel. You can open Settings by long-pressing
  the OCDroid launcher icon, or from the monitoring notification's **Settings** action.
- The official QR scanner needs a **secure origin**. Use the HTTPS tunnel from
  `opencode pair --remote`; plain HTTP LAN pages cannot use the browser camera.
- Battery optimisation can delay background work on some phones. The foreground monitoring
  notification should remain visible while monitoring is enabled.
- This is an unofficial personal client and is not affiliated with OpenCode or Anomaly.
