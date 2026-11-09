# Architecture

[Documentation](../README.md) · [Build](../../BUILDING.md) · [Motion](../design/MOTION.md)

Vitreo is a Kotlin/Compose HOME activity. Android owns other app windows, Recents,
system gestures and secure authentication. Work profiles and Private Space are
not fully implemented. Native widgets and optional system integration use public
Android APIs.

## Catalog and persisted state

LauncherApps plus MAIN/LAUNCHER queries feed AppCatalog on IO, then
LauncherViewModel exposes StateFlow to Compose. The catalog respects effective
component state, retains temporarily unavailable activities and remaps equivalent
aliases while preserving placements. Package callbacks invalidate caches off Main.
Confirmed uninstalls clear Home, Dock, folders, known apps, overrides and recent
suggestions; package replacement does not. Refresh only removes a missing package
when both platform checks confirm its absence. Launch targets are rechecked
against the current catalog; layout backups cannot inject executable intent URIs.

LayoutOps maintains ordered Home entries, folders and a four-slot Dock.
LauncherState.sanitized bounds input and removes invalid/duplicate entries.
StateCodec uses optional, bounded appearance fields in schema 1; StateStore uses
AtomicFile and a serial IO writer. ViewModel cleanup closes/drains pending saves,
though abrupt process death can lose the last in-flight change. Import size is
limited to 1 MiB and unreadable state is retained for recovery when possible.

## Home and gestures

The pager holds Today, Home pages and App Library. Stable keys preserve identity.
HomeGrid reserves built-in/native widget rectangles before flowing app entries
through free cells. Insets, font scale, icon size and edit-toolbar space determine
geometry; showing the search keyboard does not repack Home.

HomeReorder uses stationary logical slots rather than animated tile bounds.
Pointer position is read by the floating icon's drawing layer; preview order is
local until drop. applyHomeOrder commits using current entries, preserving
concurrent removals and folder renames. Android's consumed synthetic release on
ACTION_CANCEL discards the preview. Edit mode keeps one pointer handler, and
neighbour pages stay composed with inactive semantics hidden. Edge scroll runs
in the screen's scope so a current-page change cannot cancel it halfway.

Modals disable underlying gestures and clear background semantics. SurfaceHost
retains outgoing content and one progress value through reversal/dismissal.
App arrival is armed by a real activity/Overview handoff rather than focus loss
alone; music overlays therefore do not replay Home's radial icon transition.

## Widgets, icons and wallpapers

WidgetController owns AppWidgetHost IDs and placements in installation-local
preferences, outside portable layout backups. Pending configuration is matched
to its ID. Move/resize preserves bindings; removal releases them. Native outline
clipping, proportional fitting, provider padding and exact Android 12 size options
keep RemoteViews inside the host. Catalog previews do not allocate widget IDs.

IconPacks discovers ADW/Nova resource intents and parses static appfilter/picker
resources on IO, with XML/token/item bounds and an 8 MiB bitmap cache. It never
executes pack code. Per-app overrides, exact pack mappings, theme artwork and
native icons form the fallback chain. Missing artwork never removes an app.

WallpaperStore saves a bounded photo and crop, or loads a collection background,
and prepares a frosted texture off Main. HomeBackground shares the composed crop,
dim and static decoration with SystemHomeWallpaper. Synchronization requires the
HOME role, writes only FLAG_SYSTEM and avoids repeated writes by pixel hash and
wallpaper ID. A local fallback hands off to Android's wallpaper surface.

GlassPanel/GlassRenderer use hardware AGSL on API 33+ when supported, with cached
sharp/frosted paths for API 31/32, reduced effects or failure; opaque panels bypass
sampling. Draw-time coordinates keep a moving lens aligned without wallpaper
redecoding or per-frame bitmap blur. iOS 16 uses uniform frost. Cyberpunk bypasses
the glass renderer and shares CyberPalette, shapes and typography across its HUD.
Character/faction and wallpaper IDs are append-only for backup stability.

## Device controls, notices and island

ControlCenterController owns visible-panel audio, torch and settings observers.
It reads real state after operations and unregisters on stop. Brightness affects
Vitreo's window; restricted device controls open Android settings.

SystemBridge holds ephemeral notification/media flows. Android binds the protected,
non-exported VitreoNotificationListener after explicit permission. Independent
feature switches gate collection and media callbacks; disable/revocation clears
the relevant memory. Notification bodies and artwork never enter layout files,
backups, diagnostics or network requests.

NotificationIsland uses transient WindowManager views inside that listener's
process. Fresh posts trigger alerts; inbox refresh/reconnect does not replay them.
IslandLayout bounds dp geometry; local preferences store activation and placement.
Media-notification hints plus explicit reservation/yield choices support other
islands without enumerating their windows. See [island behavior](../guide/NOTIFICATION_ISLAND.md).

## Lock Screen and depth

LockScreenRuntime is device-local and only prepares a non-exported, separate
surface task when enabled and secure keyguard is active. A single bounded quick
wake recovery preserves Android fallback; no persistent service, wake lock or
late wake retries are added. Android handles credentials and native fingerprint
policy. LockNotice is a redacted projection; tapping rechecks the live notification
after system authentication. Idle return hands display management to keyguard.

DepthWallpaper runs the bundled person model on CPU off Main, caches the current
mask and falls back on failure. LockDepthLayer clips a conservative foreground to
the clock while sharing the wallpaper crop, dim and wake transform. See
[depth/model provenance](LOCK_SCREEN_DEPTH.md) and the [user guide](../guide/LOCK_SCREEN.md).

## Network features

HttpsClient uses HTTPS-only, bounded responses and purpose-specific redirect
host allowlists, without account tokens or cookies. ReleaseFetcher separates
stable metadata parsing, API/direct-HTTPS fallback, APK checksum/identity/signer
checks and native PackageInstaller consent. A protected daily job and private
installer receiver keep update state outside portable backups.

NewsLoader chooses publisher RSS or optional Google RSS/fallback. NewsRepository
keeps bounded per-configuration caches; NewsImages bounds hosts, image dimensions,
concurrency, decoded memory and disk. Parsing rejects external entities. No article
HTML scraping, WebView or Google account is used. Feed/image loading requires the
page to be active, resumed and focused. See [privacy](../../PRIVACY.md).

## Release boundary

JVM/lint/source checks and exact-commit API 31/35 evidence gate publication.
Private signing, immutable public assets and upload-digest verification are
covered by [Releasing](RELEASING.md). Screenshots have their own
[APK-backed provenance](../screenshots/PROVENANCE.md), separate from test evidence.
