"""Contrats du protocole et protection des références, sans chronométrer Java."""
import copy
import json
from pathlib import Path
import random
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import fixed_report as report


def fixture(directory, profile=None):
    """Observations artificielles réservées aux tests, jamais publiées."""
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "campaign.json").unlink(missing_ok=True)
    profile = copy.deepcopy(profile or report.load_profile())
    cli, jvm = [], []
    rng = random.Random(profile['seed'])
    for case in profile['experiments']:
        if case.get('cli', True):
            for phase, blocks, pairs in [('warmup', profile['cli']['warmups'], 1),
                    ('measure', profile['cli']['blocks'], profile['cli']['pairs_per_block'])]:
                for block in range(1, blocks + 1):
                    orders = report.balanced_orders(pairs, rng) if phase == 'measure' else [('java', 'grep')]
                    for iteration, engines in enumerate(orders, 1):
                        for position, engine in enumerate(engines, 1):
                            cli.append(dict(case_id=case['id'], phase=phase, block=block, iteration=iteration,
                                sequence=len(cli)+1, position=position, engine=engine,
                                elapsed_ns=(30_000_000 if engine == 'java' else 3_000_000)+len(cli)*500, matching_lines=0))
        for fork in range(1, profile['jvm']['forks']+1):
            sequence = len({r['sequence'] for r in jvm}) + 1
            for phase, count in [('warmup', profile['jvm']['warmups']), ('measure', profile['jvm']['samples_per_fork'])]:
                for iteration in range(1, count+1):
                    n = fork*1000+iteration
                    jvm.append(dict(case_id=case['id'], fork=fork, sequence=sequence, phase=phase, iteration=iteration,
                        matching_lines=0, total_lines=10, parsing_ns=n, nfa_ns=n, dfa_ns=n, minimization_ns=0,
                        search_preparation_ns=n, preparation_ns=4*n, scan_ns=6*n, total_ns=10*n))
    automata = [dict(case_id=c['id'], regex_length=len(c['regex']), nfa_states=10+i, nfa_transitions=20+i,
                     search_dfa_states=30+i, search_dfa_transitions=40+i)
                for i, c in enumerate(profile['experiments'])]
    a, b = report.summaries(profile, cli, jvm)
    for name, rows in [('automata.csv', automata), ('cli.csv',cli), ('jvm.csv',jvm), ('cli-summary.csv',a), ('jvm-summary.csv',b)]:
        report.write_csv(directory/name, rows)
    report.write_json(directory/'profile.json', profile)
    (directory/'validation.txt').write_text('TEST FIXTURE ONLY\n')
    manifest = dict(schema_version=2, profile=profile, profile_sha256=report.sha256_file(directory/'profile.json'),
        integrity=report.tree_hashes(directory), source_sha256={}, started_utc='2000-01-01T00:00:00+00:00',
        finished_utc='2000-01-01T00:00:00+00:00', java_version='fixture', grep_version='fixture', locale='fixture',
        os='fixture', cpu='fixture', python='fixture', logical_cpu_count=1, cli_scope='fixture', jvm_scope='fixture',
        git_head='fixture', git_dirty=False, minimization_implemented=False,
        validation={c['id']: {'matching_lines':0} for c in profile['experiments']},
        automata={row['case_id']: row for row in automata},
        corpora={c['corpus']: {'lines':10, 'bytes':1000} for c in profile['experiments']})
    report.write_json(directory/'campaign.json', manifest)
    return manifest, cli, jvm


class FixedReportTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.profile = report.load_profile()

    def test_fixed_profile_and_branch_growth_expansion(self):
        self.assertEqual(14, len(self.profile['experiments']))
        self.assertEqual(13, sum(c.get('cli', True) for c in self.profile['experiments']))
        for case in self.profile['experiments']:
            if 'branch_depth' in case:
                self.assertEqual('(a|b)*a' + '(a|b)'*case['branch_depth'] + 'b', case['regex'])
        self.assertEqual(30, self.profile['cli']['blocks']*self.profile['cli']['pairs_per_block'])

    def test_balancing_is_exact_and_reproducible(self):
        first = report.balanced_orders(6, random.Random(123))
        self.assertEqual(first, report.balanced_orders(6, random.Random(123)))
        self.assertEqual(3, first.count(('java','grep')))
        self.assertEqual(3, first.count(('grep','java')))

    def test_statistics_keep_outliers(self):
        result = report.summarize([1, 2, 3, 4, 1000], .15)
        self.assertEqual((3, 2, 4, 1000, 5), tuple(result[k] for k in ('median_ms','q25_ms','q75_ms','max_ms','n')))
        self.assertTrue(result['noisy'])
        for values in ([1], [1, float('nan')], [-1, 1]):
            with self.assertRaises(ValueError):
                report.summarize(values, .15)

    def test_equal_counts_do_not_imply_equal_lines(self):
        a, b = self.root/'a', self.root/'b'
        a.write_bytes(b'1:a\n3:b\n'); b.write_bytes(b'1:a\n4:b\n')
        self.assertEqual(report.count_lines(a), report.count_lines(b))
        self.assertFalse(report.exact_files(a, b))
        b.write_bytes(a.read_bytes()); self.assertTrue(report.exact_files(a,b))

    def test_complete_data_uses_forks_as_independent_units(self):
        fixture(self.root)
        _, _, _, cli, jvm = report.load_validated_results(self.root)
        self.assertTrue(all(r['n']==30 for r in cli))
        self.assertTrue(all(r['n']==5 and r['unit']=='fork_mean' for r in jvm))

    def test_modified_raw_data_is_rejected(self):
        fixture(self.root)
        with (self.root/'cli.csv').open('a') as stream:
            stream.write('tampering\n')
        with self.assertRaisesRegex(ValueError, 'modifiées'):
            report.load_validated_results(self.root)

    def test_missing_hash_and_forged_summary_are_rejected(self):
        manifest, _, _ = fixture(self.root)
        del manifest['integrity']['cli.csv']
        report.write_json(self.root/'campaign.json', manifest)
        with self.assertRaisesRegex(ValueError, 'incomplet'):
            report.load_validated_results(self.root)
        manifest, _, _ = fixture(self.root)
        path = self.root/'cli-summary.csv'
        path.write_text(path.read_text().replace('process', 'not-process'))
        manifest['integrity']['cli-summary.csv'] = report.sha256_file(path)
        report.write_json(self.root/'campaign.json', manifest)
        with self.assertRaisesRegex(ValueError, 'Résumé'):
            report.load_validated_results(self.root)

    def test_duplicate_measurement_or_wrong_count_is_rejected(self):
        manifest, cli, jvm = fixture(self.root)
        altered = copy.deepcopy(cli); altered[1] = altered[0]
        with self.assertRaisesRegex(ValueError, 'dupliquées'):
            report.validate_observations(manifest, altered, jvm)
        cli[0]['matching_lines'] = 1
        with self.assertRaisesRegex(ValueError, 'Comptage'):
            report.validate_observations(manifest, cli, jvm)

    def test_unbalanced_order_is_rejected(self):
        manifest, cli, jvm = fixture(self.root)
        for a, b in zip(cli[::2], cli[1::2]):
            a['engine'], b['engine'] = 'java', 'grep'
        with self.assertRaisesRegex(ValueError, 'déséquilibré'):
            report.validate_observations(manifest, cli, jvm)

    def test_clean_protects_assets_and_unlinks_child_symlinks(self):
        assets = self.root/'docs/assets'; assets.mkdir(parents=True)
        (assets/'published.svg').write_text('published')
        target = self.root/'target'; target.mkdir()
        (target/'report').symlink_to(assets, target_is_directory=True)
        (target/'benchmarks').mkdir(); (target/'benchmarks/data.csv').write_text('local')
        (target/'classes').mkdir()
        with patch.object(report, 'ROOT', self.root):
            report.clean_generated()
            self.assertFalse((target/'report').exists())
            self.assertTrue((target/'classes').exists())
            self.assertEqual('published', (assets/'published.svg').read_text())

    def test_clean_rejects_target_symlink(self):
        (self.root/'target').symlink_to(self.root, target_is_directory=True)
        with patch.object(report, 'ROOT', self.root), self.assertRaises(ValueError):
            report.clean_generated()

    def test_publication_rolls_back_if_installation_fails(self):
        old, new = self.root/'old', self.root/'new'
        old.mkdir(); new.mkdir()
        (old/'x').write_text('original'); (new/'x').write_text('new')
        original_replace = report.os.replace
        def fail_install(source, destination):
            if Path(source) == new:
                raise OSError('simulated failure')
            original_replace(source, destination)
        with patch.object(report, 'ROOT', self.root), patch.object(report.os, 'replace', side_effect=fail_install):
            with self.assertRaises(OSError):
                report.publish_transaction([(new, old)])
        self.assertEqual('original', (old/'x').read_text())

    def test_publication_is_repeatable_and_cleanup_preserves_assets(self):
        fixture(self.root/'target/report/results')
        figures = self.root/'target/report/figures'; figures.mkdir()
        for name in report.FIGURES:
            for suffix in ('svg','png'):
                (figures/f'{name}.{suffix}').write_text('test figure')
        report.write_json(figures/'provenance.json', dict(
            campaign_sha256=report.sha256_file(self.root/'target/report/results/campaign.json'),
            figures=report.tree_hashes(figures)))
        (self.root/'.cache').mkdir()
        with patch.object(report, 'ROOT', self.root), patch.object(report, 'fingerprint', return_value={}):
            assets = self.root/'docs/assets'
            assets.mkdir(parents=True)
            (assets/'obsolete.png').write_text('stale')
            report.publish_assets(self.root/'target/report')
            first = report.tree_hashes(assets)
            self.assertFalse((assets/'obsolete.png').exists())
            self.assertEqual(6, len(list(assets.glob('*.svg'))))
            self.assertEqual([], list(assets.glob('*.png')))
            report.publish_assets(self.root/'target/report')
            self.assertEqual(first, report.tree_hashes(assets))
            report.clean_generated()
            self.assertEqual(first, report.tree_hashes(assets))

    def test_purge_removes_only_generated_csv_and_txt(self):
        results = self.root/'target/report/results'; results.mkdir(parents=True)
        (results/'raw.csv').write_text('x')
        (results/'validation.txt').write_text('ok')
        (results/'campaign.json').write_text('{}')
        (results/'report.md').write_text('keep')
        with patch.object(report, 'ROOT', self.root):
            removed = report.purge_raw_results(self.root/'target/report')
        self.assertEqual(2, len(removed))
        self.assertFalse((results/'raw.csv').exists())
        self.assertFalse((results/'validation.txt').exists())
        self.assertTrue((results/'campaign.json').exists())
        self.assertTrue((results/'report.md').exists())
        self.assertTrue((results/'purge.json').exists())


if __name__ == '__main__':
    unittest.main()
