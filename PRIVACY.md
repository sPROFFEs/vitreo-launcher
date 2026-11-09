# Privacy

Vitreo has no accounts, ads or analytics. Core launcher use, bundled themes and
wallpaper depth do not require Google Play services. Internet is used for software
updates and optional news/images, not to send app layouts or notification content.

## Local data

| Data | Storage and use |
| --- | --- |
| App arrangement and appearance | Private local state; optionally exported as a layout JSON |
| Recent suggestions | Recent launches made through Vitreo, not global usage-history access |
| Wallpaper photo | System picker, private copy bounded to 1600 px, plus saved crop settings |
| Android widgets | Installation-local bindings/placements; not portable widget IDs |
| Icon packs | Installed resources read locally; no pack code execution or image redistribution in backups |
| Island and custom-lock activation | Device-local preferences, excluded from imported layouts |
| Notification/media content | Memory only, gated by feature switches and Android authorization |

The catalog queries available launcher activities for the current Android user;
Vitreo does not upload an app inventory. Photos are copied only when saved;
cancelling preserves the current image. Portable backups exclude photos, widget
bindings, recent suggestions and Android permissions. Appearance preferences are
portable, but importing a layout does not grant permission or activate the island
or custom Lock Screen.

## Notification access and overlays

Notification Center, connected media, the island and lock summaries are optional.
Android binds a protected, non-exported NotificationListenerService only after
explicit user authorization. Separate switches gate collection and media callbacks.
Disable clears the relevant data/listeners; permission revocation disconnects and
clears both. Turning off a feature does not revoke an Android permission itself.

Home's Notification Center can display app, title, text, time and supported actions.
The Lock Screen shows only app icon/name/time/count, excluding secret notices;
message text, titles, artwork and actions remain hidden until Android authentication.
Media integration reads active MediaSessions, bounded artwork and supported actions,
without a listening-history database.

Notification bodies, actions, playback details and artwork are not written to
layout files, backups, diagnostics or network requests. Android can restrict
sensitive notices and other profiles.

The island additionally needs **Display over other apps**. It shows fresh eligible
alerts and removes its windows on lock, screen-off, disable or access loss. A media
notification hint and manual reservation handle competing islands; Android does
not expose a list of other apps' overlay windows. No accessibility or persistent
foreground service is added.

## Controls and wallpaper synchronization

The panel reads volume, battery, brightness and public connectivity state.
ACCESS_NETWORK_STATE does not grant SSID, location, nearby-device or cellular
signal-strength access. Volume changes the real media stream; Home brightness
affects Vitreo's window. Restricted controls open Android settings. Torch uses the
public API without capturing images.

**Sync with Android Home** is on by default and only applies while Vitreo holds
HOME. SET_WALLPAPER delivers the composed static Home image, crop, dim and theme
to Android, using FLAG_SYSTEM without system backup. It does not read another
app's wallpaper or change Android's secure-lock wallpaper. Glass samples Vitreo's
own background, not other apps. Bundled wallpapers and HUD animation are local;
there is no live-wallpaper service.

## Lock Screen and experimental depth

The custom surface is off by default, non-exported, excluded from Recents and
uses FLAG_SECURE in real use; its unlocked preview permits screenshots. Android /
GrapheneOS validates PIN, password and biometrics. Vitreo never reads or stores
credentials, disables keyguard or changes strong-auth policies.

No wake lock, keep-screen-on flag, boot receiver, device-admin permission or late
wake retry loop is added. Idle handoff returns to native keyguard. Native Android
remains the fallback when the custom surface cannot be prepared.

Experimental depth is separately off by default. Its bundled CPU model processes
the still wallpaper on-device, without a model download, Google service or photo
upload. A private cache holds the current silhouette mask, identified by wallpaper
hash; Android may clear it. No reliable mask/overlap means ordinary rendering.
See [depth processing and model provenance](docs/development/LOCK_SCREEN_DEPTH.md).

## Software updates

Production checks stable public releases of sPROFFEs/vitreo-launcher through
HTTPS, using the GitHub API and direct release-metadata/checksum fallback.
GitHub/CDN servers receive the request and IP, without tokens, app inventory,
notifications or layout content. APK size, hash, package, increasing version and
installed signing identity are checked before Android's installer handles consent.
Prereleases, Debug/Preview and rollbacks are rejected.

A daily JobScheduler check uses private preferences and temporary APK storage,
outside layout backups; automatic checks and unmetered-download preferences are
configurable. REQUEST_INSTALL_PACKAGES requires Android authorization. Android
retains control over confirmation; Vitreo does not force an installer above the
lock screen. POST_NOTIFICATIONS is optional for pending-install notices.

## News and article images

News is off by default. Publisher RSS contacts BBC News, EL PAÍS, Le Monde or
tagesschau according to the selected edition. Optional Google RSS uses
news.google.com without an account, cookie or Google Play services; fallback to
the publisher is independently configurable. This is not personalized Discover.

Images load only from feed-supplied, allowed publisher CDNs: ichef.bbci.co.uk,
ichef.bbci.com, imagenes.elpais.com, img.lemde.fr and images.tagesschau.de.
The image switch can disable those requests. Google RSS does not supply article
photos. Vitreo does not scrape article pages or execute their scripts for artwork.
Publishers/CDNs receive requests and IP, not location, notices or app history.

Caches hold up to 50 headlines per configuration and 12 recent configurations;
image limits are 24 MiB on disk and 12 MiB decoded memory. Loading requires the
page to be active, resumed and focused. There is no periodic background news fetch.
Tapping an article opens your browser under that site's policy. GrapheneOS's
Network permission controls all Vitreo network access.

## Diagnostics

**About Vitreo → Export Diagnostics** exports only when requested. It contains
version/device information, graphics/preferences, activity count and the last
crash stack, not a full app inventory, photo or credentials. Review it and any
screenshots/logs before sharing; technical errors can contain contextual details.
For private security concerns, see [Security](SECURITY.md).
