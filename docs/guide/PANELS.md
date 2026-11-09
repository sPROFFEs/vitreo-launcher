# Control Center, notifications and status bars

[Documentation](../README.md) · [Notification island](NOTIFICATION_ISLAND.md) · [Privacy](../../PRIVACY.md)

## Choose your integrations

| Setting | Default | Extra authorization |
| --- | --- | --- |
| Control Center | On | None for the panel; some controls open Android settings |
| Connect media player | Off | Android notification access |
| Notification Center | Off | Android notification access |
| Custom status bar | Off | None; shared between Home and Vitreo's Lock Screen |
| Notification island | Off | Notification access and Display over other apps |

Control Center is configured in its own Settings page. The status bar,
Notification Center and gesture zones are in **Status Bar & Notifications**.
Enabling a switch does not grant Android notification access automatically.

## Gestures

Start a downward pull below Android's status bar: the right 38% opens Control
Center, and the left 62% opens Notification Center when enabled. The adjustable
64–180 dp band has a test diagram in Settings. Disabled panels do not reserve
that zone; Home's ordinary downward gesture opens Search outside panel zones.
Today also has a Control Center button.

A panel follows the finger. A short pull or reversal cancels; a deliberate pull
or flick opens it. Swipe upward from the bottom handle to close, or use Back / Home.
Scrolling the panel body retains its own gesture. Reduce Motion and Battery Saver
use reduced effects.

## Controls and media

| Control | Actual behavior |
| --- | --- |
| Volume | Android's media stream, subject to fixed-volume policy |
| Connected player | Live track/artwork, supported playback actions and seeking |
| Playback without integration | System media keys while Android reports active audio |
| Home brightness | Vitreo's window; Use system brightness restores the system value |
| Flashlight | Public torch API when the device allows it; no image capture |
| Connectivity, Focus and rotation | Android's relevant settings/panels; real state where available |
| Timer, calculator, camera and music | Compatible installed apps, or an unavailable message |
| Appearance and HUD Effects | Vitreo's own theme preferences |

Modern iOS has Favorites, Media and Connectivity pages and a side rail. iOS 16
uses a compact frosted main page, rounded-square controls and Back/header-arrow
navigation from expanded modules. Cyberpunk uses angular controls.

Use **+** or the classic header menu to edit shortcuts: remove, resize or drag to
reorder. **Add a Control** opens the picker; accessible ordering arrows and
**Restore Default Controls** remain available.

Connected media requires explicit Android notification access. Selection follows
the active sessions, preferring a playing session, and unavailable actions stay
disabled. Turning integration off clears media listeners/data. Restricted global
Android settings are opened rather than simulated as working launcher toggles.

## Notification Center

The optional center shows current-user alerts, app icons, expandable text and
supported actions. Tap to open; swipe left or use Clear for clearable notices.
Ongoing notices remain. Group-summary duplicates are excluded; profiles and
sensitive content can be restricted by Android. Message content is not displayed
while the device reports itself locked. Text replies open the source app.

Notification content and media artwork remain in memory, outside layout backups,
diagnostics and network requests. Revoking Android access disconnects the listener
and clears its data. Lock Screen uses separate, redacted summaries; see
[Lock Screen](LOCK_SCREEN.md).

## Android's bar remains available

The custom bar shows time, battery/charging and active network connection. It
does not fabricate cellular signal strength or read SSID/location. Cyberpunk rail
thickness and opacity are adjusted in **Themes & Icons → Status bar appearance**.

The shared switch replaces the bar only within Home and Vitreo's Lock Screen.
Android still owns the physical top edge, transient bar reveal, other apps,
Recents, navigation and authentication. This does not globally replace SystemUI.
For Android floating banners, see the [island guide](NOTIFICATION_ISLAND.md#use-the-island-instead-of-androids-pop-up-banners).
