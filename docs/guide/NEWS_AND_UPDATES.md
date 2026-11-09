# News and software updates

[Documentation](../README.md) · [Privacy](../../PRIVACY.md) · [Troubleshooting](TROUBLESHOOTING.md)

## News in Today

**Settings → Today & News** optionally replaces Today's widget panel with a news
feed. News is off by default. Switching back preserves the widget configuration.
Choose an edition, topic and whether to show article images, then pull to refresh.

| Source | Behavior |
| --- | --- |
| Publisher RSS, the default | BBC News in English, EL PAÍS in Spanish, Le Monde in French or tagesschau in German |
| Google News RSS | Optional public feed, with a separately switchable publisher fallback |

Neither uses a Google account, Google Play services or the Google News app. This
is not personalized Discover. Publisher RSS supplies a cover story and thumbnails
when included by the publisher; Google RSS does not provide those photographs.
Missing images never prevent reading a headline, and the image switch saves data.
The page identifies the source actually displayed, including fallback.

Feeds load only while the page is visible, resumed and focused. Cached stories
remain available offline; there is no periodic background news fetch. Articles
open in your browser when tapped. On GrapheneOS, Vitreo needs its **Network**
permission for any online source. Private DNS, VPN or network restrictions may
also affect delivery. See the [network troubleshooting steps](TROUBLESHOOTING.md#news-or-updates-do-not-load).

## Stable APK updates

**Settings → Software Update** checks stable releases from this repository.
Automatic checks run daily, with Wi-Fi/unmetered downloads by default; both
preferences can be changed. You can also check manually or install the latest
release APK over the existing installation.

GitHub's API has a direct HTTPS metadata/checksum fallback for rate limits and
transport failures. Downloads are checked for size, SHA-256, package identity,
increasing version and the installed signing identity. Prereleases, rollback,
Debug and Preview packages are rejected.

Allow Vitreo to install apps in Android when requested. Android retains
installation control and may require confirmation; Vitreo does not bypass it or
open installer prompts above the secure lock screen. Update preferences and
temporary APKs are private and are excluded from layout backups.

A signature mismatch is not fixed by enabling unknown-source installation. See
[signing continuity](../development/SIGNING.md) before removing an installation.
