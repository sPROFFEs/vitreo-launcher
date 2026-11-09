# Get started

[Documentation](../README.md) · [Troubleshooting](TROUBLESHOOTING.md)

Vitreo runs on Android 12 and newer. Its interface is in English. No Google
account, Google Play services, root or accessibility service is required.

## Install and choose Home

1. Download the `.apk` asset from the [latest stable release](https://github.com/sPROFFEs/vitreo-launcher/releases/latest).
   The source-code ZIP is not an installable app.
2. Open the APK and allow installation for the downloading app if Android asks.
3. Choose **Vitreo** in Android's default Home-app settings. You can also use
   **Settings → Default launcher** inside Vitreo.

Keep Android's original launcher installed. Change the default Home app in
Android Settings whenever you want to switch back.

## Find your way around

| Action | Result |
| --- | --- |
| Swipe right from the first Home page | Today: widgets and suggestions, or optional news |
| Swipe left past the last Home page | App Library |
| Tap Search or pull down outside a panel gesture zone | Local app search |
| Hold an app | App actions; start moving it to arrange Home |
| Tap the gear in Today or Edit Home Screen | Settings |
| Pull down below Android's status bar on the right | Control Center, when enabled |

Use **Settings → Themes & Icons** for iOS 26, iOS 16 Classic or Cyberpunk.
[Themes & wallpapers](THEMES.md) explains character palettes, motion and icon packs.

## Optional integrations

Control Center starts enabled. Connected media, Notification Center, the custom
status bar, notification island, custom Lock Screen, depth and news are optional.
Enabling a feature does not grant its Android permissions automatically.

- [Panels](PANELS.md): controls, notification access and gesture zones.
- [Island](NOTIFICATION_ISLAND.md): alert overlay and placement.
- [Lock Screen](LOCK_SCREEN.md): preview first; Android keeps authentication.
- [News & updates](NEWS_AND_UPDATES.md): sources and installation consent.

## Keep your layout

Use **Settings → Layout & Backup → Export Layout** before a reset or device change.
Import restores the app arrangement and appearance. Photos, Android widget IDs,
recent-app suggestions and device-local activation/permissions are not transferred.
Choose the photo again and add widgets on the destination device.

Release APK updates preserve data when the app ID and signing identity match.
Debug and Preview have separate app IDs and temporary development signatures;
they do not update a release installation. See [signing](../development/SIGNING.md).
