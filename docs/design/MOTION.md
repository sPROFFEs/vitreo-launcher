# Motion and interaction

[Documentation](../README.md) · [iOS study](IOS.md) · [Validation](../development/TESTING.md)

Vitreo aims for immediate feedback, reversible gestures and continuity between
states. Its spring values are project tuning, not measured iOS constants. UIKit,
SpringBoard and Swift animation libraries do not run inside this Android launcher.

## Shared motion roles

[LauncherMotion](../../app/src/main/java/dev/vitreo/launcher/ui/SurfaceMotion.kt)
defines panel, menu, close, press and folder springs. SurfaceHost retains outgoing
content and one progress value through entry, interruption, reversal and dismissal.
Finger release transfers velocity where supported. Geometry can settle while
opacity and backdrop values remain bounded; hidden activities settle their surfaces.

| Interaction | Continuity rule |
| --- | --- |
| Panels and folders | Keep the originating anchor and outgoing content until the last frame |
| Control Center | Fixed gesture region, shared reveal and bounded slider resistance |
| Dock and page dots | Read fractional pager progress in drawing layers; reserve footer geometry |
| Home app return | Radial foreground arrival into unchanged cells; wallpaper stays on Android's surface |
| Settings / menu navigation | Direction follows page history; a new modal session cannot resurrect an old category |
| Today editing | Stable keys, drag handles and accessible order arrows |

## Home editing

Pickup preserves the finger's grab offset. A floating icon owns its lift/shadow
and landing, while stable-key neighbours animate into logical free slots.
Preview ordering stays local until release; cancellation discards it. Consumed
synthetic releases from Android ACTION_CANCEL are treated as cancellation, not
as a drop. Edit mode permits immediate dragging without another long press.

Folder grouping requires a deliberate hold near the target's center. Insertion
areas permit ordinary reordering. Logical slot hit testing avoids oscillating
against moving icon bounds. Widget rectangles remain reserved, and committing
uses current entries so a concurrent removal/rename is not overwritten.

Adjacent pager pages are prepared with inactive semantics hidden. Empty vertical
scroll interception is removed when rows fit. Horizontal swipes yield to paging;
edge navigation completes even when the current-page index changes midway.

## Glass and render cost

Rapid pointer/progress reads happen in graphics/layout/drawing layers. Wallpaper
textures, icon resources and paths are reused rather than decoded each frame.
Moving lenses observe actual draw-time coordinates, so scrolling or parent
transforms update the sampled wallpaper without a touch to force invalidation.

Clear and regular material share the bounded optical lens. Cached frost handles
Android 12, reduced effects and unsupported hardware; iOS 16 uses uniform diffusion.
Cyberpunk uses its separate angular material. The Immersive clock exists only
while resumed, focused and visibly unobscured, and decorative effects stop with
Battery Saver, Reduce Motion or disabled HUD Effects.

## App return and overlays

Focus loss alone does not arm Home's arrival: Aoede or another focusable overlay
can leave Home resumed underneath. An actual activity stop, relevant HOME intent
or Android Overview handoff arms the transition. Returning icons animate in
layers, without repacking cells or scaling the system wallpaper. Android retains
ownership of task/Recents animations.

## Island windows

The capsule opens horizontally from its own center and closes symmetrically,
independent of offset handles or touch areas. Expanding transfers the visible
scale/opacity into the card morph. Replacing or reversing an alert continues from
its displayed bounds rather than restarting from an old endpoint.

Compact and full-screen surfaces use fixed origins and a submitted-frame handoff.
The retiring window releases input immediately. Lock, permission loss or disable
removes active and retiring surfaces without waiting. Reduced motion, disabled
system animators and Battery Saver skip geometry transitions. Placement controls
and coexistence policies are in the [island guide](../guide/NOTIFICATION_ISLAND.md).

## Lock Screen

The central gesture region stays stationary while clock/accessories follow the
finger. Wake uses a bounded reveal once the backdrop is ready. Wallpaper/crop/dim
are shared with the optional depth foreground, which is clipped to the clock and
fades during swipe. Model inference is a cached still-image operation, not an
animation-frame task. Android owns authentication and its fingerprint UI.

## References and evidence

- [Apple HIG Motion](https://developer.apple.com/design/human-interface-guidelines/motion)
- [Designing Fluid Interfaces, WWDC18](https://developer.apple.com/videos/play/wwdc2018/803/)
- [UIViewPropertyAnimator](https://developer.apple.com/documentation/uikit/uiviewpropertyanimator)
- [Apple design system, WWDC25](https://developer.apple.com/videos/play/wwdc2025/356/)
- [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices)

Emulator pixel/input checks, compositor captures and optimized motion recordings
are evidence for those environments. They are not a physical-device frame-rate
guarantee or exact equivalence to Apple's compositor. Use the
[physical-device matrix](../development/TESTING.md#physical-device-checks) for hardware review.
