# Control Center and Home integration

Settings → Control Center provides switches for Control Center and active-player integration.
Settings → Status Bar & Notifications contains the shared Home/Lock Screen status-bar switch, Notification Center and gesture zones.
The three integration options default to **off**, including when upgrading old
layouts. Turning off Control Center removes its gesture and Today button.

**iOS 16 Classic** uses rounded-square controls, a frosted main page, Screen
Mirroring and more compact slider corners. Tap a module's expansion control for
media or connectivity; Back or the top-left arrow returns to the main page.
The header's ellipsis opens shortcut editing. **iOS 26** retains the vertical
Favorites/Media/Connectivity rail, circular controls and Liquid Glass. Its compact
network cluster opens the full connectivity page as one accessible control.

## Gestures and system boundaries

Start a downward pull in the upper-right 38% for Control Center, or the left 62%
for Notification Center when enabled. The sensitive band starts below Android's
status bar (8 dp inside Home in custom-bar mode) and defaults to 112 dp tall.
Settings lets you adjust it from 64–180 dp and test both zones on a diagram.
Outside those zones, Home's downward gesture opens Search. Disabled panels do
not reserve their zones. Horizontal paging and provider-widget gestures retain
priority when a panel pull has not started.

A panel follows the finger after 24 dp of downward travel. Release after 60 dp,
or flick after 24 dp at 700 dp/s, to open. A short slow pull or an upward reversal
cancels. The retained panel springs from its current position; its wallpaper
material and Home blur share the same progress. Swipe up from the bottom 100 dp
to dismiss, or use Close, Back or Home. Reduce Motion uses a short fade; Battery
Saver also reduces effects. No per-frame wallpaper capture or bitmap blur is used.

**Android SystemUI is not replaced globally.** The custom bar uses immersive status
bar handling in Home and Vitreo’s optional Lock Screen only when explicitly enabled. Both use the native Android bar by default. Android still owns the physical top edge: a swipe
from that edge reveals its transient bar. Other apps, Recents,
system navigation and security prompts retain Android's UI. No root or accessibility service is installed. The independently optional [notification island](NOTIFICATION_ISLAND.md) requests overlay access for transient alerts above apps. The independently optional [lock surface](LOCK_SCREEN.md) also retains Android authentication.

The custom bar shows device time, battery percentage/charging and the validated
active network. Wi-Fi indicates a Wi-Fi connection, not measured strength; M
identifies mobile transport. Cellular signal bars are not fabricated. Network
state uses the normal ACCESS_NETWORK_STATE permission; no SSID, location, phone
state or nearby-device permission is requested.

## Controls

| Control | Actual behavior |
| --- | --- |
| Media volume | Reads/writes Android STREAM_MUSIC and respects fixed-volume devices |
| Connected player | Real MediaSession title, artist, bounded artwork, playback state, supported previous/play/pause/next and seeking |
| Playback without connection | System media keys while Android reports active audio; no guessed track metadata |
| Home brightness | Vitreo window only; System restores Android's default |
| Flashlight | CameraManager torch callback confirms actual availability/state; no camera session |
| Connectivity | Opens Android Internet, Bluetooth, airplane and hotspot settings; read-back highlights where available |
| Focus / rotation | Android Do Not Disturb / Display settings |
| Dark Mode / HUD Effects | Changes Vitreo's appearance |
| Timer / alarms / calculator / camera / music | Compatible installed apps, or a message if none is available |
| Battery / cast / NFC / accessibility / display | Android settings |
| Widgets / Vitreo Settings | Corresponding launcher page |

Favorites, Media and Connectivity are vertical pages with a side rail. Tap + to
edit shortcuts: remove, resize to one/two columns, or hold/drag to reorder. Add a
Control opens a searchable picker; accessible arrows and Restore Default remain
available. Android restricts radio toggles, global brightness and system power
controls for ordinary launchers; Vitreo opens supported system screens.

## Notification access and media

Enabling a feature does **not** grant notification access. Use Android notification
access in Settings, or Connect player in the Media page, and explicitly authorize
Vitreo in Android's system screen. The service is non-exported and protected by
BIND_NOTIFICATION_LISTENER_SERVICE. Android alone binds it after authorization.

Notification Center shows actual current-user alerts, expandable text and simple
notification actions. Tap to open the app, swipe left or use Clear to dismiss
clearable alerts. Ongoing alerts remain; Clear processes only clearable items.
Group-summary duplicates are excluded. Work profiles, private spaces and sensitive
content can be restricted by Android. No notification content is displayed while
the device reports itself locked. Reply actions that need text input are opened
in the source app rather than imitated.

Media selection follows Android's active-session priority, preferring a playing
session. Callbacks update metadata and state; unavailable transport actions are
disabled. The artwork/title opens the selected session's activity when supplied,
with the installed music-app chooser as fallback. Turning either feature off
clears its corresponding in-memory data/listeners. Revoking Android access clears
both and disconnects media. Notification text, artwork and playback details are
never written to layout files, backups, diagnostics or network requests.

## Visual references

Reviewed 2026-10-19:

- [Apple Control Center, iOS 26](https://support.apple.com/guide/iphone/use-and-customize-control-center-iph59095ec58/26/ios/26): grouped geometry, round controls, lenses, side rail and tall sliders.
- [Apple Liquid Glass design](https://www.apple.com/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/): diffuse backdrop, optical edges and restrained motion.
- [Android immersive bars](https://developer.android.com/develop/ui/views/layout/immersive).
- [Android NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService).
- [Android MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager).

Control symbols are original code-native vectors in ControlSymbols.kt, inspired
by those proportions. No SF Symbols font or Apple artwork is bundled. Full-screen
wallpaper diffusion uses the cached frosted wallpaper; the individual glass lenses
retain live edge refraction on supported devices. UIKit is a reference, not an
Android dependency, and the result is an approximation of Apple's proprietary material.
