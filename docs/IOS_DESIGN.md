# iOS visual and motion study — 0.7.0

Reviewed on 2026-10-21. These projects are references, not a native iOS runtime or
an authoritative specification. Vitreo implements the selected behavior in
Kotlin / Compose and its original AGSL renderer. No SwiftUI, web runtime, Apple
wallpapers, SF Symbols files or private iOS hooks are bundled by this change.

## Reviewed repositories

| Reference / pinned revision | Reviewed material | Decision in Vitreo |
| --- | --- | --- |
| [AnukarOP/iOS-26-clone](https://github.com/AnukarOP/iOS-26-clone/tree/2078483b90ca6684321977a1cb6c68a2165f7648) · Apache-2.0 | `ContentView.swift`, `SystemDesign.swift`, `AppModel.swift`: four-column Home, separate dock, clear material and short springs | Align Home / dock columns, float the dock and preserve app order. Size for Android insets and the user's icon preference instead of copying fixed iPhone offsets. |
| [lukejacksonn/homescreen](https://github.com/lukejacksonn/homescreen/tree/98a812f4f7c33594e791d65092f73b8f54b3c598) · no repository license found | Home grid, App Dock, App Launcher, App Library, Widget Panel, Page Indicator, Popover, Screen Stack and overscroll components | Use as visual / interaction reference only. Preserve context across open / close, keep navigation separate from the app grid, and transition between page feedback and Search. Do not import code, icons or app emulation. |
| [seunghan91/ios26-design-system](https://github.com/seunghan91/ios26-design-system/tree/e7e9249f2831dd7c52a9e04ff67f06fee4caeaad) · MIT | Design tokens, material CSS, Home / Control Center recipes, menu, icon, search, page-control and sheet specs | Consistent text roles, spacing, capsules and a hierarchy between clear chrome and readable text panels. Treat recipes and dimensions as community estimates; some control descriptions differ from the SwiftUI example and actual phone constraints. |
| [alessiorubicini/ControlCenterForSwiftUI](https://github.com/alessiorubicini/ControlCenterForSwiftUI/tree/36aeae416a07bbc02b2f7801e1dea7564eddf5e2) · no repository license found | `CCButton.swift`, `CCVerticalSlider.swift`, example composition and utilities | Original Compose implementation of state feedback, warm slider fill, changing volume symbols, contrast thresholds and bounded elastic overscroll. Keep the touch region stationary. Avoid per-drag-event haptic spam and keep Android as the source of real device state. |
| [archisvaze/liquid-glass](https://github.com/archisvaze/liquid-glass/tree/fbeb99ef5e8dad2cbbc991ffc23e186a28dc01c4) · MIT | CSS material layering and WebGL refraction / highlight model | Refine Vitreo's existing analytic lens, saturated color transmission and rim lighting. Reuse cached wallpaper textures; do not embed WebGL, multi-tap live blur or an idle render loop. |
| [Stealth2115/Ios26-anim](https://github.com/Stealth2115/Ios26-anim/tree/75c5e1c71672d9290b44599d21b56d5fa3476f69) · no repository license found | `Tweak.x`, build notes and README | Context / corner continuity are useful goals. Reject private SpringBoard hooks, overlay injection and its 1.5-second open / close constants. Those are an experimental iOS 16 approximation, not official iOS 26 motion parameters or an Android implementation. |

## Cross-check against Apple

[Materials](https://developer.apple.com/design/human-interface-guidelines/materials)
distinguishes functional Liquid Glass from the content layer. Clear glass suits
rich backgrounds; regular material prioritizes the readability of text. Vitreo
uses clear adaptive chrome for the dock, search and controls, and stronger regular
material for menus and expanded app panels. Native third-party widgets retain
their provider content. Bright backgrounds can use dark foregrounds instead of
painting dark glass beneath dark labels. Accessibility overrides remain available.

[Motion](https://developer.apple.com/design/human-interface-guidelines/motion) and
[Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views)
inform continuous state transitions, restrained spring response and interruption.
The exact Apple rendering / spring implementation is not reproduced. Vitreo keeps
one reveal progress, retains outgoing content until dismissal, inherits velocity
when possible and settles hidden menus when the activity stops. Module and page
feedback read progress during drawing rather than rebuilding the workspace.

[Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)
also emphasizes layout, controls and typography together. Android uses its own
font family with explicit iOS-like roles; no Apple system font is redistributed.
Home still adapts to screen size, font scale, navigation insets and icon size.

## Android implementation

- Home and dock share column centers. Dock height expands for larger user-selected
  icons. Page feedback weights adjacent dots continuously and windows collections
  larger than seven pages instead of losing the active marker.
- App Library derives preview icon sizes from the available square. Expanded
  phone categories use up to three columns, with fewer in narrow windows.
- Clear panels follow foreground contrast; regular panels keep text legible.
  Wallpaper dim is applied consistently to their samples. API 31–32 and reduced
  effects use cached sharp / frosted textures; API 33+ uses hardware AGSL when
  supported. Optical thickness and the rim's width are bounded in
  density-independent units. Control / notification lenses account for the
  shaded backdrop; its full-screen diffusion also respects wallpaper dimming.
- Sampling observes parent movement in the drawing phase. Opening a panel or
  dragging the pager does not decode a wallpaper or run a new bitmap blur.
- iOS controls use white active surfaces with colored symbols, wider phone
  modules, readable media labels, a brief reveal and a gentle press response.
  Sliders use bounded nonlinear resistance and a fixed touch area. Real brightness
  and volume are clamped independently; brightness retains its existing 2% floor.
- Reduce Motion removes geometric stretch / reveal. Reduce Transparency keeps
  opaque surfaces. Battery Saver retains the established reduced-effects path.
  Cyberpunk keeps its separate material, angular controls and appearance options.

## Verification and limits

Unit coverage exercises page windows and bounded, symmetric slider resistance,
including non-finite input. Native tests exercise actual slider deformation,
stationary touch bounds, endpoint values and Reduce Motion. Existing coverage
continues to check category gestures, lifted-icon anchoring, interrupted panel
opening / dismissal, large icons, keyboard recovery and activity lifecycle.

Use real screenshots and motion recording to review the renderer on API 31 and
35. Passing emulator tests is not a physical Pixel frame-rate measurement.
Android / GrapheneOS still owns app windows, Recents, native authentication and
system gestures. Vitreo does not copy SpringBoard transition hooks or insert an
unsynchronized fingerprint scan animation.
