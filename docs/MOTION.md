# Motion in Vitreo

The aim is immediate feedback, continuity and gestures that can change direction.
The springs below are Vitreo tuning choices, not measured iOS timing constants.
UIKit and Swift animation libraries do not run on Android.

## Design references

Reviewed on 2026-10-17:

- [Apple HIG: Motion](https://developer.apple.com/design/human-interface-guidelines/motion):
  purposeful, brief feedback; cancellable movement and accessibility alternatives.
- [Designing Fluid Interfaces, WWDC18](https://developer.apple.com/videos/play/wwdc2018/803/):
  responsive gestures, interruption, velocity and spring continuity.
- [UIViewPropertyAnimator](https://developer.apple.com/documentation/uikit/uiviewpropertyanimator):
  interactive progress, reversing and continuing an in-flight animation.
- [Get to know the new design system, WWDC25](https://developer.apple.com/videos/play/wwdc2025/356/):
  surfaces anchored to their originating action and clear modal hierarchy.
- [Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices):
  defer rapidly changing state reads to layout/drawing and retain stable item keys.

The requested [animation-framework directory](https://awesome-repositories.com/f/mobile-development/ios-animation-frameworks)
was reviewed for discovery. Its entries cover different tasks, including vector
playback and image loading. No extra framework is needed for desktop physics;
Compose's native springs and gesture velocity are used directly.

## Implementation

| Interaction | Implementation |
| --- | --- |
| Panel open / cancel | Spring, damping 0.9, stiffness 380 |
| Context menu | Spring, damping 0.78, stiffness 650; anchored to the source icon |
| Panel close | Critically damped spring, stiffness 700 |
| Drag dismissal | Finger-driven progress, velocity passed into the settling spring; short downward flicks can close, upward reversals cancel |
| Home rearrangement | Stable app/widget keys, animated placement offsets; widget rectangles reserve cells before apps flow around them |
| Settings navigation | Forward/back horizontal direction follows the page history |
| Menu More / Back | Directional content transition with a spring size change |
| Press / edit feedback | Scale and rotation read inside graphicsLayer, avoiding per-frame icon recomposition |
| Segmented controls | Indicator translates in graphicsLayer, without relaying out every option each frame |

Surface geometry retains the small spring overshoot; opacity and backdrop blur
remain bounded. Outgoing content lives until its last frame. A drag stops the
in-flight spring and resumes from the visible value, using the finger's velocity.
Search/Control Center follow a downward dismissal instead of moving against it.
Provider resize options are sent only when the widget's rendered size changes.
Home widgets respect provider resize minima and report their exact dp size with
Android 12's size-list API, as described in [flexible widget layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts).

On activity stop, modal progress settles offscreen so an interrupted frame clock
cannot leave an invisible touch barrier. Battery Saver and Reduce Motion use the
reduced path. No idle refraction animation or polling loop is introduced.

## Verification and limits

Cyberpunk (0.5.1) uses critically damped entry springs (panel stiffness 1000,
menu 1200), restrained folder/category scale and 90 ms press feedback. HUD panels
draw a single 180 ms decorative sweep, with a short displaced line accent.
The effects switch, Reduce Motion and Battery Saver suppress this decoration.
Static background lines have no animation loop. The existing interactive
velocity handoff and content-retention behavior also applies to Cyberpunk.

Returning from Recents or another activity triggers a separate Home arrival:
icons start radially beyond the nearest screen edge and settle into their
unchanged cells with a critically damped spring (stiffness 350). Widget cards
use a smaller vertical reveal. Transforms run in graphics layers and do not
repack the grid. Reduce Motion/Battery Saver settle immediately.

Departure transforms apply to foreground content only. The wallpaper is never
scaled or faded with the workspace. When Home synchronization succeeds, Android
draws the shared wallpaper behind the transparent window, including during
Recents. A bounded crossfade hands the identical local fallback to that system
surface after applying a new background; no wallpaper update occurs per frame
or on an unchanged return. Android still owns its task/card animations.

JVM tests cover packing under different row counts and gesture dismissal choices.
API 31/35 instrumentation covers real native widget binding, movement, resizing,
persistence, clipping and catalog inset; existing regressions cover interrupted
panels, menus, HOME and app return. Optimized Preview smoke tests record a motion
video and `dumpsys gfxinfo` data with Android animations enabled.

Emulator recordings and frame statistics are evidence for that environment, not
a 60/120 fps guarantee on a phone. Physical checks on Pixel/Samsung should include
slow drags, quick reversals, repeated app switches, large text, Battery Saver and
Reduce Motion. Android owns task/Recents animations; a normal HOME activity cannot
reproduce private SpringBoard/Quickstep behavior across other apps' windows.
