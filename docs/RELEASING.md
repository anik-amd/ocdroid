# Releasing

OCDroid publishes normal, versioned GitHub releases. Each release contains `OCDroid.apk` and
`SHA256SUMS`.

## Versioning

- `0.x` releases are the current WebView shell line.
- Use a minor bump for user-visible features (for example `0.3.0`).
- Use a patch bump for fixes (`0.3.1`).
- Bump `versionCode` by one and `versionName` to the release version in
  `app/build.gradle.kts`.
- After a release is published, bump the version before merging further changes. A push with the
  same `versionName` overwrites that release's assets.

## Checklist

1. Update `versionCode` and `versionName`.
2. Add `release-notes/OCDroid_<version>.md`.
3. Update `CHANGELOG.md` and `KNOWN_ISSUES.md` if needed.
4. Run the JVM tests locally if possible:
   ```bash
   ./gradlew :app:testDebugUnitTest --stacktrace
   ```
5. Open a PR. CI runs tests and a debug build.
6. Merge to `main`. The release job runs the JVM tests again, mirrors the optional web client,
   signs the APK, writes `SHA256SUMS`, and publishes the GitHub release.
7. Verify the release page has both files and that the checksum matches the APK.
8. Smoke-test on a physical device when possible.

## Repository secrets

| Secret | Required | Purpose |
| --- | --- | --- |
| `KEYSTORE_BASE64` | yes | base64-encoded release keystore |
| `KEYSTORE_ALIAS` | yes | key alias |
| `KEYSTORE_PASSWORD` | yes | keystore/key password |
| `OC_SERVER_URL` | recommended | server origin used to mirror the official web client |
| `OC_SERVER_USERNAME` | optional | Basic-auth username; defaults to `opencode` |
| `OC_SERVER_PASSWORD` | optional | Basic-auth password/token for the mirror request |

`OC_SERVER_URL` must be a secret, not a committed variable. It is part of the maintainer's
private infrastructure. The mirror script never logs it. If it is missing, the release still
builds but does not bundle the web UI.

Never print a secret in a workflow step, and never upload the keystore. The workflow removes
`app/keystore` at the end even when a step fails.

### Enable the bundled web UI

Run these from a machine that can reach the OpenCode server. The interactive prompt keeps the
value out of shell history:

```bash
gh secret set OC_SERVER_URL --repo anik-amd/ocdroid
# paste the server root, for example:
# https://opencode.<tunnel-id>.opentunnel.xyz

# Only if the server uses Basic auth:
gh secret set OC_SERVER_USERNAME --repo anik-amd/ocdroid
gh secret set OC_SERVER_PASSWORD --repo anik-amd/ocdroid
```

Use the origin only. Do not paste a one-time `/auth/connect/...` pairing link and do not put
credentials in `OC_SERVER_URL`. Verify the names, not the values:

```bash
gh secret list --repo anik-amd/ocdroid
```

Then bump `versionCode` / `versionName` and push. Do not re-run the same version just to add the
bundle; the next release should point at the new source. If a release run failed after the
version bump and you need to retry the same commit:

```bash
gh workflow run build.yml --repo anik-amd/ocdroid --ref main
```

The release log should show that the mirror ran without the `OC_SERVER_URL is not configured`
warning. A quick APK check:

```bash
unzip -l OCDroid.apk | grep 'assets/web/index.html'
```

### Rotate an exposed OpenTunnel URL

OCDroid does not own the tunnel. It is created by OpenCode remote access and its private key
lives in the OpenTunnel profile on the machine that created it. Do this on that machine.

1. Sign out existing sessions by changing the OpenCode password:

   ```bash
   opencode service set password 'a-new-long-password'
   opencode service start
   ```

2. Disconnect remote access:

   ```bash
   opencode service set remote false
   ```

3. Delete the old tunnel. This is destructive: it removes the hostname for good and invalidates
   every route that shares the default tunnel.

   ```bash
   opentunnel status
   opentunnel delete --yes
   ```

   If the CLI is missing, install it with `npm install -g opentunnel`, or use the Homebrew/curl
   options from <https://opentunnel.xyz/>. For a non-default profile:

   ```bash
   opentunnel --profile <profile> delete --yes
   ```

   If `opentunnel status` says there is no profile, the old identity is not on this machine.
   Stop the old OpenCode/opentunnel service on whichever machine ran it. Without the old profile
   you cannot call `DELETE` for the old tunnel, but an offline tunnel cannot serve traffic.

4. Create a fresh tunnel and print a new pairing link:

   ```bash
   opencode pair --remote --yes
   ```

   To let the service keep the new tunnel, leave remote access enabled. The origin is the
   `https://<route>.<new-tunnel-id>.opentunnel.xyz` part, not the `/auth/connect/...` link.

5. Verify the new origin is the one the server reports:

   ```bash
   opencode api get /api/info
   ```

6. Update the repository secret with the new origin:

   ```bash
   gh secret set OC_SERVER_URL --repo anik-amd/ocdroid
   ```

7. Bump the app version and publish a new release so the APK is rebuilt with the new mirror.

Do not rely on `opencode service set remote false` and `true` alone to rotate the hostname:
OpenCode reuses the device's default OpenTunnel profile, so it may reconnect to the same tunnel.
Deleting the tunnel (or the profile) is what forces a new hostname. The route name is not
authentication; keep the OpenCode password enabled.

## Actions policy

- Workflow default permission is `contents: read`.
- Only the release job gets `contents: write`.
- Actions are pinned to commit SHAs.
- Checkout uses `persist-credentials: false`.
- Documentation-only pushes are skipped so they cannot overwrite release artifacts.
- Manual dispatches only release when run from `main`.
- Pull requests run tests and a debug build, never a signed release.

## If a release fails

- **Missing signing secrets** — configure all three keystore secrets.
- **Mirror 401** — set `OC_SERVER_PASSWORD` (and username if not `opencode`).
- **Mirror host unreachable** — clear `OC_SERVER_URL` to build without the bundled UI, or fix the
  server/tunnel secret. The URL is never printed.
- **`OC_SERVER_URL did not return an HTML web client`** — the URL points at an API, login page or
  wrong port. Use the server root that renders the official web client.
- **Wrong release body** — add the missing `release-notes/OCDroid_<version>.md`; the release step
  fails when the file is unmatched.
- **Install fails over an old build** — the old APK was signed with another key. Ask the user
  before uninstalling; data is lost.

## After an exposed secret

If a tunnel URL, token, pairing code or keystore password is exposed:

1. Rotate it immediately. Removing it from the current commit is not enough.
2. Remove it from current files and add a regression check if possible.
3. Decide with the owner whether public git history must be rewritten.
4. Publish a security note only after the secret is no longer useful.

See [`../SECURITY.md`](../SECURITY.md).
