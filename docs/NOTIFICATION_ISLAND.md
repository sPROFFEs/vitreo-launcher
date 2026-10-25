# Notification Dynamic Island

**Settings → Dynamic Island** enables a temporary alert above other apps. It is
independent of Notification Center, Control Center and the custom status bar.
Activation and placement are device-local and never imported from a Home backup.

1. Enable notification island.
2. Explicitly authorize **Display over other apps** and **Android notification access**.
3. Use **Preview island**, adjust placement, and send a new notification from another app.

The compact black capsule uses the actual application's icon on the left and a
bell on the right, keeping its central camera region empty. Cyberpunk uses HUD
colors and a squared card. Tap the capsule, handle or interaction area to expand.
The expanded card shows the current title/body, Open and Dismiss where available.
Outside taps and Back collapse without passing the same tap to the app beneath.
Open rechecks the live notification and native keyguard; Dismiss uses Android's
notification listener. Reading time respects Android's accessibility timeout.
Reduce Motion and Battery Saver remove the geometry animation.

## Placement, matching Aoede

All twelve [Aoede](https://github.com/sPROFFEs/Aoede) layout controls are available,
with one-unit buttons as well as sliders:

| Element | Width | Height | Top | Horizontal |
| --- | --- | --- | --- | --- |
| Capsule | 112–320 dp | 28–72 dp | 0–240 dp | −100–100% |
| Handle | 16–128 dp | 0–32 dp; zero hides | Auto or 0–320 dp | −100–100% |
| Touch area | 48–320 dp | 48–160 dp | Auto or 0–320 dp, clamped below camera/status insets | −100–100% |

Top is measured from the display edge, not from the bottom of Android's bar.
The capsule may surround the camera; the touch area remains accessible below it.
The window encloses the capsule, handle and touch area, so placing them far apart
also enlarges the region that receives taps. Preview placement on the actual
phone. Reset restores geometry without changing activation or permissions.

The implementation was studied against user-owned Aoede commit
`f6581df2f0fd6ab036773b9c14d82124ddda4d58` (`MusicIsland.kt`). Vitreo uses its own
native views and the existing notification-listener lifecycle. No additional
foreground service, usage-history access or accessibility service is added.
Visual guidance: [Apple Live Activities](https://developer.apple.com/design/human-interface-guidelines/live-activities).
Showing ordinary Android notifications here is a Vitreo feature; iOS ordinarily
uses notification banners for these alerts.

## Coexistence with music islands

An ongoing Aoede player notification (`com.aoede.twa`) or an active media-style
notification is a conservative hint that a music island may be present. Choose:

- **Move below the other island**: reserve the configured top area. If it will not
  fit, Vitreo yields instead of overlapping.
- **Pause notification island**: suppress incoming island alerts while the hint
  is present. They remain available through Android and Notification Center.
- **Use independent placement**: your coordinates take precedence.

Set **Other island · bottom edge** to include Aoede's capsule, handle and touch
area. **Always reserve space** supports other overlay apps without media alerts.
Android does not expose other applications' overlay windows or their geometry to
normal apps. A media notification is not proof that an island is enabled. This
is why the reserved area and policy remain explicit user controls. An obscured
Vitreo touch point yields when Android reports coverage there; partial coverage
of another part of the window can also describe the native status bar. This is
an additional safeguard, not universal overlay detection.

## Privacy and lifecycle

Only new, recent alerts from the current Android user trigger the island. Existing
inbox snapshots, duplicate updates, grouped summaries, secret app/channel alerts,
Vitreo's own notifications and ongoing/media notifications are excluded. A new
alert replaces the current transient card; no persistent notification history is
created. Dismissing the card by timeout does not clear the Android notification.

The overlay is removed on screen-off, native keyguard, disabling, permission
revocation or listener disconnection. It never wakes the display or appears over
a locked device. Unlocked, visible island contents follow normal Android screenshot behavior; the secure Lock Screen activity retains its separate capture protection. Notification contents and app-icon bitmaps stay in bounded memory,
are excluded from backups and diagnostics, and never leave the device. Android's
banners and Quick Settings remain controlled by Android. Protected screens (including some Android Settings and permission dialogs) may deliberately hide application overlays; Vitreo respects this [Android window protection](https://developer.android.com/reference/android/view/Window#setHideOverlayWindows(boolean)).
