"""Tests du protocole de comparaison, sans lancer Maven ni dépendre de GNU grep."""

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("compare_egrep", Path(__file__).resolve().parents[1] / "lib/compare_egrep.py")
benchmark = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(benchmark)


class ComparisonProtocolTest(unittest.TestCase):
    """Protège la compatibilité des motifs, la lecture et le traitement des erreurs."""

    def test_common_regex_language(self):
        """Accepte les opérateurs communs et les caractères spéciaux échappés."""
        for expression in ["Elizabeth|Darcy", "a(b|c)*", "a.b", "(a*)*", r"a\.b", r"\*\|\(\)\\", "été", "-word"]:
            with self.subTest(expression=expression):
                benchmark.validate_expression(expression)

    def test_rejects_incompatible_or_invalid_regex(self):
        """Refuse les extensions non implémentées et les syntaxes ambiguës."""
        for expression in ["", "a+", "a?", "[ab]", "a{2}", "^a", "a$", r"\b", "a\\", "a**", "*a",
                           "()", "(a", "a)", "a|", "|a", "a||b", "a(|b)", "a\nb", "a\0", "😀"]:
            with self.subTest(expression=expression), self.assertRaises(ValueError):
                benchmark.validate_expression(expression)

    def test_normalization_preserves_lines_and_source(self):
        """Normalise les séparateurs sans ajouter de dernière ligne ni modifier la source."""
        with tempfile.TemporaryDirectory() as temporary:
            source, target = Path(temporary) / "source.txt", Path(temporary) / "copy.txt"
            for original, expected in [(b"", b""), (b"\r\n", b"\n"),
                                       ("été\r\na\rb\n\nlast".encode(), "été\na\nb\n\nlast".encode())]:
                with self.subTest(original=original):
                    source.write_bytes(original)
                    info = benchmark.prepare_corpus(source, target)
                    self.assertEqual(original, source.read_bytes())
                    self.assertEqual(expected, target.read_bytes())
                    self.assertEqual(len(expected), info["bytes"])
                    self.assertEqual(benchmark.hashlib.sha256(expected).hexdigest(), info["sha256"])

    def test_rejects_unsupported_text(self):
        """Ne compare pas des unités de caractères différentes ni du texte UTF-8 invalide."""
        with tempfile.TemporaryDirectory() as temporary:
            source, target = Path(temporary) / "source.txt", Path(temporary) / "copy.txt"
            for data in [b"a\0b", b"\xff", "😀".encode()]:
                with self.subTest(data=data), self.assertRaises(ValueError):
                    source.write_bytes(data)
                    benchmark.prepare_corpus(source, target)

    @patch.object(benchmark.subprocess, "run")
    def test_grep_no_match_is_not_an_error(self, execute):
        """Le statut 1 de grep accompagne un compte nul et reste un succès du protocole."""
        execute.return_value = subprocess.CompletedProcess(["grep"], 1, b"0\n", b"")
        count, duration = benchmark.run_count(["grep"], {}, 1, grep=True)
        self.assertEqual(0, count)
        self.assertGreaterEqual(duration, 0)

    @patch.object(benchmark.subprocess, "run")
    def test_process_errors_and_bad_counts_abort(self, execute):
        """Refuse les erreurs système, les sorties humaines et les statuts incohérents."""
        for grep, status, output in [(True, 2, b""), (True, 1, b"2"), (True, 0, b"0"),
                                      (False, 1, b"0"), (False, 0, b"result: 3"), (False, 0, b"-1")]:
            with self.subTest(grep=grep, status=status, output=output), self.assertRaises(ValueError):
                execute.return_value = subprocess.CompletedProcess(["tool"], status, output, b"problem")
                benchmark.run_count(["tool"], {}, 1, grep=grep)

    @patch.object(benchmark.subprocess, "run")
    def test_timeout_is_propagated(self, execute):
        """Une exécution bloquée ne devient pas une fausse mesure."""
        execute.side_effect = subprocess.TimeoutExpired(["tool"], 1)
        with self.assertRaises(subprocess.TimeoutExpired):
            benchmark.run_count(["tool"], {}, 1)


if __name__ == "__main__":
    unittest.main()
