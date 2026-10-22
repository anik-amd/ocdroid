# OCDroid v0.2.0

Android shell for the **official OpenCode web client**, with server discovery, QR pairing,
encrypted credentials and background task notifications. The UI is the real OpenCode web app,
not a reimplementation.

## Install

Download `OCDroid.apk` below and install it on Android 8.0 or newer. You may need to allow
installing apps from unknown sources for your browser or file manager.

## Highlights

- **The official OpenCode connect screen is now the login surface.** Pairing links and QR
  payloads load the server, fill the official form and let the official client redeem the code.
  The old native redemption that left the web app on its login screen is gone.
- **The official QR scanner gets camera access.** OCDroid now grants WebView camera permission
  requests for your server, including the Android runtime permission prompt.
- **The native pairing scanner is portrait and clean.** The red laser line is removed and the
  camera no longer flips into landscape.
- **Notifications have sound and vibration.** New `tasks_v3` and `attention_v3` channels use
  the Android notification sound with distinct vibration patterns.
- **Open conversations stay quiet.** If you are already viewing the conversation that finishes
  or asks for attention, no notification is posted. Notifications for other conversations still
  arrive.
- **Opening a conversation dismisses its notification**, whether you opened it from the
  notification or by navigating in the web app.
- The official client's session token is read back into encrypted native settings, so the
  background monitor uses the same credentials as the web client.
- Manual field says “Server URL or pairing link” and still accepts pasted pairing links.

## Pairing

On the computer running OpenCode:

```bash
opencode pair --remote
```

Scan the QR code from the app, or paste the printed link into the server field. A QR that only
contains `127.0.0.1` or `localhost` cannot work from the phone; use `--remote` or start the
server with a LAN-reachable hostname.

## Notes

- **Android 13+** requires notification permission. If alerts do not appear, open
  **OCDroid settings → Send test notification** and check the diagnostics panel.
- The official QR scanner needs a **secure origin**. Use the HTTPS tunnel from
  `opencode pair --remote`; plain HTTP LAN pages cannot use the browser camera.
- Battery optimisation can delay background work on some phones. The foreground monitoring
  notification should remain visible while monitoring is enabled.
- This is an unofficial personal client and is not affiliated with OpenCode or Anomaly.
