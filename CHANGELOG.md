# Changelog

All notable changes to OCDroid. Older release notes are in [`release-notes/`](release-notes/).

## [0.3.5] - 2026-10-23

### Added

- First-run welcome screen with setup instructions: how to run `opencode pair` on the computer,
  a copy-paste instruction the user can hand to their OpenCode AI agent (so the agent starts the
  web server and prints a pairing QR), a native pairing QR scanner, a paste-a-link / manual
  address + password form, and links to settings and the full help dialog

### Changed

- The welcome screen also replaces the plain error page whenever a saved server cannot be
  reached (e.g. a rotated tunnel): the dead address is shown with Retry, scanning and manual
  entry available instead of a blank screen

## [0.3.4] - 2026-10-23

### Fixed

- Black screen after entering a login code: the bundled web-client mirror only contained
  statically referenced files and was missing all 107 lazy-loaded chunks (the app shell, the
  settings screen, dialogs — including the camera QR scanner). `scripts/fetch-web-assets.mjs`
  now crawls import statements, dynamic imports and `__vite__mapDeps` arrays so every chunk is
  mirrored (383 files instead of 124)
- QR scan on the official login page could not open a camera because the scanner chunk itself
  was never bundled; the complete mirror ships `_assets/scanner-*.js` now

### Changed

- The custom first-run login page is gone. OCDroid is a single WebView: when no server is
  configured it opens the official web client's own "Connect to a server" page from the APK
  (file:// asset), and the hidden SettingsActivity remains the manual fallback for server
  configuration
- After signing in on the bundled login page, the app hands the fresh token to the server copy
  of the web client via `?auth_token=`, which the official client consumes and strips itself
- Reported servers are accepted only after a reachability probe, so a rotated tunnel left over
  in the login page's storage cannot silently take over the app
- Removed the native ZXing scanner, its dependency and the server-discovery UI; camera
  permission is now requested only for the web client's own QR scanner

## [0.3.3] - 2026-10-23

### Fixed

- Login no longer ends on a black screen: OCDroid no longer reloads the page after the web
  client stores its session token
- QR scanning on the official web client's login page can request and use the camera

## [0.3.2] - 2026-10-23

### Added

- Bundled official web client in the release APK from the configured server mirror
- Disconnect action in the persistent monitoring notification; exits the app without clearing
  saved server settings

### Changed

- Persistent monitoring notification now says "Connected with Opencode" and uses the app's
  colored icon
- Expanded notification shows Disconnect and Settings actions

## [0.3.1] - 2026-10-23

### Fixed

- Command-palette button now also sets `margin-left`, keeping spacing on older Android WebView
  builds that do not support `margin-inline-start`

### Changed

- GitHub Actions upgraded to v5 for `checkout`, `setup-java`, `setup-gradle`, and
  `upload-artifact`

## [0.3.0] - 2026-10-23

### Added

- Command-palette button injected into the official titlebar, using the official
  `icon-button-v2` component and theme variables
- Synthetic `Ctrl+K` / `Ctrl+Shift+P` fallback for phones without a hardware keyboard
- JVM tests for origin comparison, asset-path traversal and command-palette invariants
- `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, `CHANGELOG.md`
- `docs/AI_AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/RELEASING.md`
- Issue and pull-request templates, Dependabot configuration
- `SHA256SUMS` in GitHub releases

### Changed

- README rewritten with the independent/non-affiliation disclaimer, why the app exists, install
  and pairing steps, hidden Settings access, command-palette usage and security notes
- MIT license updated to credit OCDroid contributors
- Web client mirroring requires `OC_SERVER_URL`; static-path allowlist, same-origin redirects,
  size caps and no URL logging
- CI split into test, PR debug build and signed release jobs; default read-only permissions;
  Actions pinned to commit SHAs; checkout credentials not persisted
- Stale docs, screenshots and release notes removed

### Fixed

- Removed the hardcoded private tunnel URL from the workflow and mirror script
- LAN discovery no longer broadcasts the server password/token to every host
- Full-origin checks replace host-only checks for bridge, camera, auth, assets and downloads
- Mixed content and third-party cookies disabled
- External schemes restricted to `http`, `https`, `mailto` and `tel`
- Download auth/cookies only attached for the configured server origin
- Bundle path traversal blocked
- Password is no longer persisted in a plaintext fallback preference file
- Notification visibility changed to private on the lock screen
- Notification copy now says it returns to OCDroid instead of implying a session deep link

## [0.2.1] - 2026-10-22

- Made Settings reachable from the launcher shortcut and monitoring notification
- Kept the official token in the encrypted password field
- Added notification test screen and monitoring status

## [0.2.0] - 2026-10-21

- Official OpenCode connect screen and login flow
- QR camera permission passthrough
- Native pairing scanner improvements
- Notification channels with sound and vibration
- Active-session notification suppression

## [0.1.0] - 2026-10-20

- Initial public WebView shell
- Server discovery, QR pairing, encrypted credentials
- Bundled official web assets and background notifications

[0.3.1]: https://github.com/anik-amd/ocdroid/releases/tag/v0.3.1
[0.3.0]: https://github.com/anik-amd/ocdroid/releases/tag/v0.3.0
[0.2.1]: https://github.com/anik-amd/ocdroid/releases/tag/v0.2.1
[0.2.0]: https://github.com/anik-amd/ocdroid/releases/tag/v0.2.0
[0.1.0]: https://github.com/anik-amd/ocdroid/releases/tag/v0.1.0
