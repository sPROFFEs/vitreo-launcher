# Screenshot provenance

[Gallery](README.md) · [Documentation](../README.md)

## Capture source

- App: signed production **Vitreo 0.10.14**, version code 1014.
- Package: dev.vitreo.launcher, not Debug or Preview.
- [Published APK](https://github.com/sPROFFEs/vitreo-launcher/releases/tag/v0.10.14).
- App source: [b1ca8b19b11dda101b7d29aa9ef1d098623b02e0](https://github.com/sPROFFEs/vitreo-launcher/tree/b1ca8b19b11dda101b7d29aa9ef1d098623b02e0).
- APK SHA-256: `d1f91967fb086aefc8dbe6225c96ea6cf709dcc9fe40f08b9c8116358c50431d`.
- Capture date: 2026-11-09. Each image has its own UTC timestamp.
- Environment: disposable AOSP Android 15 / API 35 x86_64 emulator,
  1080 × 2400, density 420, gesture navigation and English locale.

[captures.json](captures.json) records the installed APK hash/version, Android
build fingerprint, source PNG digest, full-size/thumbnail digests, dimensions,
scene and configuration notes for every file. The capture helper checks the
focused package and reads SHA-256 from its installed base APK, rather than
assuming the source checkout identifies the binary.

These captures are a local documentation session. The release's successful
[CI run](https://github.com/sPROFFEs/vitreo-launcher/actions/runs/42595767548) and
build-info.json provide separate API 31/35 test evidence. No physical Pixel or
GrapheneOS capture/performance claim is made here.

## Scene configuration

The ordinary **Import Layout** UI applied these public example profiles:

| Profile | Configuration |
| --- | --- |
| [iOS 26](profiles/ios26.json) | Blue Home, Orchid lock image, 72 dp icons, custom bar, example folder and Browser override |
| [iOS 16](profiles/ios16.json) | Same arrangement with Orchid and Classic material |
| [Lucy](profiles/cyber-lucy.json) | Moonlight, Lucy palette, Immersive/modular HUD and matching lock image |
| [David](profiles/cyber-david.json) | Afterimage with yellow/green accents and Immersive HUD |
| [Quiet HUD](profiles/cyber-quiet.json) | Kiroshi colors, Neon Rider, ambient/modular effects off |

The profiles contain only public AOSP component IDs and appearance settings.
They are reproducibility aids for this emulator, not a recommended personal
layout. They do not bind Android widgets or activate the custom Lock Screen,
notification access or the island. Retired Debug/Preview and test fixtures were
removed from this disposable emulator before capture. System navigation and the
app's ordinary settings are real configuration choices, not drawn into the images.

Lock images use unlocked preview/editor flows because the real secure surface
blocks screenshots. No live notification, media session, person-depth result or
personal account content is fabricated. Wallpaper art belongs to the bundled
[stock](../artwork/CYBERPUNK_WALLPAPERS.md) and
[Edgerunners](../artwork/EDGERUNNERS_WALLPAPERS.md) collection.

## Image pipeline and refresh

Full-size WebP files are **lossless encodings** of native adb PNG captures. The
helper decodes them again and checks pixel equality. Thumbnails are resized to
360 pixels wide and encoded at quality 90 for a lighter README. There is no crop,
retouching, added phone frame, AI interface generation or UI compositing.

Install the chosen release on a disposable emulator, select Vitreo as default
Home, import the matching profile through Layout & Backup, then navigate the
actual app. Disable Android touch/pointer-location visualization. Use a fresh
output directory for a new APK so versions cannot be mixed:

With Python's venv support and adb on PATH:

```sh
python3 -m venv out/screenshots-venv
out/screenshots-venv/bin/python -m pip install Pillow
out/screenshots-venv/bin/python scripts/capture_screenshots.py \
  --serial emulator-5554 --name ios26-controls --theme ios26 \
  --screen 'Control Center' --expect-text 'Not Playing' \
  --notes 'Describe the actual selected profile/state' --output out/screenshots-next
```

The helper only captures; it does not install, reset, configure themes, grant
permissions or stage notification/media content. Review every full-size result,
replace the gallery as a set, update this provenance and its links, then run:

```sh
python3 scripts/check_docs.py
```

Old 0.5.0 gallery images were replaced. Their original files/provenance remain
available in Git history rather than being mixed into the current gallery.
