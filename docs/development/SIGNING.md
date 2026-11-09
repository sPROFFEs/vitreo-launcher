# APK signing and update continuity

[Documentation](../README.md) · [Build](../../BUILDING.md) · [Releasing](RELEASING.md)

## Keep the production identity

| Variant | App ID | Signing identity |
| --- | --- | --- |
| Release | dev.vitreo.launcher | Maintainer's private production key |
| Preview | dev.vitreo.launcher.preview | Development/runner key; continuity between builds is not guaranteed |
| Debug | dev.vitreo.launcher.debug | Local development key |

An ordinary in-place update needs the same app ID, a compatible signing identity
and a higher version code. Vitreo does not implement a key-rotation workflow.
Keep the existing production key; generating another key is not a repair for a
signing failure. Signing keys cannot be recovered from the APK certificate.

Keep an encrypted offline backup of the keystore and its credentials. Do not put
either in Git, issues, release assets, shell history or build properties.

## GitHub Actions secrets

Configure these exact names in **Repository Settings → Secrets and variables → Actions**:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Obtain their values from the existing key and your secret manager. Base64 encodes
the keystore; it does not encrypt it. The source check verifies all four values
are present before emulator jobs. The build materializes the key in RUNNER_TEMP
and removes it in an always-run cleanup step. It is not cached or uploaded.

Code receiving these secrets can use them: review Gradle, scripts and workflow
changes and protect the publishing branch. Pull requests do not receive the key;
the publish job receives verified APK assets rather than the private keystore.

## Local release builds

Supply `VITREO_STORE_FILE`, `VITREO_STORE_PASSWORD`, `VITREO_KEY_ALIAS` and
`VITREO_KEY_PASSWORD` through your secret manager, then run `:app:assembleRelease`.
Packaging fails if the complete private configuration is absent. Use Preview
for an optimized test build that does not need the production key.

## Moving from a development build

Export the layout, install Release, import it and choose the wallpaper again.
Re-add Android widgets, choose Release as default Home and verify the result.
Preview/Debug do not update a Release installation because their IDs differ.
A layout export does not contain photos, widget bindings or Android permissions.

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the signing identity does not match.
Do not uninstall the production app as the first troubleshooting step; preserve
the layout and investigate which APK/key was used. Published APKs and tags are
immutable. See [Android app signing](https://developer.android.com/studio/publish/app-signing).
