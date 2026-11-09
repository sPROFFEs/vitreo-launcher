# Testing and validation

[Documentation](../README.md) · [Build](../../BUILDING.md) · [Releasing](RELEASING.md)

## Local checks

```sh
python3 scripts/check_project.py
python3 scripts/check_docs.py
python3 -m unittest discover -s tests -q
bash gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assemblePreview :uninstallFixture:assembleDebug
```

Source/Python checks do not compile Android. JVM tests do not establish native
widget, shader, window or keyguard behavior. The optional Kotlin CLI core script
and syntax script are additional tools, not replacements for the Gradle/native
checks. Test counts grow; read the executed JUnit and release build-info.json
rather than treating a historic count as the current requirement.

## Emulator matrix

The reusable device-smoke workflow runs API 31 and 35. It can be dispatched
without publishing. The signed release workflow supplies the same prebuilt APKs
to both emulators; no Gradle build is repeated inside them.

| Phase | Executed target and purpose |
| --- | --- |
| Application instrumentation | Debug: real widgets, catalog, UI, gestures, pixels, island, notifications, model inference and update safety |
| Optimized smoke | Preview: start, navigate, open/return from apps, record motion and inspect frame statistics |
| System Home | Three isolated tests for the real default-HOME task, app return and foreign overlays |
| Secure lock | Two isolated tests with disposable credentials, Android authentication and cleanup |

Missing/skipped/truncated required results or failures block publication. The
signed Release is checked for identity, permissions, signature and digest; these
phases execute Debug and optimized Preview, not the signed APK itself.

For a **disposable emulator only**, after building the APKs:

```sh
export ANDROID_SERIAL=emulator-5554
export GITHUB_SHA=$(git rev-parse HEAD)
python3 scripts/device_smoke.py 35
```

Use the emulator's actual serial/API. The script rejects physical devices before
installing, clearing data, changing default Home or setting a temporary PIN. It
starts the known stock emulator launcher once after install broadcasts, confirms
the one-time immersive tutorial and requires stable focus. Later product ANRs
and failed assertions are not dismissed or retried. Secure tests restore PIN,
Home role and lock timing. Never use this reset workflow on a personal phone.

## What the regressions cover

- Home packing, large text/icons, widget reservations and page capacity; live
  preview, commit/cancel, folder hover and immediate edit-mode dragging.
- Dock/pager continuity, diagonal swipes, interrupted modal entry/dismissal,
  retained menus, search keyboard recovery and real HOME/app return.
- App install/update/uninstall, unavailable components, aliases, pack replacement
  and malformed pack XML. Only the disposable uninstall fixture is removed.
- Native widget binding/configuration, exact sizing, persistence, movement,
  proportional fitting, catalog padding and RemoteViews clipping.
- AGSL/cached glass refraction, stable centers, moving lens coordinates, contrast
  and absence of an inset lighting contour. Hardware-only cases are API-gated.
- Optional notification/media authorization, transport callbacks, summaries,
  dismissal, revocation, status bars and secure notification opening.
- Island geometry, center opening/closing, interrupted/replaced alerts, compositor
  position, foreign overlays, media coexistence and screen-off cleanup.
- Lock editor drafts/previews, actual PIN cancellation/success, quick wake/idle
  boundaries and previous-app restoration. Real fingerprint behavior needs hardware.
- Bundled CPU person segmentation on AOSP, blank-image fallback, crop/dim alignment,
  clock overlap bounds and a positive rendering/touch-clipping case.
- Stable update parsing, API/direct-HTTPS fallback, retry limits, package/signer/
  hash/version checks; RSS source/cache/image limits and active-page loading.

Full logs, JUnit, screenshots and failure diagnostics are workflow artifacts.
Current release results belong to its exact commit. [Documentation screenshots](../screenshots/PROVENANCE.md)
are separate, curated captures and are not substituted for test evidence.

## Optional live probes

These make real network requests or change only a disposable emulator's test
installation. They are not selected by the normal deterministic JUnit suite.
Install Debug and its instrumentation APK first.

```sh
adb shell am instrument -w -e mode updates-web dev.vitreo.launcher.debug.test/dev.vitreo.launcher.SelfUpdateProbe
adb shell am instrument -w -e mode network dev.vitreo.launcher.debug.test/dev.vitreo.launcher.SelfUpdateProbe
adb shell am instrument -w -e mode news -e market ES -e provider direct -e images true dev.vitreo.launcher.debug.test/dev.vitreo.launcher.SelfUpdateProbe
```

Repeat news with US/FR/DE and optional Google RSS; Google does not supply article
photos. A positive self-update probe also needs `:app:assembleUpdateFixture`,
then `python3 scripts/test_self_update.py`. The fixture uses the Debug identity,
a higher version and native installer flow; replacement intentionally ends the
instrumentation process. It is never a release asset. Remove that newer fixture
before reinstalling an older Debug build.

## Physical device checks

Emulator results do not establish physical GrapheneOS biometric behavior or
60/120 Hz frame rates. Record model, OS/build, Vitreo version/app ID and APK SHA.
Keep the original launcher and Android's secure lock available.

| Area | Check on Pixel / GrapheneOS and other target devices |
| --- | --- |
| Home | Slow/fast drags, cancel, folder hover, both paging directions, Dock exchange and uninstall while away |
| Layout | 60/100 dp icons, large fonts, landscape, gesture/three-button navigation and camera cutouts |
| Widgets | Real providers, configuration, size/page changes, restart and bounded previews |
| Themes | iOS 26/16, Cyberpunk quiet/Immersive, all character palettes, photo dim and pack fallback |
| Motion | App/Overview return, interrupted panels, music-island outside taps, reduced motion and Battery Saver |
| Lock | PIN/passphrase/fingerprint, lockdown/secondary PIN if configured, fast cycles, delayed locking, calls, camera, process death, cold boot and user changes |
| Depth | Clear/uncertain portraits, no subject, too much clock coverage, photo crop, clock height and disabled restoration |
| Notices/media | Access grant/revoke, sensitive summaries, supported actions, ongoing notices and competing players/islands |
| Network/update | Network revoked/restored, DNS/VPN failures, offline RSS, image switch and Android update consent |
| Persistence | Restart, backup/import, missing apps/packs, photos/widget bindings reselected locally |

Use Export Diagnostics in About Vitreo and review it before sharing. Include
reproduction steps and focused logs. Do not publish private notifications,
credentials or keystores. See [Security](../../SECURITY.md).
