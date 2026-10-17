# Vitreo Launcher

An Android home screen inspired by iOS, with glass surfaces, a four-column grid,
App Library and native Android widgets. Android 12 or newer. English by default;
no accounts, ads, analytics, root or accessibility service.

[Download the signed APK](https://github.com/sPROFFEs/vitreo-launcher/releases/latest)
· [Changes](CHANGELOG.md) · [Build](BUILDING.md) · [Privacy](PRIVACY.md)

<p>
  <img src="docs/screenshots/home.png" width="220" alt="Home screen with clock, calendar and glass dock">
  <img src="docs/screenshots/category.png" width="220" alt="Expanded App Library category">
  <img src="docs/screenshots/app-menu.png" width="220" alt="App menu anchored to its icon">
  <img src="docs/screenshots/widget-previews.png" width="220" alt="Widget catalog with provider previews">
</p>

Screenshots from Android emulators. Each CI run also keeps screenshots, a motion
recording and test reports for its exact commit.

## Use Vitreo

Install the release APK and select **Vitreo** as your default Home app in Android
Settings. Keep your system launcher installed so you can switch back at any time.
Updates use the same app ID and signing key; install the newer APK over the old one.
Debug and Preview are separate apps with separate data and test signatures.

- **Home:** swipe between pages. Four app columns, folders and a four-slot glass dock.
- **Today:** swipe right from the first Home page for widgets and recent suggestions.
- **Search:** tap Search or swipe down. Search locally by app name or package.
- **App Library:** swipe past the last Home page. Open a category or use the alphabetical index.
- **Uninstalling:** confirmed removals clear Home, Dock, folders and suggestions automatically. APK updates preserve placement.
- **App actions:** hold an icon for shortcuts, folders, Dock and Edit Home Screen.
- **Settings:** use the gear in Today or in Edit Home Screen. Choose icon size up to
  84 dp, icon artwork/treatment, appearance, wallpaper and Motion & Glass options.
- **Wallpaper:** use a built-in background or a local photo. Pinch, pan, Fit, Fill and
  Reset let you frame it before saving; the original remains available for later edits.

## Widgets on Home and Today

1. Hold an empty Home area, or choose **Edit Home Screen** from an app's menu.
2. Tap **Add Widgets**. Choose **Home Screen** or **Today**, and a size for Home.
3. Find a widget and tap **Add**. Accept Android's binding/configuration dialog if requested.
4. Tap the **…** below a widget to change its size or page, move it between Home
   and Today, or remove it. In edit mode, hold and drag a Home widget; pause at an
   edge to change pages. A trailing page is available during a widget drag.

**Configure Widget** is available when the provider supports reconfiguration.

Apps make room around widget rectangles. Page and size survive app restarts;
existing Today widgets stay in Today after upgrading. Up to eight native widgets
can be added. Large provider layouts are fitted proportionally and clipped inside
rounded native containers. Catalog previews are passive and never bind a widget.
Widget IDs belong to this installation and are excluded from portable backups.

The built-in Clock & Calendar pair can be toggled separately. Native widgets come
from installed Android apps; their content and network use belong to their providers.

## Motion and glass

Panels open from their source and retain their content during dismissal. Dragging
can cancel or complete a close using distance and velocity. Press feedback, icon
editing and segmented controls animate in the drawing layer; Home cells spring
into their new positions when widget space changes. The design references and
implementation choices are documented in [Motion](docs/MOTION.md).

Android 13+ uses a custom AGSL effect over Vitreo's own wallpaper. Android 12 uses
a frosted fallback. **Battery Saver and Reduce Motion limit the effect**; Reduce
Transparency uses opaque panels. Settings explains which mode is active. A textured
wallpaper makes refraction easier to see. Vitreo does not use UIKit or capture other apps.

Android controls cross-app transitions, Recents, notifications, the keyboard and
lock screen. Vitreo coordinates its desktop and passes the tapped icon's bounds
to the system; it cannot replace iOS's compositor or Android's system launcher integration.

## Development

With JDK 17, Python 3.10+ and Android SDK 35 configured:

```sh
python3 scripts/check_project.py
python3 -m unittest discover -s tests -v
bash gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:lintPreview :app:assemblePreview
```

Preview APK: `app/build/outputs/apk/preview/app-preview.apk`.
See [BUILDING.md](BUILDING.md) for pinned tools, emulator tests and release setup;
[Architecture](docs/ARCHITECTURE.md) for storage and layout;
[Testing](docs/TESTING.md) for physical-device checks.

A push to `main` runs source checks, API 31/35 device tests, JVM tests and lint,
then builds and verifies the signed, optimized release. Numeric versions publish
as normal releases. Published tags and assets are never overwritten: bump
`version.properties` with `python3 scripts/bump_version.py X.Y.Z` before the next
release. Signing setup and update continuity are in [SIGNING.md](docs/SIGNING.md).

## Troubleshooting

If an app is missing, use **Settings → Refresh apps** and search by name or package.
The catalog covers the current Android user. Work profiles, Secure Folder and
Private Space are not combined into this catalog.

For a bug report, include the device, Android version, Vitreo version, exact steps
and **Settings → About Vitreo → Save diagnostics**. Review the exported file before
sharing it. See [Security](SECURITY.md) for private reports.

## License and credits

Vitreo is MIT licensed. Selected geometry, motion roles and menu placement adapt
[Folio](https://github.com/McCal-Codes/folio), with its MIT notice retained.
Twenty-two bundled app images come from [SVG Stack](https://svgstack.com/collections/apple-ios-icon-set)
under CC BY 4.0; Contacts uses original address-book artwork. Clock and Calendar
show live local time/date. Other apps retain their own icons. All source URLs,
hashes and notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and in
the APK's Open source licenses screen. Apple/iOS are design references; Vitreo is
an independent Android project.
