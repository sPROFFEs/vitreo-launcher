# Control Center

Vitreo's optional panel uses an iOS-inspired grouped layout and local customization.
Open it with a short downward swipe in the upper-right area below Android's status
bar on Home, Today or App Library, or with Today's button. Dismiss upward from the
bottom handle, with Close or Back. Settings → Control Center enables/disables it.

| Control | Actual behavior |
| --- | --- |
| Media volume | Reads and writes Android STREAM_MUSIC; respects fixed-volume devices and rejected changes |
| Playback | Sends previous/play-pause/next to Android's media handler; no track metadata, notification access or player selection |
| Home brightness | Overrides only Vitreo's window; the system button restores -1 (Android default) |
| Flashlight | Public CameraManager torch; callback confirms availability/state; no camera capture |
| Connectivity | Android Internet, Bluetooth, airplane and tethering settings; state highlights only when readable |
| Focus / rotation | Android Do Not Disturb / Display settings |
| Dark Mode / HUD Effects | Changes Vitreo's appearance |
| Timer / alarms / calculator / camera / music | Opens compatible installed apps, or explains that none is available |
| Battery / cast / NFC / accessibility / display | Android settings, with a message if the device has no handler |
| Widgets / Vitreo Settings | Opens the launcher's corresponding page |

Edit adds/removes/reorders the shortcut grid and can restore its defaults.
Preferences survive restarts and travel with portable layout backups. Android
restricts radio, rotation, global brightness and Do Not Disturb changes for ordinary
apps; Vitreo uses public settings screens for these controls. No extra permissions,
privileged service or system replacement is installed.

## References

Reviewed 2026-10-17:

- [Apple: use and customize Control Center (iOS 26)](https://support.apple.com/en-euro/guide/iphone/iph59095ec58/26/ios/26)
- [Apple HIG: Controls](https://developer.apple.com/design/human-interface-guidelines/controls)
- [Android AudioManager](https://developer.android.com/reference/android/media/AudioManager)
- [Android CameraManager](https://developer.android.com/reference/android/hardware/camera2/CameraManager)
- [Android window brightness](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness)

Apple's layout/interaction is the visual reference. UIKit APIs and private system
capabilities are not Android implementation dependencies.


## 0.5.3 composition and customization

The [iOS 18 reference](https://9to5mac.com/2024/09/17/customize-use-control-center-in-ios-18/)
and [Apple's Control Center guide](https://support.apple.com/guide/iphone/use-and-customize-control-center-iph59095ec58/ios)
inform the compact four-column grid, circular controls, tall sliders, + editing,
searchable picker and vertical page rail. Favorites, Media and Connectivity
are functional pages. There is no simulated HomeKit, AirDrop or shutdown control.
The top power symbol opens Android battery/power settings and is labeled accordingly.

Tap + to edit the shortcut grid. Minus removes a shortcut; its corner toggles
one/two columns. Hold and drag to reorder; picker arrows provide an accessible
alternative. The four-column packer preserves order and prevents overflow for every
combination of widths. Add a Control opens the searchable picker. Sizes and order
survive restarts and backups; old backups use the compact defaults. The base
connectivity/media/focus/slider modules retain a stable position. The entire panel
can still be disabled in Settings. Page transitions respect Reduce Motion.
