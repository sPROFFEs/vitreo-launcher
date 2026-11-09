# Build Vitreo

[Documentation](docs/README.md) · [Testing](docs/development/TESTING.md) · [Releasing](docs/development/RELEASING.md)

## Toolchain

| Component | Project configuration |
| --- | --- |
| JDK | 17 in CI; local builds also verified with 21 |
| Android SDK / build tools | Platform 35 / 35.0.0, plus platform-tools |
| Android Gradle Plugin / Gradle | 8.9.2 / 8.11.1 |
| Kotlin / Compose plugin | 2.1.20 |
| Compose BOM | 2025.04.01 |
| Minimum / target Android API | 31 / 35 |
| Python | 3.10+ for bootstrap and repository checks |

Configure JAVA_HOME and ANDROID_HOME, accept the Android SDK licenses and put
platform-tools on PATH. The first build needs network access for Gradle and Maven
dependencies. Runtime news and updates are independent of build-time downloads.

## Build and verify

```sh
python3 scripts/check_project.py
python3 scripts/check_docs.py
python3 -m unittest discover -s tests -q
bash gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assemblePreview
```

The optimized test APK is `app/build/outputs/apk/preview/app-preview.apk`.
`:app:assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`.
Use Android Studio or the same commands from a configured shell. On Windows,
`gradlew.bat` uses Python from PATH.

| Variant | App ID | Purpose |
| --- | --- | --- |
| Release | dev.vitreo.launcher | Signed production APK; requires the private signing configuration |
| Preview | dev.vitreo.launcher.preview | Optimized, non-debuggable test build with a development signature |
| Debug | dev.vitreo.launcher.debug | Development and instrumentation |

Release and Preview are separate installations. For stable key continuity,
required signing variables and migrating a test layout, see [Signing](docs/development/SIGNING.md).

## Gradle bootstrap

The repository's text bootstrap, `scripts/gradle_bootstrap.py`, uses installed
Gradle 8.11.1 or downloads the official ZIP and verifies its SHA-256 before
extracting it. It is not a complete official wrapper with gradle-wrapper.jar.
Actions supplies the same Gradle version through setup-gradle.

The distribution checksum is
`f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`.
The configured URL/checksum live in `gradle/wrapper/gradle-wrapper.properties`.
Check [Gradle's published checksums](https://gradle.org/release-checksums/) when changing it.

Full device commands and optional probes are in [Testing](docs/development/TESTING.md).
Versioning, signed workflows and publication recovery are in [Releasing](docs/development/RELEASING.md).
