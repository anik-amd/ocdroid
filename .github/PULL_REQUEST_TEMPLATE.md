## Summary

<!-- What changed, and why? Keep it focused. -->

## Type

- [ ] Bug fix
- [ ] Feature
- [ ] Security hardening
- [ ] Documentation
- [ ] Build / CI
- [ ] Refactor

## Testing

- [ ] `./gradlew :app:testDebugUnitTest --stacktrace`
- [ ] Debug build passes in CI
- [ ] Device-tested (describe below)
- [ ] Not device-tested (explain why)

Device / Android version:

## Security impact

<!-- Does this touch credentials, origins, discovery, downloads, the JS bridge, networking,
     bundled assets or release signing? If yes, explain the boundary. If no, say "none". -->

## UI

<!-- Does this change the official OpenCode web UI? OCDroid must not add a native chat UI.
     If this is the command-palette button, include a screenshot with no secrets. -->

## Checklist

- [ ] No secrets, tokens, real server URLs, keystores or generated `assets/web` files in the diff
- [ ] Documentation / release notes updated if behavior changed
- [ ] Tests added or updated for testable logic
- [ ] Security invariants in CONTRIBUTING.md are preserved
