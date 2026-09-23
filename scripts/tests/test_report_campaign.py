"""Vérifie les corpus répétés sans exécuter les campagnes de mesure."""

import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    "report_campaign", Path(__file__).resolve().parents[1] / "lib/report_campaign.py")
campaign = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(campaign)


class ReportCampaignTest(unittest.TestCase):
    def test_streamed_replication_preserves_raw_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = directory / "source.bin"
            for original in [b"", b"a", b"\r\n", b"\x80\xff\na\rb",
                             b"x" * 65535 + b"\n\x00\xff\nfin"]:
                with self.subTest(length=len(original)):
                    source.write_bytes(original)
                    base = original + (b"\n" if original and not original.endswith(b"\n") else b"")
                    corpora, lines = campaign.prepare_scaled_corpora(source, directory, factors=(2, 3))
                    self.assertEqual(base.count(b"\n"), lines)
                    self.assertEqual(source, corpora[1])
                    self.assertEqual(original, source.read_bytes())
                    self.assertEqual(hashlib.sha256(original).hexdigest(), campaign.sha256_file(source))
                    for factor in (2, 3):
                        self.assertEqual(base * factor, corpora[factor].read_bytes())



if __name__ == "__main__":
    unittest.main()
