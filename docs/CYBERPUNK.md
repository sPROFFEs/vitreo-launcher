# Cyberpunk theme and icon packs

Available in 0.5.1. Open **Settings → Themes & Icons → Cyberpunk**. The theme is
opt-in; existing layouts and older backups continue to use iOS by default.

The native HUD uses dark panels, red borders, cyan details, cut corners, a static
grid background and Rajdhani typography. Clock and Calendar use real local time
and date. A selected photo is retained under a dark overlay. Home, Dock, folders,
App Library, context menus, Search, controls and Settings share this treatment.
Native Android widgets keep their provider content and their bounded host.

## Icons

The theme includes 23 Arcticons app-family glyphs and 13 CYBERCORE control glyphs.
Other apps retain their native artwork inside a HUD frame. Credits, source
revisions and SHA-256 hashes are in `third_party/arcticons`, `third_party/cybercore`
and `third_party/fonts`; notices/licenses are also packaged in the APK.

Install an ADW/Nova-compatible icon pack and return to **Themes & Icons** to
select it. This works with either theme. Hold an app, choose **More → Change
Icon**, select a pack and search for an alternate. **Use Default** clears that
app's override. The picker reads installed resources and never runs pack code.

Resolution order: per-app override, the selected pack's exact activity mapping,
theme artwork, native artwork. A missing drawable, unknown alias or unavailable
pack falls through. Theme/pack changes do not remove apps or rearrange them.
Uninstalling an app removes its override; alias replacement moves the reference
with its shortcut. Backups store pack/drawable references, not third-party images.

The reader supports static `item` mappings in `res/xml`, `res/raw` or
`assets/appfilter.xml`, plus `drawable.xml` picker entries. Dynamic clocks,
calendar prefixes and pack-supplied fallback masks are not implemented. The
reader uses a 16 MiB XML limit, 100,000 item limit, bounded token count and rejects
DTD/entity declarations. Parsing and rasterization run on IO. Parsed indexes and
an 8 MiB bitmap cache are invalidated when pack inventory/configuration changes;
an ordinary return from another app retains them.

Recommended candidates and sources are in [the research](CYBERPUNK_RESEARCH.md).
The commercial Aionyxe/LineX packs have not been purchased or tested here; their
artwork is not bundled. Compatibility tests use an original disposable test pack.

## Classic and Immersive

**Classic** is the existing restrained HUD and remains the default for previous
Cyberpunk installations and imported backups. **Immersive** is an explicit choice
in Themes & Icons. It adds rain, light drift, scanning accents and moving panel
edges; it never moves app labels or continuously glitches text. One shared animation
clock is read during drawing, without rebuilding the workspace or wallpaper every
frame. The clock exists only while the activity is resumed, focused, and Home is
visible without a modal. Battery Saver, Reduce Motion and HUD Effects disable it.

Two original stock wallpapers, **Neon Rider** and **Digital Dusk**, are available
in Themes & Icons and Wallpaper. Selecting Immersive leaves the current wallpaper
intact. Selecting a stock image saves its palette ID and leaves your imported photo
available. Android Home synchronization uses the same composed static poster;
ambient effects are a foreground overlay, not a live-wallpaper service.
[Generation prompts and provenance](artwork/CYBERPUNK_WALLPAPERS.md).

## Motion

Panels use a 180 ms decorative sweep with a short displaced line accent. These
effects leave content legible. Classic has no idle animation loop; Immersive adds
the optional ambient effects described above. The **Panel
sweeps & glitch accents** switch controls them; Control Center also exposes
**HUD Effects**. Reduce Motion and Battery Saver suppress decoration. Cyberpunk
uses critically damped entry springs and restrained scale, with existing
velocity-aware cancellation retained. Native app launches still use Android's
public launcher contract; Recents and other apps' windows belong to Android.

See [MOTION.md](MOTION.md) and [TESTING.md](TESTING.md). Emulator verification does
not establish 60/120 Hz performance on the Pixel 8 Pro or S21 FE. This is an
independent inspired theme with original HUD geometry, not an official game asset
pack or a replacement for Android SystemUI.
