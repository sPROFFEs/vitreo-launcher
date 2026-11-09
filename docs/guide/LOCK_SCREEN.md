# Lock Screen and GrapheneOS

[Documentation](../README.md) · [Gallery](../screenshots/README.md) · [Depth implementation](../development/LOCK_SCREEN_DEPTH.md)

Vitreo offers an optional visual surface over Android's existing secure keyguard.
Android / GrapheneOS keeps authentication. The feature is **off by default** and
requires Vitreo to be the default Home app with a secure PIN, password or pattern.

## Preview and customize

Open **Settings → Lock Screen → Full-screen preview** before enabling **Custom
lock screen**. A preview never enables the feature or authenticates the device.
**Customize Lock Screen** edits a draft; Done applies it, Cancel or Back discards it.

- Follow Home, iOS or Cyberpunk independently of the Home theme.
- Choose six clock fonts, size and color, date, Glass / Solid and vertical height.
  The clock follows Android's 12/24-hour preference and fits the available width.
  Tap its outline to edit, or drag the lower-right resize handle. iOS 16 retains
  a solid clock at its original proportions while preserving modern preferences.
- Follow Home's photo/crop, or choose **Separate wallpaper from Home** and a
  collection image, with independent dimming.
- Show battery, next alarm and optional notification summaries. Cyberpunk also
  offers an Immersive HUD; motion respects Reduce Motion and Battery Saver.

The native Android status bar remains the default. **Settings → Status Bar &
Notifications → Custom status bar** enables the same custom bar on Home and
Vitreo's Lock Screen. Camera and system insets remain reserved.

## Experimental depth

**Clock → Depth effect · Experimental**, also available in the editor's Wallpaper
panel, is off by default. A bundled model detects people locally and can place
part of a subject in front of the clock. No account, Google Play services, model
download or photo upload is needed.

Use a portrait with a clear silhouette near the clock. Uncertain masks, no
subject, no clock overlap or excessive coverage keep the ordinary wallpaper.
Landscapes, dark images, fine hair and anime artwork may not produce a usable
cutout. This is a two-layer treatment, not a generated 3D spatial scene.
Status icons, notifications and unlock controls remain outside the occlusion area.
Turning it off restores the normal surface immediately.

## Unlock and notification privacy

Swipe upward in the central clock/accessory area, rather than Android's bottom
navigation edge, or tap **Use PIN or password**. Android decides which
credentials are available. Cancelling leaves the device locked; success removes
Vitreo's separate surface task and returns to the previous app.

**Fingerprint shortcut** is optional and hidden by default. **System unlock**
hands the screen to Android's real keyguard and fingerprint/credential interface.
Vitreo does not read fingerprints, skin the sensor UI or infer which factor
unlocked the phone. A synchronized fingerprint-read animation is not implemented.
System / Emergency, the top lock button and Back also expose the native keyguard.
Camera uses Android's secure-camera intent.

With Android notification access, lock cards show real app icons, names, times
and counts. Message titles, bodies, artwork and actions stay hidden, and secret
notifications are excluded. Opening a card requires Android authentication.
This is independent of Home Notification Center.

## Idle, wake and platform limits

After 5, 10, 15 or 30 seconds idle, Vitreo hands back to Android so its display
policy can turn the screen off normally. There is no wake lock or keep-screen-on
flag. The activity is excluded from Recents and real use blocks screenshots;
the unlocked preview permits the documentation captures.

Vitreo prepares its surface when the display turns off with secure keyguard
active. A quick-wake recovery is bounded to a single 600 ms window. Android's
native screen remains available after reboot, process death, delayed locking,
user changes, calls or another activity allowed above keyguard. There are no
late retries that force the custom screen over an ongoing interaction.

GrapheneOS owns strong authentication, lockdown, secondary biometric PIN, duress
credentials, profiles and auto reboot. This feature does not change those policies.
Its activation is local to the device and is never enabled by importing a backup.
Emulator tests cover Android PIN/authentication flows; physical Pixel / GrapheneOS
biometric behavior still needs [device testing](../development/TESTING.md#physical-device-checks).
