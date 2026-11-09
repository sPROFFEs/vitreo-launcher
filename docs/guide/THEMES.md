# Themes, icons and wallpapers

[Documentation](../README.md) · [Gallery](../screenshots/README.md) · [Home & widgets](HOME_AND_WIDGETS.md)

Open **Settings → Themes & Icons**. Theme selection preserves the app arrangement
and widget bindings. Interface light/dark mode, wallpaper, icon source and motion
preferences are separate choices.

## Choose a style

| Style | Appearance |
| --- | --- |
| iOS 26 | Refractive glass, continuous corners, floating capsules and modern Control Center |
| iOS 16 | Uniform frost, quieter shadows, rounded-square controls and a solid, unstretched lock clock |
| Cyberpunk | Angular HUD panels, line artwork, Rajdhani typography and selectable color palettes |

**Motion & Glass** controls refraction, frost, Reduce Motion and Reduce
Transparency. iOS 16 keeps the modern optical preferences for when you switch
back. On Android 12, Battery Saver, reduced effects or unsupported hardware,
glass uses the prepared frosted wallpaper. See [glass troubleshooting](TROUBLESHOOTING.md#glass-looks-flat-or-animations-are-reduced).

## Cyberpunk palettes and effects

Choose Kiroshi Optics, Arasaka Corpo, Netrunner Deep-Net or Trauma Team, or an
Edgerunners character palette:

| Character | Palette | Included wallpaper |
| --- | --- | --- |
| David | Emergency yellow and Sandevistan green | Afterimage |
| Lucy | Moonlight cyan, pearl and violet | Moonlight |
| Rebecca | Mint green and electric pink | Neon Pulse |
| Kiwi | Crimson and pale gold | Deep Red |

Tap a character row for **colors only**. **Apply style + Home & Lock wallpapers**
explicitly applies its colors and both collection backgrounds. The imported
photo is retained, and existing dim, motion, effects and lock activation choices
are preserved. The four illustrations are original fan-inspired artwork;
[provenance and prompts](../artwork/EDGERUNNERS_WALLPAPERS.md) identify their source.

**Classic · Quiet HUD** keeps the restrained appearance. **Immersive** enables
ambient rain, light drift and scanning accents. The modular effects include
corner reticles, tap accents, Cyberdeck widgets, Quickhack Dock, icon tier marks,
terminal search and themed notification treatments. Individual switches remain
available. Effects pause when Home is obscured, loses focus, enters Battery Saver
or uses Reduce Motion; there is no live-wallpaper service.

**Status bar appearance** adjusts the Cyberpunk rail's thickness and opacity,
with a preview and Reset. It applies to Home, Lock and Notification Center when
the custom status bar is enabled separately. Camera/status insets remain reserved.
Cyberpunk Control Center uses angular controls throughout.

## Icon packs and per-app overrides

Choose an installed ADW/Nova-compatible pack in **Themes & Icons**. Hold an app
and open **More → Change Icon** to choose **Current theme**, an installed pack or
**Use Default**. This lets a third-party gallery use the theme's Photos artwork.
Overrides follow the selected iOS/Cyberpunk theme and survive layout backups.

The bundled Cyberpunk set has 23 app-family glyphs and 13 control glyphs. Apps
without matching artwork retain their native icon. A missing pack or drawable
does not remove an app. External packs are read as resources; their code is not
executed and their images are not copied into exported backups.

Static appfilter mappings and drawable pickers are supported. Dynamic pack clocks,
calendar prefixes and pack-supplied fallback masks are not implemented. See
[asset credits](../../THIRD_PARTY_NOTICES.md) for bundled licenses.

## Wallpaper framing

Open **Settings → Wallpaper** for the collection or a local photo. The collection
includes four procedural backgrounds, five original Cyberpunk stock images and
the four Edgerunners illustrations. All are available offline.

The photo editor supports pinch, pan, **Fit**, **Fill** and **Reset**. Saving keeps
a private copy of the source, bounded to 1600 pixels on its longer side, plus its
crop settings; Cancel preserves the previous wallpaper. Dim applies to both
Cyberpunk experiences and iOS, including photos.

**Sync with Android Home** is on by default and applies only while Vitreo is the
default launcher. Android receives the same static Home background, crop and dim,
helping app/Recents transitions stay coherent. It does not change Android's secure
lock wallpaper. The [custom Lock Screen](LOCK_SCREEN.md) can follow Home or use an
independent collection image and dim.
