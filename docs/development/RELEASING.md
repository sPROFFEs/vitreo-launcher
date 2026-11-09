# Release process

[Documentation](../README.md) · [Signing](SIGNING.md) · [Testing](TESTING.md)

## Version and publish

`version.properties` is the version source. For an application release:

1. Finish the change and its relevant checks.
2. Run `python3 scripts/bump_version.py X.Y.Z` with the next stable version.
3. Commit and push main. The Release APK workflow builds and verifies the result.
4. Check both emulator jobs and publication, then inspect the public release's
   APK, checksums, signing certificate and build-info.json.

The workflow also accepts a matching vX.Y.Z tag or a manual run on main/the exact
tag. It does not publish from pull requests. A published version cannot be
replaced by another commit: increment the version rather than moving its tag.

Documentation-only maintenance does not require a new APK. With the current
main-push release trigger, a documentation-only commit can use GitHub's
[`[skip ci]` commit directive](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/skip-workflow-runs) after its local documentation checks. Never use that
path for application, signing or release-script changes. Alternatively, work on a
branch and use the normal pull-request checks.

## Pipeline

| Job | Required result |
| --- | --- |
| source-checks | Source/script checks, valid release reference and four signing secrets present |
| build | JVM tests, lint, Debug/test APKs, optimized Preview, signed Release and uninstall fixture |
| device-tests | Same prebuilt APKs on disposable API 31 and API 35 emulators |
| verify | Exact-commit device evidence, package/version/permission checks, signature and SHA-256 |
| publish | Revalidate assets, upload to a draft, verify GitHub upload digests, then publish stable |

Debug application tests, optimized Preview smoke, three real HOME tests and two
secure-keyguard tests run in isolated phases. Missing, skipped, truncated or
failed required evidence blocks publication. See [Testing](TESTING.md) for what
these phases establish and their physical-device limits.

Release APKs are normal releases with prerelease false. Preview APKs remain
workflow artifacts. Publication checks existing tags/assets and never overwrites
a public APK. The updater consumes stable release metadata and verifies the
installed signing identity before offering an update.

## Workflows and artifacts

- release.yml runs the signed pipeline on main/version tags/manual dispatch.
- ci.yml checks pull requests without private secrets and provides an optimized
  Preview artifact; it can also be dispatched manually.
- device-smoke.yml is reusable and manually runnable for emulator validation.

Both emulator jobs download the same build outputs. No second Gradle build runs
inside the emulator. Intermediate artifacts are keyed by run and commit so a
failed job can be rerun using successful prior outputs from the same execution.
This does not permit replacing published release assets.

Gradle caches are writable only for main; other references read shared caches.
Actions are pinned to commit SHAs. Build/verify do not write repository content;
publish receives contents-write permission and no signing key. The private key is
removed after the jobs that need it and is never sent to the emulators.

## Diagnose failures

| Failure | Check |
| --- | --- |
| Missing signing secrets | Exact names in [Signing](SIGNING.md); no secret values in logs |
| Wrong password or alias | Existing keystore type, alias and credentials; preserve the production key |
| Public tag points elsewhere | Bump the version; do not move the tag |
| Existing incomplete draft | Review that draft and its exact commit before removing only the unfinished draft |
| Publishing 403 | Actions contents permission, organization policy and tag rules |
| Native test failure | Saved JUnit, screenshots and logcat for the failing API/commit; diagnose before rerunning |
| Kotlin/lint/build error | Full failing task output and source SHA; syntax-only checks are insufficient |

Each public release includes build-info.json with identity, source commit,
certificate, APK digest and executed device phases, plus SHA256SUMS and mapping.txt.
Emulators execute Debug and optimized Preview; the signed Release is checked
statically. Builds are not claimed bit-for-bit reproducible, and there is no
complete transitive-dependency SBOM or security audit.
