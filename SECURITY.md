# Security policy

## Supported versions

| Version | Supported |
| --- | --- |
| 0.3.x | ✅ current |
| 0.2.x | ⚠️ upgrade recommended |

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

Use GitHub's private vulnerability reporting:

<https://github.com/anik-amd/ocdroid/security/advisories/new>

Include:

- OCDroid version and `versionCode`
- Android version and device model
- OpenCode server version and whether it is LAN HTTP or HTTPS
- clear reproduction steps
- relevant logs with tokens, passwords, tunnel hostnames and query strings redacted

You should get an initial response within a few days. Please give us a reasonable window to
ship a fix before public disclosure.

## Threat model

OCDroid is a thin Android shell. It trusts the OpenCode server the user configures, and it
renders that server's official web client in a WebView. It does not run a local model, proxy
API traffic, or send telemetry.

### What we protect

- **Server password / pairing token** — stored with Android Keystore-backed
  `EncryptedSharedPreferences`. If Keystore is unavailable, the password stays in memory for the
  current process and is never written to the plaintext fallback file.
- **Origin boundary** — bridge injection, camera permission, HTTP auth, bundled assets and
  download credentials are scoped to the exact configured origin (scheme, host and port).
- **LAN discovery** — probes are unauthenticated. The password/token is never broadcast to
  every address on the network.
- **Bundled web assets** — the mirror script only fetches known static paths, follows
  same-origin redirects, enforces size caps, and blocks path traversal.
- **Release integrity** — releases include `SHA256SUMS`; verify it before installing.
- **Lock screen** — task notifications use private visibility so session titles are not shown in
  full on a secure lock screen.

### What we do not protect

- **A compromised server.** The official web client is served by your OpenCode server. If that
  server is malicious, it can control the page inside the WebView.
- **A compromised device.** Root, malware with accessibility access, or a malicious keyboard can
  read what you type. Android Keystore does not protect against a fully compromised OS.
- **HTTP on a hostile network.** LAN HTTP is supported for convenience. Anyone on the same
  network may be able to observe or modify traffic. Use HTTPS for remote access and trusted
  Wi-Fi for HTTP.
- **Notification content on an unlocked device.** Notifications are private on the lock screen
  but visible while the device is unlocked.
- **The exported Settings activity.** It is required for the launcher shortcut. It only exposes
  OCDroid's own preferences; it does not expose web content or arbitrary files.

## Secret handling for contributors

Never commit:

- `OC_SERVER_URL`, `OC_SERVER_USERNAME`, `OC_SERVER_PASSWORD`
- tunnel hostnames or pairing links
- `KEYSTORE_BASE64`, keystore files, `signing.properties`
- API keys, tokens, cookies or `.env` files

CI reads these from repository secrets. The mirror script does not print the configured URL.
If a secret is exposed, assume it is compromised: rotate it, remove it from current files, and
decide whether the public git history must be rewritten.

## Release verification

Each release publishes:

- `OCDroid.apk`
- `SHA256SUMS`

Verify before installing:

```bash
sha256sum -c SHA256SUMS
```

On Windows PowerShell:

```powershell
(Get-FileHash .\OCDroid.apk -Algorithm SHA256).Hash
Get-Content .\SHA256SUMS
```

The hashes must match. Install only from the official repository release page.

## Scope

In scope:

- credential leakage
- origin-confusion or bridge exposure
- path traversal in bundled assets
- arbitrary intent or file access from a malicious page
- privilege escalation through exported components
- supply-chain issues in the build workflow

Out of scope:

- attacks that require a rooted or fully compromised device
- denial of service against your own OpenCode server
- HTTP interception on a network the user chose to trust
- vulnerabilities in the official OpenCode web client itself (report those upstream)
