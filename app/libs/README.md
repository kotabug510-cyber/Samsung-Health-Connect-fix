# Samsung Health Data SDK AAR Directory

Place the official Samsung Health Data SDK AAR downloaded from the Samsung Developer Portal into this directory:

- `samsung-health-data-api-1.1.0.aar` (or `samsung-health-data-1.1.0.aar`)

This directory is intentionally un-ignored in `.gitignore` so the AAR remains tracked in Git for local and GitHub Actions CI builds.

When an official `samsung-health-data*.aar` is present in `app/libs/`, `app/build.gradle.kts` automatically links directly against it and excludes the `compileOnly` stubs module.
