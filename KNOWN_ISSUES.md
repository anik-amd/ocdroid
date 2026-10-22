# Known issues

OCDroid deliberately keeps the official OpenCode web client unchanged. Most limitations below
come from that choice rather than from a missing native screen.

## Current

- **Command-palette button depends on the official titlebar.** OCDroid injects a small button
  into `#opencode-titlebar-right` and dispatches the official `Ctrl+K` shortcut. If a future
  OpenCode web release removes or renames that slot, the button will disappear. The hardware
  keyboard shortcut `Ctrl+K` / `Ctrl+Shift+P` still works. The button is hidden behind dialogs
  so it cannot open a second palette on top of one. It is not yet verified on a physical device.
- **HTTP is supported for local networks, but not restricted to them.** The app keeps
  `usesCleartextTraffic="true"` so a phone can reach a LAN server at `http://192.168.x.x:4096`.
  Use HTTPS for any server reachable outside your trusted network. See
  [`SECURITY.md`](SECURITY.md).
- **Camera access over plain HTTP may be blocked by WebView.** Browsers only treat secure
  origins as camera-capable. If the official QR scanner cannot start on an HTTP LAN origin,
  connect through an HTTPS tunnel or type the pairing link instead.
- **Notifications are best-effort on aggressive Android skins.** Some OEM battery managers
  stop foreground services even when Android reports them as running. If alerts stop while the
  screen is off, exempt OCDroid from battery optimization and keep background monitoring on.
- **Notification taps return to the app, not a deep-linked conversation.** The official web
  route needs a server-specific encoded directory that is not present in every SSE event. The
  active-conversation check still suppresses alerts for the conversation you are reading.
- **The hidden Settings shortcut is a launcher feature.** Long-press the OCDroid icon and choose
  **OCDroid settings**, or use the **Settings** action on the ongoing monitoring notification.
  Some launchers hide app shortcuts; the notification action remains available while monitoring
  is running.
- **CI has no emulator.** JVM tests and the debug build run in GitHub Actions, but camera,
  notification sound/vibration, WebView rendering and the injected command-palette button still
  need a real-device smoke test before a release is announced.
- **Bundled web UI is optional.** It is generated at build time from the private
  `OC_SERVER_URL` secret. Forks and pull requests that do not configure that secret build a
  live-only WebView; the official UI still loads from the server.
- **The Settings activity is exported.** This is required for the launcher's static shortcut.
  It exposes only OCDroid's own preferences and does not read arbitrary app data.
