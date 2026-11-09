# Troubleshooting

[Documentation](../README.md) · [Get started](GETTING_STARTED.md) · [Device checks](../development/TESTING.md)

## Glass looks flat or animations are reduced

Check Battery Saver and **Settings → Motion & Glass**. Reduce Motion, Reduce
Transparency and the iOS 16 style deliberately change the material. Android 12
uses cached frost; hardware AGSL refraction needs Android 13+ and supported
hardware rendering. Cyberpunk uses its own HUD material.

A quiet or uniform wallpaper gives refraction less detail to bend. Check the
wallpaper dim, theme and effects settings before changing all optical sliders.
Emulator screenshots do not establish a phone's 60/120 Hz performance.

## An app is missing

Use **Settings → Refresh apps**, then search by both name and package. Home only
contains placed shortcuts; App Library lists available launcher activities.
A package update or icon alias can briefly change its activity. Missing artwork
should fall back to the native icon rather than hide the app.

Work profiles, Secure Folder and Private Space are not fully supported. Include
which profile contains the app, its package, Android build and Vitreo version in
an issue. A confirmed uninstall removes shortcuts automatically; updating does
not intentionally remove their arrangement.

## Widgets are missing, cropped or cannot be configured

Check that the provider exposes a Home widget. Add it through **Add Widgets** and
complete Android's binding/configuration step. Available content and sizes belong
to the provider. Reconfiguration appears only when it supports it.

Widgets must be added again after moving a layout to another device. Provider
layouts are proportionally fitted and clipped; report the provider/package, size,
font scale and a screenshot if content still escapes its container.

## News or updates do not load

On GrapheneOS, check Vitreo's **Network** permission, then connection, private DNS
and VPN restrictions. News does not require Google services. Try **Publisher RSS**
and refresh; Google RSS and its fallback are separate options. Images depend on
the source and **Show article images**. Previously cached headlines can remain
readable offline.

Software Update uses API and direct HTTPS metadata paths while retaining APK
verification. Record the displayed error and version; do not disable verification
or share tokens to work around a download failure.

## Panels or the notification island do not appear

Check the feature switch and Android notification access independently. The island
also needs **Display over other apps**, hides on lock/screen-off, and can pause or
move when a media island is active. Use its placement preview and the panel gesture
zone preview. See [panels](PANELS.md) and [island placement](NOTIFICATION_ISLAND.md).

## Android's own lock screen appears

It remains the supported fallback after reboot, process death, delayed locking,
calls or certain quick wake sequences. Keep Vitreo as default Home and configure
secure Android credentials. The fingerprint shortcut opens the real keyguard;
it does not replace GrapheneOS authentication. [Lock Screen](LOCK_SCREEN.md) lists
setup and integration limits.

## An APK will not update the installed app

Check that it is a newer signed release rather than Preview/Debug. Android needs
the same app ID and compatible signing identity. Export the layout before any
uninstall; removing app data also removes the local photo and widget bindings.
See [signing](../development/SIGNING.md).

## Report a reproducible problem

Use **Settings → About Vitreo → Export Diagnostics**. Include device/Android build,
Vitreo version, steps, expected behavior and the affected theme/settings. Review
logs and screenshots before sharing them. Do not post private notifications,
credentials, signing keys or complete personal backups. See [Security](../../SECURITY.md).
