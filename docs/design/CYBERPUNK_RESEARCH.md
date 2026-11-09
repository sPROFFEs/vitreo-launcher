# Cyberpunk reference research

[Documentation](../README.md) · [Shipping themes](../guide/THEMES.md) · [Asset credits](../../THIRD_PARTY_NOTICES.md)

Historical review: 2026-10-17, after 0.5.0 and before the first Cyberpunk release.
These are sources and implementation decisions, not a list of current features
or a promise of future work. The [theme guide](../guide/THEMES.md) describes the
shipping factions, Edgerunners styles, effects and limitations.

## Visual sources

| Source | Contribution and boundary |
| --- | --- |
| [CD PROJEKT RED accessibility article](https://www.cyberpunk.net/en/news/49591/update-2-1-accessibility-features) | Official menu/HUD references; no screenshots or game assets redistributed |
| [CYBERCORE at 5b90ffb](https://github.com/sebyx07/cybercore-css/tree/5b90ffbcc4a1aa84d122eead296fab0cf7ee58ca) | MIT control geometry, panels and bounded decorative effects; selected glyphs have retained provenance |
| [cyberpunk-css at c6a77b4](https://github.com/alddesign/cyberpunk-css/tree/c6a77b49062c9c876a85c0137cb69c2acc9fd1d8) | Angular geometry and clipped accent references; no web runtime or commercial fonts imported |
| [Arcticons at 90a4343](https://github.com/Arcticons-Team/Arcticons/tree/90a4343510f35e33421f0a41df5debc29c3604d1) | Curated CC BY-SA artwork; no GPL application code copied |
| [Official Edgerunners material](https://www.cyberpunk.net/en/edgerunners) | Character palette inspiration for the later original wallpapers |

The original Arcticons appfilter inspection counted 49,548 mappings and 14,028
distinct drawable names at that pinned revision, not guaranteed runtime coverage
of every alias or the current upstream pack. Vitreo bundles a documented subset
and supports installed packs separately.

Commercial Aionyxe/LineX candidates were noted during the original review but
were not purchased, runtime-tested or bundled. No compatibility recommendation
is inferred from those old storefront references.

## Decisions retained in the native implementation

- Use Compose shapes/materials, not WebView/CSS, with readable labels and large
  phone touch regions rather than game-menu pixel dimensions.
- Separate structure, information and highlight colors. Later faction/character
  palettes share the same semantic roles across the HUD.
- Use bundled Rajdhani Medium/Semibold with its OFL notice. Do not redistribute
  Blender/Cyberpunk fonts merely because a surrounding CSS repository is MIT.
- Read ADW/Nova icon resources without executing pack code; missing mappings keep
  native artwork and app identity. Pack XML parsing and bitmap caches are bounded.
- Preserve app order, Android widget content, wallpaper dim and Android ownership
  of Recents, authentication and other apps' windows.
- Keep Quiet/Immersive and individual effects separate. Stop decoration when
  obscured, unfocused, reduced or in Battery Saver; avoid continuous text glitches.

Exact palette values and effect switches are source definitions, not sampled
constants from the game. Original wallpaper prompts and supplied-image provenance
remain in [artwork](../artwork/CYBERPUNK_WALLPAPERS.md) and
[Edgerunners artwork](../artwork/EDGERUNNERS_WALLPAPERS.md).

Physical Pixel/Samsung frame rates were not established by this research. Use
[device testing](../development/TESTING.md#physical-device-checks) for measured
performance; emulator traces do not prove a 60/120 Hz target.
