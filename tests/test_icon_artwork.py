"""Keep imported artwork tied to its reviewed source bytes and bundled credits."""
import hashlib
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class IconArtworkTests(unittest.TestCase):
    def test_bundled_icons_match_the_recorded_publisher_assets(self):
        records = json.loads((ROOT / "third_party/svgstack/assets.json").read_text())
        files = set()
        for entry in records:
            image = ROOT / entry["file"]
            self.assertEqual(image.parent, ROOT / "app/src/main/res/drawable-nodpi")
            self.assertTrue(entry["source"].startswith("https://svgstack.com/icon/"))
            self.assertTrue(entry["image"].startswith("https://svgstack.com/media/img/"))
            data = image.read_bytes()
            self.assertEqual(data[:4], b"RIFF")
            self.assertEqual(data[8:12], b"WEBP")
            self.assertEqual(hashlib.sha256(data).hexdigest(), entry["sha256"])
            files.add(image.name)
        self.assertEqual(files, {p.name for p in (ROOT / "app/src/main/res/drawable-nodpi").glob("ios_*.webp")})

    def test_every_imported_icon_is_credited_inside_the_apk(self):
        notice = (ROOT / "app/src/main/assets/licenses/NOTICE.txt").read_text()
        for entry in json.loads((ROOT / "third_party/svgstack/assets.json").read_text()):
            self.assertIn(entry["source"], notice)
        self.assertIn("Creative Commons Attribution 4.0 International", notice)
        self.assertEqual((ROOT / "third_party/licenses/CC-BY-4.0.txt").read_bytes(),
            (ROOT / "app/src/main/assets/licenses/CC-BY-4.0.txt").read_bytes())

    def test_cyberpunk_artwork_and_fonts_match_pinned_sources(self):
        for group in ("arcticons", "cybercore", "fonts"):
            records = json.loads((ROOT / f"third_party/{group}/assets.json").read_text())
            self.assertGreater(len(records), 0)
            for entry in records:
                data = (ROOT / entry["file"]).read_bytes()
                self.assertEqual(hashlib.sha256(data).hexdigest(), entry["sha256"])
                self.assertIn(entry["source"], (ROOT / "app/src/main/assets/licenses/NOTICE.txt").read_text())
        for name in ("CC-BY-SA-4.0.txt", "Rajdhani-OFL.txt"):
            self.assertEqual((ROOT / "third_party/licenses" / name).read_bytes(),
                (ROOT / "app/src/main/assets/licenses" / name).read_bytes())
        self.assertEqual((ROOT / "third_party/cybercore/LICENSE").read_bytes(),
            (ROOT / "app/src/main/assets/licenses/CYBERCORE-MIT.txt").read_bytes())
