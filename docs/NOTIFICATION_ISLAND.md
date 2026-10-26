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
The capsule opens horizontally from its centre in 280 ms and closes in 180 ms,
using Aoede's curves. Offset handles and touch areas do not move that centre.
Replacing an alert keeps the current capsule, while expanding/collapsing retains
the card morph. Closing releases input immediately; lock and permission loss
remove both active and retiring windows without waiting for animation.
The card's corners follow its animated bounds in both directions. Expanded alerts
use rounded actions with an emphasized Open button and retain the same compact
placement, touch area and surface handoff when interrupted.

## Use the island instead of Android's pop-up banners

Open **Dynamic Island → Configure Android banners**. Vitreo can open a recent
notification's exact category, or Android's complete app-notification settings.
On Pixel / GrapheneOS, leave the category enabled and turn off **Pop on screen**
(called **Show as pop-up** on some devices). Leave **Alerting** selected if you
want sound/vibration. The notification remains in Android's panel and reaches
Vitreo. Choosing **Silent** also mutes it; disabling the category prevents delivery.
Individual conversations can have separate settings.

Android owns other applications' category settings; an ordinary launcher cannot
change them. Vitreo does not cancel/repost incoming notifications, modify Do Not
Disturb or ask for privileged settings access. See
[Android notification controls](https://support.google.com/android/answer/9079661?hl=en)
and [channel settings](https://developer.android.com/develop/ui/views/notifications/channels).

For all apps at once, **All apps via ADB** shows an optional manual alternative.
From a computer authorized for USB debugging, first record the existing value:

```sh
adb shell settings get global heads_up_notifications_enabled
adb shell settings put global heads_up_notifications_enabled 0
```

Restore the recorded value to undo it (normally `1`). If it was `null`, delete the
setting instead:

```sh
adb shell settings put global heads_up_notifications_enabled 1
# Use this instead if the original value was null:
adb shell settings delete global heads_up_notifications_enabled
```

This is a system-wide Android change and persists if Vitreo is disabled or
uninstalled. It leaves categories and sound/vibration settings intact. Bubbles,
full-screen calls/alarms and apps' own banners are separate mechanisms. Support
can vary by device/version. GrapheneOS's
[heads-up suppressor](https://github.com/GrapheneOS/platform_frameworks_base/blob/00b2ff99f28b9b5862514b0a25c265b51c59a8fb/packages/SystemUI/src/com/android/systemui/statusbar/notification/interruption/CommonVisualInterruptionSuppressors.kt)
observes this setting specifically for `PEEK` interruptions. Vitreo never applies
these commands itself.

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
