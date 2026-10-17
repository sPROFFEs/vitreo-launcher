# Cyberpunk theme research

Reviewed **2026-10-17**, after publishing [Vitreo 0.5.0](https://github.com/sPROFFEs/vitreo-launcher/releases/tag/v0.5.0).
This research informed the opt-in theme implemented in **0.5.1**. iOS remains the default.
The sections below retain the original proposals; [CYBERPUNK.md](CYBERPUNK.md) documents
the shipped behavior and remaining limits. Physical-device benchmarks remain pending.

## Recommended direction

Build a native Compose theme based on Cyberpunk 2077's HUD and menus: dark
surfaces, red structural lines, cyan information, selective yellow highlights,
cut corners and short mechanical transitions. Use the supplied CSS projects as
references for components and effects, then tune against the game's own UI.

The visual interpretation comes from reviewing CD PROJEKT RED's
[accessibility article and screenshots](https://www.cyberpunk.net/en/news/49591/update-2-1-accessibility-features),
including its [settings screen](https://static.cdprojektred.com/cms.cdprojektred.com/0fd3c79efd5c6b0317b8b82ce72d8831d943eb9bbdf22215.png)
and [in-game HUD](https://static.cdprojektred.com/cms.cdprojektred.com/0fd3c79e3538313fcb6088e4dd76ac39045c48d4336f7dc5.png).
Those images are references, not assets to bundle. A phone needs larger touch
targets and clearer small labels than a desktop game menu.

## What the supplied references contribute

| Reference | Useful material | Integration decision |
| --- | --- | --- |
| [CYBERCORE components](https://sebyx07.github.io/cybercore-css/#/components) / [source](https://github.com/sebyx07/cybercore-css/tree/5b90ffbcc4a1aa84d122eead296fab0cf7ee58ca) | Semantic tokens, panels, controls, scanlines, RGB separation and roughly 150 UI glyphs | Adapt selected SVG geometry and component ideas to Compose. The project is MIT; retain notices for adapted material. Its glyphs cover controls, rather than installed Android apps. |
| [alddesign/cyberpunk-css](https://github.com/alddesign/cyberpunk-css/tree/c6a77b49062c9c876a85c0137cb69c2acc9fd1d8) / [demo](https://alddesign.github.io/cyberpunk-css/demo/) | Angular buttons and tiles, segmented borders, clipped glitch bands | Translate geometry to native shapes and bounded draw effects. Its CSS is MIT. Inspect individual assets separately. |

The first project's [tokens](https://github.com/sebyx07/cybercore-css/blob/5b90ffbcc4a1aa84d122eead296fab0cf7ee58ca/src/scss/core/_variables.scss)
and the second project's [CSS](https://github.com/alddesign/cyberpunk-css/blob/c6a77b49062c9c876a85c0137cb69c2acc9fd1d8/cyberpunk.css)
were read directly. Both include repeating effects; a Home screen should render a
static resting state and animate in response to actions. A WebView or CSS runtime
would add another input/rendering layer without helping this Compose launcher.

## Icon packs found

| Candidate | Verified source facts | Fit for Vitreo |
| --- | --- | --- |
| [Arcticons](https://github.com/Arcticons-Team/Arcticons/blob/90a4343510f35e33421f0a41df5debc29c3604d1/README.md) | Maintainer advertises 15,000+ icons. Artwork is CC BY-SA 4.0; the Android application code is GPL-3.0. | Recommended open app-icon source. Line icons can sit inside original angular HUD frames. Their rounded glyphs are an approximation, so the frame and UI need their own visual treatment. Prefer supporting the installed pack; a curated bundled subset would need attribution, provenance and the artwork's license. |
| [Cyberpunk Icons & KWGT Widget — Aionyxe](https://play.google.com/store/apps/details?id=com.aionyxe.cyberpunk) | Publisher offers a standalone icon pack plus widgets based on Cyberpunk 2077/Edgerunners. Widgets require KWGT and KWGT Pro; icons do not. | Most directly targeted candidate found. Publisher screenshots show red/cyan cut-corner widgets. Evaluate as an optional installed pack. No redistribution grant was found; do not extract its artwork into Vitreo. Exact icon coverage and Vitreo compatibility still require testing the installed APK. |
| [LineX White — JustNewDesigns](https://play.google.com/store/apps/details?id=com.jndapp.linex.white.iconpack) | Publisher supplies a white line pack with thousands of app icons and manual application through compatible launcher settings. | Secondary installed-pack candidate for a restrained HUD style. It is not a reproduction of the game's glyphs; no permission to bundle its artwork was found. |

Arcticons' pinned [appfilter.xml](https://github.com/Arcticons-Team/Arcticons/blob/90a4343510f35e33421f0a41df5debc29c3604d1/app/src/main/res/xml/appfilter.xml)
was parsed locally: **49,548 item mappings**, referencing **14,028 distinct
drawable names**, in approximately 6.5 MB of XML. These are mapping counts, not
the total artwork count. Telegram's `org.telegram.ui.LaunchActivity` maps to
`telegram`; Google Contacts' `PeopleActivity` maps to `google_contacts`. This
confirms those source entries, not runtime coverage of every alias on a phone.

Keep any adapted Arcticons artwork under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), credit contributors
and record changes. Any bundled subset should have its own provenance manifest
and license files. No Arcticons application code is needed for a resource reader.

## Visual specification to prototype

The following colors and dimensions are **Vitreo starting values**, not sampled
game constants. Compare them with the official screens on actual phones.

| Element | Proposed treatment |
| --- | --- |
| Background / panels | `#090D13` / `#141922`; enough opacity to keep labels readable over photos |
| Structure / active selection | Red `#FF4B5C`, with solid fills limited to selected controls |
| Information / focus | Cyan `#63F5F7`; persistent visible focus and selected-state shape |
| Highlights | Yellow `#FCEE0A`, used sparingly for a primary action or status |
| Text | Off-white `#EAF2F2` for body text; restrained red/cyan headings |
| Geometry | 45-degree corner cuts of 8–12 dp, 1–2 dp borders, small side ticks |
| Home / Dock | Existing app grid, angular icon frames and a segmented dark Dock |
| Library / folders | Framed category panels and opaque icon backing; stable layout while menus open |
| Widgets | HUD header/footer around the native host, with the provider's padding and content clipping preserved |

For fonts, prototype **Rajdhani Medium/Semibold** for headings and **Oxanium** for
clock/status numbers, with a system fallback for unsupported scripts. Keep body
text and app labels readable and respect font scaling. Their upstream licenses
are available in [Rajdhani OFL](https://github.com/google/fonts/blob/main/ofl/rajdhani/OFL.txt)
and [Oxanium OFL](https://github.com/google/fonts/blob/main/ofl/oxanium/OFL.txt).

alddesign's repository contains `BlenderProBook.woff2` and `Cyberpunk.otf`.
Its MIT file alone does not establish separate font-embedding rights. Blender is
also distributed by [Binnenland](https://binnenland.ch/typeface/blender).
Use the documented OFL alternatives for the prototype; obtain a verified app
license before considering a different font. Preserve Vitreo's own app identity.

## Motion specification to prototype

These are proposed tuning ranges, not measured timings from the game.

| Interaction | Proposed behavior | Reduced path |
| --- | --- | --- |
| Press | Immediate border/fill feedback, 80–120 ms; launch on the normal click event | Instant state change |
| Menu / folder entry | 160–220 ms translation and clipped panel reveal; content starts together | Short opacity transition |
| Menu exit / Back | 120–180 ms reversal from the visible state; outgoing content remains until settled | Immediate dismissal |
| Tab / category selection | 120–160 ms moving selection line and content transition | Instant selection |
| Confirm / theme preview | Optional 80–140 ms horizontal glitch in decorative bands; bounded cyan/red offsets | No glitch |
| Home page / widget drag | Existing finger tracking and spring velocity continuity | Existing reduced-motion behavior |
| Return from another app | Brief launcher-owned highlight after focus returns | No added effect |

Use `drawWithCache` for paths and static scanline texture, and `graphicsLayer`
for animated transforms/opacity, following Android's
[graphics modifier guidance](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers).
Do not recreate icons, parse XML or remeasure the full grid each frame. Stop
decorative effects when the activity stops. Gesture interruption must inherit
the current position and velocity, retaining the 0.5.0 cancellation fixes.

CD PROJEKT RED itself provides options to remove chromatic aberration, HUD
distortion and decoration in its [accessibility settings](https://www.cyberpunk.net/en/news/49591/update-2-1-accessibility-features).
Expose an effects switch and honor Vitreo's existing Reduce Motion and Battery
Saver paths. Keep glitches away from text entry, app labels and native widgets.
Android controls other apps' windows and Recents; this theme can animate
launcher-owned surfaces and request supported launch transitions.

## Native integration plan

1. Add a separate `themePreset` value to `Appearance` and `StateCodec`, with a
   default preserving existing backups. Keep interface brightness, icon source
   and motion reduction as separate choices. Theme selection must not rearrange
   apps or rebind widgets.
2. Introduce shared color, shape, typography, material and motion tokens alongside
   `VitreoTheme.kt`. Route `GlassPanel.kt`, `IosControls.kt`, `SurfaceMotion.kt`,
   `HomeWorkspace.kt` and `AppContextMenu.kt` through those tokens. The Cyberpunk
   material uses angular dark surfaces; iOS keeps its glass implementation.
3. Add an icon resolver before `EntryIcon` in `LauncherTile.kt`. Precedence:
   explicit per-app override, selected pack's exact component mapping, theme
   built-in icon, then the installed app's original icon. Preserve app identity
   and badging. Never hide an app because its artwork is absent.
4. Discover installed packs through narrowly scoped manifest queries for the
   ADW/Nova theme intents. Read resources with public `PackageManager` APIs.
   Implement static `appfilter.xml` mappings first, checking `res/xml`, `res/raw`
   and assets, then a searchable manual picker. The
   [Lawnchair specification](https://github.com/LawnchairLauncher/docs/blob/main/developers/concepts/icon-packs.md)
   is a primary implementation reference; verify behavior against real packs.
   Android documents [intent-based visibility](https://developer.android.com/training/package-visibility/declaring)
   and [resource access](https://developer.android.com/reference/android/content/pm/PackageManager#getResourcesForApplication(java.lang.String)).
5. Parse pack XML on IO with bounded size/item counts accommodating the measured
   Arcticons file; test that real file before choosing limits. Cache rendered
   artwork by pack version, component, density, requested size and theme.
   Invalidate on pack update/removal without blocking Main. An unknown alias or
   ambiguous package fallback keeps the app's native icon.
6. Add pack and per-app selection to Settings, with previews and a reset action.
   Export references rather than copying external artwork into layout backups.
   If a selected pack is unavailable after import/uninstall, fall back to native
   icons and retain app placements. Native widgets retain provider styling;
   theme only the surrounding chrome.

## Delivery and acceptance checks

Start with tokens and a Settings preview, then Home/Dock/Library/folders, then
pack loading, and finally optional action-triggered effects. Each stage must
preserve the existing iOS path and uninstall/update regressions.

Before shipping, test API 31/35, real Pixel 8 Pro and S21 FE, light/dark photos,
large text, long labels, cancelled gestures, rapid HOME/Back, app return, widget
touch/scroll and process recreation. Pack tests should include exact components,
Telegram aliases, malformed XML, missing drawables, pack replacement/removal and
backup restore without the pack installed.

Measure an optimized profileable build on physical devices, using
[Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
and frame traces for the same scripted interactions before/after. Compare frame
overrun percentiles, allocations and idle rendering with effects enabled and
disabled. The target is no material regression against the current theme; a
60/120 Hz claim requires device measurements. No Cyberpunk APK or physical-device
benchmark was produced during this research.
