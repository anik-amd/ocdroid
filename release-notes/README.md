# Release notes

Each normal release has a matching file named `OCDroid_<version>.md`. The CI workflow uses
that file as the GitHub release body.

- Current `0.x` releases: the WebView shell in this repository.
- Historical OC Remote fork notes are intentionally not kept in this repository.

When preparing a release, update `versionCode` and `versionName` in `app/build.gradle.kts`,
add the new note file here, and follow [`../docs/RELEASING.md`](../docs/RELEASING.md).
