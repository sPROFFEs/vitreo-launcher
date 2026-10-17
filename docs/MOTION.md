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

On activity stop, modal progress settles offscreen so an interrupted frame clock
cannot leave an invisible touch barrier. Battery Saver and Reduce Motion use the
reduced path. No idle refraction animation or polling loop is introduced.

## Verification and limits

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
