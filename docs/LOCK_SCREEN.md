# Optional lock screen — Pixel / GrapheneOS

Vitreo supplies a configurable visual surface over Android's **existing secure
keyguard**. It does not replace GrapheneOS security or create its own unlock method.
The feature is off by default and requires Vitreo to hold the default HOME role
and an existing Android PIN, password or pattern.

## Setup and customization

Open **Settings → Lock Screen**. Use Full-screen preview before enabling Custom
lock screen. Preview never changes the lock state or turns the feature on.

- Style: follow Home, iOS-inspired, or Cyberpunk independently of Home.
- Clock: six fonts, size 72–132 pt, six colors, optional glass
  lettering and date. It follows Android's 12/24-hour preference, fits its width
  with vertical resizing. The editor can scroll; unlocking uses the central area rather than the system navigation edge.
- Wallpaper: follow Home's photo/crop or choose a separate built-in collection
  item, with independent dimming. No system lock wallpaper is overwritten.
- Accessories: actual battery/charging state and Android's next scheduled alarm.
  These reveal no calendar events or third-party widget data.
- Cyberpunk: Classic by default, optional Immersive rain, scan and HUD motion.
  Motion pauses while not resumed, unfocused, screen off, in Battery Saver or with
  Reduce Motion. The surface never wakes the display or holds a wake lock.
- Notification indicator: optional and generic, using access already enabled in
  Status Bar & Notifications. No sender, app name, message, artwork or action is
  displayed; secret-visibility alerts do not trigger the indicator. Full notification interaction stays behind Android authentication.

Swipe up in the central clock / accessories area or tap **Use PIN or password**.
The bottom navigation edge is reserved for Android gestures. Android
presents whatever authentication its current policy permits. Cancelling leaves
it locked. After successful authentication the separate surface task is removed,
allowing the previously open app to resume. Camera uses only the secure-camera
intent; unavailable camera/torch produces an explicit message. **System / Emergency**,
the top lock button and Back hand control to the native keyguard, including its
emergency calling and medical-information controls.

**Fingerprint shortcut** in Settings → Lock Screen is independent of activation
and hidden by default, including for existing backups. When enabled it shows
**System unlock**: a fingerprint symbol on devices reporting fingerprint hardware,
or a lock otherwise. Tapping it removes Vitreo's surface without dismissing the
secure keyguard. Android / GrapheneOS then displays its real fingerprint or
credential interface according to enrollment, strong-auth and two-factor policy.
Preview only shows an explanation and never opens authentication. Hiding the
shortcut leaves central swipe, PIN / password and the system fallback available.

## Integration boundaries

The default Home exemption permits background activity starts in AOSP and the
GrapheneOS framework. Vitreo listens while locally enabled and first prepares
immediately at screen-off, checking briefly for a locked, secure keyguard.
Presentation is restricted to the foreground Android user and skipped while
Android reports a call / communication audio mode. A single immediate screen-on
recovery is permitted only within 600 ms of an observed screen-off, only while the
real keyguard is already locked, and only when no custom surface is prepared.
There are no delayed retries after wake, accessibility interception, overlay,
boot receiver or persistent service. Existing authentication and secure-camera
sessions keep their prepared surface instead of being restarted.

The native screen remains available after restart / before first unlock, if
Android stops Vitreo's process, if the default launcher changes, when the user
wakes before Android has locked, or when the configured lock delay has not elapsed.
This is a deliberate fallback; enabling this feature does not guarantee it appears
over every wake or other app that is allowed over keyguard.

GrapheneOS owns fingerprint eligibility, strong-auth / lockdown requirements,
secondary PIN, duress credentials, user profiles and auto reboot. Vitreo never
handles those credentials, changes those policies or needs Google Play services.
Under-display fingerprint behavior while an activity occludes keyguard can differ
by OS/device; the native authentication screen remains the supported route. Keep
the system launcher installed. Turn the option off to return to the native surface.

Activation is private to this installation and is **not** encoded into backups.
Appearance is portable. The activity is non-exported, excluded from Recents and
uses a separate task. Real use sets `FLAG_SECURE`; preview does not. No additional
Android permission is introduced.

## Validation

JVM tests cover backup migration/round trips, bounded appearance settings, theme /
wallpaper selection and distance/velocity gesture thresholds. Native UI tests
exercise the editor, disabled-by-default previews and both themes. The final,
isolated emulator phase sets a disposable PIN and default Home, checks incorrect
and correct Android PIN entry, cancellation, previous-app return and disabling
without dismissing keyguard. Quick-cycle checks wake as soon as Android is secured,
without waiting for Vitreo's activity to be prepared. It restores credentials, role
and lock timing. Unit tests enforce the single-use 600 ms recovery boundary.

The header uses the platform's status-bar inset even while that bar is hidden.
Network/battery occupy that band, with the centered lock below the display cutout.
Original rounded status glyphs and an inset battery percentage serve iOS; angular
glyphs, a segmented battery and HUD colors serve Cyberpunk across Lock, Home,
Notification Center and Control Center. No signal strength is invented.

The release matrix is Android API 31 and 35. These are emulator tests, **not
physical GrapheneOS certification**. Before relying on the custom surface on a
Pixel, check cold boot, fingerprint and PIN, lockdown, secondary PIN if enabled,
an incoming call, emergency-screen access, camera return, Battery Saver and
switching users. Android's native fallback must stay usable in every case.

## Primary references

- [Android KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager): show-when-locked activities and system authentication.
- [GrapheneOS activity-start controller](https://github.com/GrapheneOS/platform_frameworks_base/blob/17/services/core/java/com/android/server/wm/BackgroundActivityStartController.java): default HOME exemption.
- [GrapheneOS security features](https://grapheneos.org/features): authentication, duress and secondary biometric PIN.
- [Apple Lock Screen customization](https://support.apple.com/guide/iphone/iph4d0e6c351/ios): visual reference for clock, wallpaper and accessories; no UIKit or Apple assets are used by this feature.

### Full-screen customization

Open **Settings → Lock Screen → Customize Lock Screen** while the device is unlocked. Tap the outlined clock or choose Clock to select Rounded, Light, Bold, Serif, Mono or Condensed, a color and Glass / Solid lettering. Drag the lower-right clock handle or use the accessible Height slider (100–240%); the width stays fitted and tall iOS clocks move accessories down. Wallpaper offers built-in thumbnails, Follow Home and dimming; Widgets controls the battery, alarm and private notification indicator. Done applies the draft; Cancel or Back discards it. Editing never enables the lock surface or dismisses Android authentication.

Visual references: [Apple Lock Screen customization](https://support.apple.com/guide/iphone/iph4d0e6c351/ios), [Apple status icons](https://support.apple.com/guide/iphone/iphef7bb57dc/ios), and [iOS 26 clock resizing](https://www.idownloadblog.com/2025/06/24/how-to-change-lock-screen-clock-size-iphone-ipad/). Glyphs are original geometry; clock fonts are available Android/bundled fonts. Photo depth segmentation, Apple fonts and native Face ID are not replicated.

## Cyberpunk fingerprint animation — requested follow-up

Investigate a short, optional cyan / red HUD response tied to a **real** biometric
event. This is a recorded idea, not a shipped scan indicator. The ordinary public
keyguard dismissal API reports success, cancellation or error, without identifying
which factor was used or exposing native sensor-acquisition events. BiometricPrompt
callbacks apply to the prompt an app owns; adding that prompt would authenticate
Vitreo separately rather than replace the secure device unlock. Consequently the
current shortcut hands the screen fully to SystemUI, whose fingerprint animation
Vitreo cannot skin. A future handoff effect may acknowledge the user's tap, but
must not label it as a fingerprint read or authentication success. Respect Reduce
Motion, Battery Saver and the Classic / Immersive setting.

References: [Android KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager),
[BiometricPrompt callbacks](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.AuthenticationCallback),
[GrapheneOS two-factor fingerprint unlock](https://grapheneos.org/features#two-factor-fingerprint-unlock).

Android's native status bar is hidden only while Vitreo's optional Home status bar
is active, and on its full-screen lock surface. Other apps retain their own system
bars. Android permits transient edge reveal; a normal launcher cannot globally
disable SystemUI. See [Android immersive mode](https://developer.android.com/develop/ui/views/layout/immersive).
