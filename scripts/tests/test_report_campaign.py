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
    def test_streamed_normalization_preserves_corpus_and_replication(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = directory / "source.txt"
            for content in ["", "a", "\r\n", "été\r\na\rb\n\nlast",
                            "x" * 65535 + "\r\n😀\rfin"]:
                with self.subTest(length=len(content)):
                    original = content.encode("utf-8")
                    source.write_bytes(original)
                    expected = content.replace("\r\n", "\n").replace("\r", "\n")
                    if expected and not expected.endswith("\n"):
                        expected += "\n"
                    corpora, lines = campaign.prepare_scaled_corpora(source, directory, factors=(2, 3))
                    self.assertEqual(expected.count("\n"), lines)
                    self.assertEqual(source, corpora[1])
                    self.assertEqual(original, source.read_bytes())
                    self.assertEqual(hashlib.sha256(original).hexdigest(), campaign.sha256_file(source))
                    for factor in (2, 3):
                        self.assertEqual((expected * factor).encode("utf-8"), corpora[factor].read_bytes())


if __name__ == "__main__":
    unittest.main()
