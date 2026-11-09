# Licenses and attribution

Vitreo's own code is MIT licensed, Copyright (c) 2026 Vitreo contributors; see
[LICENSE](LICENSE). The launcher identity, original vectors, procedural backgrounds
and glass.agsl are project assets. There is no affiliation with Apple, GrapheneOS,
SVG Stack, Folio or CD PROJEKT RED.

## App artwork and fonts

| Material | License and attribution | Provenance |
| --- | --- | --- |
| 22 SVG Stack Apple IOS Icon Set WebP images | CC BY 4.0, SVG Stack | [Manifest](third_party/svgstack/assets.json), [provenance](third_party/svgstack/PROVENANCE.md) |
| 23 Arcticons app-family WebP glyphs | CC BY-SA 4.0, Arcticons contributors | [Manifest](third_party/arcticons/assets.json), [provenance](third_party/arcticons/PROVENANCE.md) |
| 13 CYBERCORE CSS control glyphs | MIT, Copyright (c) 2026 sebyx07 | [Provenance](third_party/cybercore/PROVENANCE.md) |
| Rajdhani Medium/Semibold | SIL OFL 1.1, Copyright (c) 2014 Indian Type Foundry | [Manifest](third_party/fonts/assets.json) |
| Contacts address-book vector | Vitreo MIT artwork | [Original vector](app/src/main/res/drawable/ios_contacts.xml) |

SVG Stack's [collection](https://svgstack.com/collections/apple-ios-icon-set) and
[license policy](https://svgstack.com/page/license) are the original sources.
Bundled images retain their bytes; runtime masks, tint and live clock/calendar
layers are launcher treatments. Arcticons artwork adaptations retain CC BY-SA;
no code from its GPL Android application is copied. CYBERCORE SVG geometry was
converted to Android vectors with its notices retained.

Full texts are in [third_party/licenses](third_party/licenses) and the corresponding APK license assets.
Per-resource manifests retain source revisions and SHA-256. External icon packs
are read from the user's installation and are not redistributed by Vitreo.

## Adapted Folio material

MIT, Copyright (c) 2026 Duo Launcher contributors. Retain
[third_party/folio/LICENSE](third_party/folio/LICENSE), the attribution headers in
FolioGeometry.kt, SurfaceMotion.kt and AppContextMenu.kt, and
[Folio provenance](third_party/folio/PROVENANCE.md) when redistributing.
That record documents the upstream reference and adaptation boundaries.

## Wallpapers

Neon Rider and Digital Dusk are original imagegen illustrations made for Vitreo,
distributed under the project's MIT license. Neural Pearl, Neural Carbon and
Neural Crimson were supplied by the maintainer for inclusion and retain their
original bytes. [Stock provenance and prompts](docs/artwork/CYBERPUNK_WALLPAPERS.md)
and [hashes](docs/artwork/wallpapers.sha256) record these files.

David, Lucy, Rebecca and Kiwi are original fan-inspired illustrations generated
for Vitreo and encoded to WebP under MIT for the new artwork files. Character
names and Cyberpunk / Edgerunners belong to their holders; these are not official
promotional images or extracted game/series assets. See
[Edgerunners prompts and provenance](docs/artwork/EDGERUNNERS_WALLPAPERS.md).
Referenced MotionBGS videos and IconScout animations are not bundled.

## Experimental depth

DeepLab V3 is included unchanged under Apache 2.0, TensorFlow / MediaPipe Authors.
LiteRT 1.4.1 is the standalone CPU runtime, Apache 2.0, The TensorFlow Authors.
[Model provenance](docs/development/LOCK_SCREEN_DEPTH.md) records the exact source,
normalization, labels and SHA-256. The full Apache text is also bundled in
app/src/main/assets/licenses/Apache-2.0.txt.

MediaPipe's segmentation_input_rotation0.jpg fixture belongs only to the
instrumentation APK, not the published application. Its source/hash are recorded
with the model provenance.

## Application dependencies

AndroidX / Jetpack Compose, Material Components, Kotlin and kotlinx.coroutines
use Apache License 2.0. Sources for reviewing their notices:

- [AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt)
- [Kotlin](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)
- [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)
- [Material Icons](https://github.com/google/material-design-icons/blob/master/LICENSE)

Dependencies are resolved from Google Maven/Maven Central at build time. Inspect
the resolved graph with `bash gradlew :app:dependencies --configuration releaseRuntimeClasspath`.
Packaging merges common META-INF notices instead of discarding them. This document
is not a substitute for reviewing transitive notices.

JUnit, JSON-java and build tools are test/build dependencies; Android supplies
org.json on-device. Third-party Actions are SHA-pinned and are not packaged into
the app. No Liquid Touch/Backdrop dependency, commercial font, upstream APK or
private signing key is included.
