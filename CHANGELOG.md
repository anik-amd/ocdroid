# Changelog

All notable changes to OCDroid. Older release notes are in [`release-notes/`](release-notes/).

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
- MIT license now preserves the original OC Remote copyright and adds OCDroid contributors
- Web client mirroring requires `OC_SERVER_URL`; static-path allowlist, same-origin redirects,
  size caps and no URL logging
- CI split into test, PR debug build and signed release jobs; default read-only permissions;
  Actions pinned to commit SHAs; checkout credentials not persisted
- Stale OC Remote docs, screenshots and release notes removed

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

[0.3.0]: https://github.com/anik-amd/ocdroid/releases/tag/v0.3.0
[0.2.1]: https://github.com/anik-amd/ocdroid/releases/tag/v0.2.1
[0.2.0]: https://github.com/anik-amd/ocdroid/releases/tag/v0.2.0
[0.1.0]: https://github.com/anik-amd/ocdroid/releases/tag/v0.1.0
