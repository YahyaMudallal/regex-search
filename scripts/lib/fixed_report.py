#!/usr/bin/env python3
"""Campagne expérimentale reproductible avec publication transactionnelle des assets Markdown."""

import argparse
import csv
import fcntl
import io
import json
import math
import os
from pathlib import Path
import platform
import random
import shutil
import statistics
import subprocess
import sys
import tempfile
from datetime import datetime, timezone

from compare_egrep import find_grep, prepare_corpus, run_count, utf8_locale, validate_expression
from report_campaign import prepare_scaled_corpora, sha256_file

ROOT = Path(__file__).resolve().parents[2]
PROFILE = ROOT / "scripts/report-profile.json"
FIGURES = ("latency", "scaling", "distribution", "phases", "compilation", "stress")


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_csv(path, rows):
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def fingerprint():
    paths = [ROOT / "pom.xml", ROOT / "run.sh"]
    for folder in ("src", "scripts"):
        paths.extend(path for path in (ROOT / folder).rglob("*")
                     if path.is_file() and path.suffix in {".java", ".py", ".sh", ".json", ".txt"})
    return {str(path.relative_to(ROOT)): sha256_file(path) for path in sorted(paths)}


def tree_hashes(directory):
    return {str(path.relative_to(directory)): sha256_file(path)
            for path in sorted(directory.rglob("*")) if path.is_file()}


def load_profile(path=PROFILE):
    profile = json.loads(path.read_text(encoding="utf-8"))
    if profile["cli"]["pairs_per_block"] % 2 or profile["cli"]["pairs_per_block"] < 2:
        raise ValueError("Le nombre de paires doit être pair pour équilibrer l'ordre des moteurs.")
    if min(profile["cli"]["blocks"], profile["jvm"]["forks"], profile["jvm"]["samples_per_fork"]) < 2:
        raise ValueError("Au moins deux blocs, forks et observations sont nécessaires.")
    ids = set()
    for case in profile["experiments"]:
        if case["id"] in ids:
            raise ValueError("Identifiant d'expérience dupliqué")
        ids.add(case["id"])
        if "repeat_a" in case:
            case["regex"] = "a" * case["repeat_a"] + "b"
        if "branch_depth" in case:
            depth = case["branch_depth"]
            if not isinstance(depth, int) or depth < 1 or depth > 12:
                raise ValueError("branch_depth doit être un entier entre 1 et 12")
            case["regex"] = "(a|b)*a" + "(a|b)" * depth + "b"
        validate_expression(case["regex"])
    comparisons = {}
    engines = {}
    for case in profile["experiments"]:
        if case["strategy"] not in {"KMP", "DFA", "DFAM"}:
            raise ValueError("La comparaison exige des stratégies explicites KMP, DFA ou DFAM")
        signature = (case["regex"], case["corpus"], case.get("cli", True))
        if comparisons.setdefault(case["comparison"], signature) != signature:
            raise ValueError("Les moteurs d'une comparaison doivent partager regex, corpus et périmètre")
        seen = engines.setdefault(case["comparison"], set())
        if case["strategy"] in seen:
            raise ValueError("Stratégie dupliquée dans une comparaison")
        seen.add(case["strategy"])
    if any(not {"DFA", "DFAM"}.issubset(seen) for seen in engines.values()):
        raise ValueError("Chaque comparaison exige un témoin DFA et un chemin DFAM")
    return profile


def balanced_orders(pairs, randomizer):
    orders = [("java", "grep"), ("grep", "java")] * (pairs // 2)
    randomizer.shuffle(orders)
    return orders


def summarize(values, threshold):
    if len(values) < 2 or any(not math.isfinite(value) or value < 0 for value in values):
        raise ValueError("Observations insuffisantes ou invalides")
    q25, _, q75 = statistics.quantiles(values, n=4, method="inclusive")
    median = statistics.median(values)
    relative = (q75 - q25) / median if median else 0.0
    return {"n": len(values), "mean_ms": statistics.mean(values), "median_ms": median,
            "q25_ms": q25, "q75_ms": q75, "min_ms": min(values), "max_ms": max(values),
            "stdev_ms": statistics.stdev(values), "relative_iqr": relative,
            "noisy": relative > threshold}


def exact_files(left, right):
    with left.open("rb") as a, right.open("rb") as b:
        while True:
            chunk = a.read(64 * 1024)
            if chunk != b.read(64 * 1024):
                return False
            if not chunk:
                return True


def count_lines(path):
    lines = 0
    with path.open("rb") as stream:
        while chunk := stream.read(64 * 1024):
            lines += chunk.count(b"\n")
    return lines


def sanitized_environment():
    environment = os.environ.copy()
    for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTIONS", "CLASSPATH",
                 "GREP_OPTIONS", "GREP_COLORS", "POSIXLY_CORRECT"):
        environment.pop(name, None)
    environment.update(LC_ALL=utf8_locale(), PYTHONHASHSEED="0", PYTHONDONTWRITEBYTECODE="1")
    return environment


def plotting_python(environment):
    if sys.version_info < (3, 12):
        raise ValueError("Python 3.12 ou supérieur est requis par l'environnement de tracé figé.")
    directory = ROOT / ".cache/report-venv"
    executable = directory / "bin/python"
    if not executable.exists():
        subprocess.run([sys.executable, "-m", "venv", str(directory)], check=True, env=environment)
    requirement = ROOT / "scripts/requirements-report.txt"
    check = ("import importlib.metadata as m; from pathlib import Path; "
             "pairs=[line.split('==') for line in Path(__import__('sys').argv[1]).read_text().splitlines() "
             "if line and not line.startswith('#')]; "
             "assert all(m.version(name)==version for name,version in pairs)")
    result = subprocess.run([str(executable), "-c", check, str(requirement)],
                            capture_output=True, env=environment)
    if result.returncode:
        subprocess.run([str(executable), "-m", "pip", "install", "--disable-pip-version-check",
                        "--no-cache-dir", "-r", str(requirement)], check=True, env=environment)
    return executable


def prepare_inputs(work, profile):
    folder = work / "corpora"
    folder.mkdir()
    source = ROOT / profile["source"]
    if sha256_file(source) != profile["source_sha256"]:
        raise ValueError("Le corpus source diffère du profil versionné ; changer explicitement le protocole.")
    normalized = folder / "book-1.txt"
    prepare_corpus(source, normalized)
    scaled, _ = prepare_scaled_corpora(normalized, folder)
    paths = {f"book-{factor}": path for factor, path in scaled.items()}
    paths["synthetic"] = folder / "synthetic.txt"
    length = profile["synthetic"]["prefix_length"]
    block = ("a" * (length - 1) + "b\n" + "a" * length + "c\n"
             + "ab" * (length // 2) + "ac\n" + "x" * length + "\n" + "é" * 32 + "été\n")
    with paths["synthetic"].open("w", encoding="utf-8", newline="\n") as stream:
        for _ in range(profile["synthetic"]["blocks"]):
            stream.write(block)
    metadata = {key: {"bytes": path.stat().st_size, "sha256": sha256_file(path), "lines": count_lines(path)}
                for key, path in paths.items()}
    return paths, metadata


def validate_case(work, case, commands, environment, timeout):
    outputs = {}
    for engine in ("java", "grep"):
        path = work / f"{case['id']}-{engine}.out"
        with path.open("wb") as stream:
            result = subprocess.run(commands[engine], stdout=stream, stderr=subprocess.PIPE,
                                    env=environment, timeout=timeout)
        accepted = (0, 1) if engine == "grep" else (0,)
        if result.returncode not in accepted:
            raise ValueError(f"Validation {case['id']}/{engine} : {result.stderr.decode(errors='replace')}")
        if engine == "grep" and (result.returncode == 1) != (path.stat().st_size == 0):
            raise ValueError("Statut grep incohérent avec la sortie")
        outputs[engine] = path
    if not exact_files(outputs["java"], outputs["grep"]):
        raise ValueError(f"Lignes ou numéros différents : {case['id']}")
    info = {"case_id": case["id"], "matching_lines": count_lines(outputs["grep"]),
            "numbered_output_sha256": sha256_file(outputs["grep"]),
            "numbered_output_bytes": outputs["grep"].stat().st_size, "exact_byte_comparison": True}
    for path in outputs.values():
        path.unlink()
    return info


def collect_automata(profile, java_prefix, environment, destination):
    """Mesure la taille des graphes hors chronométrage, une seule fois par regex."""
    rows = []
    cache = {}
    for case in profile["experiments"]:
        if case["regex"] in cache:
            rows.append({"case_id": case["id"], **cache[case["regex"]]})
            continue
        command = java_prefix + ["com.sorbonne.benchmark.AutomatonProfile", case["regex"]]
        result = subprocess.run(command, capture_output=True, text=True, env=environment, check=True,
                                timeout=profile["jvm"]["timeout_seconds"])
        records = list(csv.DictReader(io.StringIO(result.stdout)))
        if len(records) != 1:
            raise ValueError(f"Profil structurel invalide : {case['id']}")
        raw = records[0]
        row = {"case_id": case["id"], **{key: int(value) for key, value in raw.items()}}
        if row["nfa_states"] < 1 or row["search_dfa_states"] < 1:
            raise ValueError(f"Automate vide inattendu : {case['id']}")
        cache[case["regex"]] = {key: value for key, value in row.items() if key != "case_id"}
        rows.append(row)
    write_csv(destination / "automata.csv", rows)
    return rows


def collect_cli(profile, cases, commands, expected, environment, destination):
    randomizer = random.Random(profile["seed"])
    rows = []
    sequence = 0
    # Toutes les observations, y compris les prépassages, sont conservées.
    for phase, blocks in (("warmup", profile["cli"]["warmups"]), ("measure", profile["cli"]["blocks"])):
        for block in range(1, blocks + 1):
            order = list(cases)
            randomizer.shuffle(order)
            for case in order:
                pairs = (balanced_orders(profile["cli"]["pairs_per_block"], randomizer)
                         if phase == "measure" else [tuple(randomizer.sample(["java", "grep"], 2))])
                for iteration, engines in enumerate(pairs, 1):
                    for position, engine in enumerate(engines, 1):
                        count, elapsed = run_count(commands[case["id"]][engine], environment,
                                                   profile["cli"]["timeout_seconds"], grep=engine == "grep")
                        if count != expected[case["id"]]:
                            raise ValueError(f"Comptage différent pendant {phase} : {case['id']}/{engine}")
                        sequence += 1
                        rows.append({"case_id": case["id"], "phase": phase, "block": block,
                                     "iteration": iteration, "sequence": sequence, "position": position,
                                     "engine": engine, "elapsed_ns": elapsed, "matching_lines": count})
                write_csv(destination / "cli.csv", rows)
            print(f"  CLI {phase} : bloc {block}/{blocks}", flush=True)
    return rows


def collect_jvm(profile, cases, java_prefix, paths, expected, environment, destination):
    randomizer = random.Random(profile["seed"] + 1)
    rows = []
    sequence = 0
    for fork in range(1, profile["jvm"]["forks"] + 1):
        order = list(cases)
        randomizer.shuffle(order)
        for case in order:
            command = java_prefix + ["com.sorbonne.benchmark.ReportBenchmark", str(paths[case["corpus"]]),
                                     case["regex"], case["strategy"], str(profile["jvm"]["warmups"]),
                                     str(profile["jvm"]["samples_per_fork"]), str(expected[case["id"]])]
            result = subprocess.run(command, capture_output=True, text=True, env=environment,
                                    timeout=profile["jvm"]["timeout_seconds"], check=True)
            records = list(csv.DictReader(io.StringIO(result.stdout)))
            expected_rows = profile["jvm"]["warmups"] + profile["jvm"]["samples_per_fork"]
            if len(records) != expected_rows:
                raise ValueError("Nombre d'observations JVM incorrect")
            sequence += 1
            for record in records:
                row = {key: value if key == "phase" else int(value) for key, value in record.items()}
                if row["phase"] not in ("warmup", "measure") or row["matching_lines"] != expected[case["id"]]:
                    raise ValueError("Observation JVM invalide")
                if row["total_ns"] != row["preparation_ns"] + row["scan_ns"] or any(
                        value < 0 for key, value in row.items() if key.endswith("_ns")):
                    raise ValueError("Durées JVM incohérentes")
                rows.append({"case_id": case["id"], "fork": fork, "sequence": sequence, **row})
            write_csv(destination / "jvm.csv", rows)
        print(f"  JVM échauffées : fork {fork}/{profile['jvm']['forks']}", flush=True)
    return rows


def summaries(profile, cli_rows, jvm_rows):
    cli_summary, jvm_summary = [], []
    for case in profile["experiments"]:
        identifier = case["id"]
        if case.get("cli", True):
            for engine in ("java", "grep"):
                values = [row["elapsed_ns"] / 1e6 for row in cli_rows if row["case_id"] == identifier
                          and row["engine"] == engine and row["phase"] == "measure"]
                if len(values) != profile["cli"]["blocks"] * profile["cli"]["pairs_per_block"]:
                    raise ValueError("Échantillon CLI incomplet")
                cli_summary.append({"case_id": identifier, "engine": engine, "unit": "process",
                                    **summarize(values, profile["noise_iqr_ratio"])})
        for metric in ("parsing_ns", "nfa_ns", "dfa_ns", "minimization_ns", "search_preparation_ns", "preparation_ns", "scan_ns", "total_ns"):
            means = []
            for fork in range(1, profile["jvm"]["forks"] + 1):
                values = [row[metric] / 1e6 for row in jvm_rows if row["case_id"] == identifier
                          and row["fork"] == fork and row["phase"] == "measure"]
                if len(values) != profile["jvm"]["samples_per_fork"]:
                    raise ValueError("Échantillon JVM incomplet")
                means.append(statistics.mean(values))
            jvm_summary.append({"case_id": identifier, "metric": metric, "unit": "fork_mean",
                                **summarize(means, profile["noise_iqr_ratio"])})
    return cli_summary, jvm_summary


def publish_transaction(replacements):
    """Remplacement avec sauvegardes et restauration si une opération échoue."""
    cache = ROOT / ".cache"
    cache.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="report-publish-", dir=cache) as temporary:
        changes = []
        try:
            for index, (source, destination) in enumerate(replacements):
                destination.parent.mkdir(parents=True, exist_ok=True)
                if destination.is_symlink():
                    raise ValueError(f"Destination symbolique refusée : {destination}")
                backup = Path(temporary) / str(index)
                entry = {"destination": destination, "backup": backup, "old": False, "new": False}
                changes.append(entry)
                if destination.exists():
                    os.replace(destination, backup)
                    entry["old"] = True
                os.replace(source, destination)
                entry["new"] = True
        except BaseException:
            for entry in reversed(changes):
                if entry["new"]:
                    if entry["destination"].is_dir():
                        shutil.rmtree(entry["destination"])
                    else:
                        entry["destination"].unlink()
                if entry["old"]:
                    os.replace(entry["backup"], entry["destination"])
            raise


def report_markdown(manifest, cli_summary, jvm_summary):
    profile = manifest["profile"]
    lines = ["# Campagne fixe du README", "", f"Exécution UTC : {manifest['finished_utc']}", "",
             f"Protocole : `{profile['protocol']}` ; empreinte `{manifest['profile_sha256']}`.", "",
             "Les données concernent les sources optimisées identifiées dans [campaign.json](campaign.json).",
             "DFAM applique la minimisation de Hopcroft au DFA de recherche avant indexation.", "",
             "KMP est comparé uniquement sur les littéraux. DFA et DFAM partagent le même DFA de recherche initial et la même indexation ; seule la minimisation change.",
             "Les motifs acceptant le mot vide utilisent le raccourci commun sans construire d'automate, même en DFA/DFAM.",
             "Le profil structurel construit les graphes à titre diagnostique, même pour KMP ou un motif nullable ; ce n'est pas le travail effectué par ces chemins chronométrés.", "",
             "## Commandes complètes", "",
             "Médiane et intervalle interquartile de 30 processus par moteur. Démarrage JVM inclus ; aucun point retiré.", "",
             "| Cas et regex exécutée | Corpus | Lignes | Java, médiane [Q1 ; Q3] ms | GNU grep, médiane [Q1 ; Q3] ms |",
             "| :--- | :--- | ---: | ---: | ---: |"]
    lookup = {(row["case_id"], row["engine"]): row for row in cli_summary}
    for case in profile["experiments"]:
        if not case.get("cli", True):
            continue
        cells = []
        for engine in ("java", "grep"):
            row = lookup[case["id"], engine]
            cells.append(f"{row['median_ms']:.2f} [{row['q25_ms']:.2f} ; {row['q75_ms']:.2f}]")
        lines.append(f"| {case['label']}<br>`{case['regex'].replace(chr(124), chr(92)+chr(124))}` | {case['corpus']} | {manifest['validation'][case['id']]['matching_lines']} | {' | '.join(cells)} |")
    lines += comparison_markdown(profile, cli_summary, jvm_summary)
    lines += ["", "## Taille structurelle des automates", "",
              "Ces comptes sont collectés une seule fois hors chronométrage. Ils permettent de relier le coût de préparation à la taille réellement construite.", "",
              "| Cas | Longueur regex | États NFA | Transitions NFA | États DFA recherche | Transitions DFA recherche | États DFAM | Transitions DFAM |",
              "| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for case in profile["experiments"]:
        shape = manifest["automata"][case["id"]]
        lines.append(f"| {case['label']} | {shape['regex_length']} | {shape['nfa_states']} | {shape['nfa_transitions']} | {shape['search_dfa_states']} | {shape['search_dfa_transitions']} | {shape['dfam_states']} | {shape['dfam_transitions']} |")
    lines += ["", "## JVM échauffées", "",
              "5 JVM distinctes par cas ; 10 prépassages puis 10 mesures par JVM. Chaque mesure reconstruit le motif et lit le fichier.",
              "Les statistiques sont calculées sur les **5 moyennes de forks**, pas sur 50 répétitions prétendument indépendantes.",
              "Le parcours inclut le décodage et les IO ; il ne mesure pas seulement les transitions en mémoire.", "",
              "| Cas et regex exécutée | Préparation, médiane des forks ms | Parcours, médiane des forks ms | Total, médiane des forks ms |",
              "| :--- | ---: | ---: | ---: |"]
    lookup_jvm = {(row["case_id"], row["metric"]): row for row in jvm_summary}
    for case in profile["experiments"]:
        values = [lookup_jvm[case["id"], metric]["median_ms"] for metric in ("preparation_ns", "scan_ns", "total_ns")]
        regex_label = "`" + case['regex'].replace("|", "\\|") + "`"
        lines.append(f"| {case['label']}<br>{regex_label} | " + " | ".join(f"{value:.3f}" for value in values) + " |")
    noisy = [f"{row['case_id']}/{row.get('engine', row.get('metric'))}" for row in cli_summary + jvm_summary if row["noisy"]]
    lines += ["", "## Dispersion et limites", "",
              f"Signalement fixé avant mesure : IQR/médiane > {profile['noise_iqr_ratio']:.0%}. Ces observations sont conservées.",
              "Séries signalées : " + (", ".join(noisy) if noisy else "aucune") + ".", "",
              "Les intervalles représentés sont des dispersions, pas des intervalles de confiance. Aucun classement universel n'est déduit.",
              "Cache de fichiers sollicité, machine non isolée, charge de fond et température non contrôlées. L'échauffement fixe ne prouve pas une convergence du JIT.",
              "Les cas complexes sont bornés ; cette campagne ne supprime ni ne couvre tous les cas d'explosion exponentielle.", "",
              "Données brutes locales (non versionnées) : `target/report/results/automata.csv`, `cli.csv`, `jvm.csv` et leurs résumés.",
              "Sans `--purge`, les prépassages, ordres d'exécution, résultats exacts et empreintes sont conservés localement. Avec `--purge`, les CSV/TXT sont supprimés seulement après validation et publication des assets.", ""]
    lines += ["## Regex exactes", "", "Ces chaînes sont celles transmises aux moteurs ; aucune syntaxe de quantification n'est ajoutée.", ""]
    for case in profile["experiments"]:
        lines += [f"<details><summary>{case['id']} — {len(case['regex'])} caractères — {case['corpus']}</summary>",
                  "", "```text", case["regex"], "```", "", "</details>", ""]
    return "\n".join(lines)



def comparison_markdown(profile, cli_summary, jvm_summary):
    groups = {}
    for case in profile["experiments"]:
        groups.setdefault(case["comparison"], {})[case["strategy"]] = case
    cli = {(row["case_id"], row["engine"]): row for row in cli_summary}
    jvm = {(row["case_id"], row["metric"]): row for row in jvm_summary}
    lines = ["", "## Comparaison directe des moteurs", "",
             "Même regex et même corpus sur chaque ligne. N/A : KMP ne traite pas les opérateurs regex.",
             "GNU grep -E est l'équivalent d'egrep ; sa colonne utilise les 30 mesures du cas DFA associé, sans mélanger les séries.", "",
             "| Regex / corpus | KMP ms | DFA ms | DFAM ms | GNU grep -E ms |",
             "| :--- | ---: | ---: | ---: | ---: |"]
    for strategies in groups.values():
        case = strategies["DFA"]
        if not case.get("cli", True):
            continue
        values = [f"{cli[strategies[key]['id'], 'java']['median_ms']:.3f}" if key in strategies else "N/A"
                  for key in ("KMP", "DFA", "DFAM")]
        values.append(f"{cli[case['id'], 'grep']['median_ms']:.3f}")
        regex = case["regex"].replace("|", "\\|")
        lines.append(f"| `{regex}` / {case['corpus']} | " + " | ".join(values) + " |")
    lines += ["", "### Effet propre de la minimisation dans les JVM", "",
              "Chaque valeur est la médiane des cinq moyennes de forks. Les médianes de phases ne sont pas additives.", "",
              "| Regex / corpus | Hopcroft ms | Préparation DFA / DFAM ms | Parcours DFA / DFAM ms | Total DFA / DFAM ms |",
              "| :--- | ---: | ---: | ---: | ---: |"]
    for strategies in groups.values():
        dfa, dfam = strategies["DFA"], strategies["DFAM"]
        values = [f"{jvm[dfam['id'], 'minimization_ns']['median_ms']:.3f}"]
        for metric in ("preparation_ns", "scan_ns", "total_ns"):
            values.append(" / ".join(f"{jvm[c['id'], metric]['median_ms']:.3f}" for c in (dfa, dfam)))
        regex = dfa["regex"].replace("|", "\\|")
        lines.append(f"| `{regex}` / {dfa['corpus']} | " + " | ".join(values) + " |")
    return lines

def publish_assets(report_directory):
    """Publie atomiquement les assets référencés par README/docs après validation complète."""
    results = report_directory / "results"
    figures = report_directory / "figures"
    manifest, _, _, cli_summary, jvm_summary = load_validated_results(results)
    provenance = json.loads((figures / "provenance.json").read_text(encoding="utf-8"))
    if provenance["campaign_sha256"] != sha256_file(results / "campaign.json"):
        raise ValueError("Les figures ne correspondent pas aux mesures")
    if fingerprint() != manifest["source_sha256"]:
        raise ValueError("Les sources ont changé depuis la mesure ; publication refusée")
    for name in FIGURES:
        for suffix in ("svg", "png"):
            filename = f"{name}.{suffix}"
            if sha256_file(figures / filename) != provenance["figures"][filename]:
                raise ValueError(f"Figure modifiée : {filename}")

    destination = ROOT / "docs/assets"
    cache = ROOT / ".cache"
    cache.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="publish-assets-", dir=cache) as temporary:
        staged = Path(temporary) / "assets"
        # Recréer l'ensemble publié depuis zéro : aucun asset obsolète ne survit
        # silencieusement à un changement du protocole.
        staged.mkdir()
        for name in FIGURES:
            shutil.copyfile(figures / f"{name}.svg", staged / f"{name}.svg")
            (staged / f"{name}.png").unlink(missing_ok=True)
        snapshot = {key: manifest[key] for key in (
            "schema_version", "started_utc", "finished_utc", "profile", "profile_sha256",
            "source_sha256", "git_head", "git_dirty", "java_version", "grep_version", "locale",
            "python", "os", "cpu", "logical_cpu_count", "corpora", "validation", "automata",
            "cli_scope", "jvm_scope", "minimization_implemented")}
        snapshot.update(
            cli_summary=cli_summary,
            jvm_summary=jvm_summary,
            figures={f"{name}.svg": provenance["figures"][f"{name}.svg"] for name in FIGURES},
            raw_data_policy="Regenerated by scripts/report-campaign.sh; optional local retention in target/report/results")
        write_json(staged / "benchmark.json", snapshot)
        report_text = report_markdown(manifest, cli_summary, jvm_summary).replace(
            "# Campagne fixe du README", "# Référence expérimentale reproductible")
        report_text = report_text.replace("[campaign.json](campaign.json)", "[benchmark.json](benchmark.json)")
        (staged / "benchmark.md").write_text(report_text, encoding="utf-8")
        (staged / "provenance.json").unlink(missing_ok=True)
        publish_transaction([(staged, destination)])


def validate_markdown_assets():
    """Vérifie images et liens Markdown qui ciblent docs/assets/."""
    import re
    missing = []
    documents = [ROOT / "README.md", *sorted((ROOT / "docs").glob("*.md"))]
    asset_root = (ROOT / "docs/assets").resolve()
    pattern = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
    for document in documents:
        text = document.read_text(encoding="utf-8")
        for target in pattern.findall(text):
            if "://" in target or target.startswith(("data:", "mailto:", "#")):
                continue
            clean = target.split("#", 1)[0].strip().strip("<>")
            if not clean:
                continue
            path = (document.parent / clean).resolve()
            if path == asset_root or path.is_relative_to(asset_root):
                if not path.exists():
                    missing.append(f"{document.relative_to(ROOT)} -> {clean}")
    if missing:
        raise ValueError("Assets Markdown manquants : " + "; ".join(missing))


def purge_raw_results(report_directory):
    """Supprime les CSV/TXT générés après publication, sans toucher aux assets Markdown."""
    results = report_directory / "results"
    removed = []
    if not results.exists():
        return removed
    for suffix in ("*.csv", "*.txt"):
        for path in sorted(results.rglob(suffix)):
            if path.is_symlink():
                path.unlink()
            elif path.is_file():
                path.unlink()
            removed.append(str(path.relative_to(ROOT)))
    write_json(results / "purge.json", {
        "purged_utc": datetime.now(timezone.utc).isoformat(),
        "removed": removed,
        "note": "Les assets docs/assets ont été publiés et validés avant cette purge. Relancer la campagne pour régénérer les données brutes."
    })
    return removed


def clean_generated():
    """Nettoie uniquement les sorties locales de benchmark, jamais les assets publiés."""
    target = ROOT / "target"
    if target.is_symlink():
        raise ValueError("target ne doit pas être un lien symbolique")
    for name in ("report", "report-work", "report-corpora", "benchmarks"):
        path = target / name
        if path.is_symlink():
            path.unlink()
        elif path.exists():
            shutil.rmtree(path)


def load_validated_results(directory):
    """Vérifie les empreintes et recalcule les résumés avant tracé ou publication."""
    manifest = json.loads((directory / "campaign.json").read_text(encoding="utf-8"))
    if manifest.get("schema_version") != 2:
        raise ValueError("Format de campagne incompatible")
    required = {"profile.json", "automata.csv", "cli.csv", "jvm.csv", "cli-summary.csv", "jvm-summary.csv", "validation.txt"}
    if set(manifest["integrity"]) != required:
        raise ValueError("Manifeste d'intégrité incomplet")
    for name, digest in manifest["integrity"].items():
        path = (directory / name).resolve()
        if not path.is_relative_to(directory.resolve()) or sha256_file(path) != digest:
            raise ValueError(f"Données modifiées ou incohérentes : {name}")
    if sha256_file(directory / "profile.json") != manifest["profile_sha256"]:
        raise ValueError("Profil différent de l'empreinte publiée")
    if load_profile(directory / "profile.json") != manifest["profile"]:
        raise ValueError("Profil développé incohérent")
    def read_rows(name):
        with (directory / name).open(newline="", encoding="utf-8") as stream:
            return list(csv.DictReader(stream))
    automata_rows = [{key: value if key == "case_id" else int(value) for key, value in row.items()}
                     for row in read_rows("automata.csv")]
    expected_automata = manifest.get("automata", {})
    if {row["case_id"]: row for row in automata_rows} != expected_automata:
        raise ValueError("Profil structurel des automates incohérent")
    if set(expected_automata) != {case["id"] for case in manifest["profile"]["experiments"]}:
        raise ValueError("Profil structurel incomplet")
    def raw_rows(name):
        text_columns = {"case_id", "phase", "engine"}
        return [{key: value if key in text_columns else int(value) for key, value in row.items()}
                for row in read_rows(name)]
    cli = raw_rows("cli.csv")
    jvm = raw_rows("jvm.csv")
    validate_observations(manifest, cli, jvm)
    cli_summary, jvm_summary = summaries(manifest["profile"], cli, jvm)
    for name, expected in (("cli-summary.csv", cli_summary), ("jvm-summary.csv", jvm_summary)):
        if read_rows(name) != [{key: str(value) for key, value in row.items()} for row in expected]:
            raise ValueError(f"Résumé différent des observations : {name}")
    return manifest, cli, jvm, cli_summary, jvm_summary


def validate_observations(manifest, cli, jvm):
    """Refuse trous, doublons, comptes erronés et faux regroupements de forks."""
    profile = manifest["profile"]
    cases = {case["id"]: case for case in profile["experiments"]}
    expected_cli, expected_jvm = set(), set()
    for identifier, case in cases.items():
        if case.get("cli", True):
            for phase, blocks, pairs in (("warmup", profile["cli"]["warmups"], 1),
                    ("measure", profile["cli"]["blocks"], profile["cli"]["pairs_per_block"])):
                expected_cli.update((identifier, phase, block, iteration, engine)
                    for block in range(1, blocks + 1) for iteration in range(1, pairs + 1)
                    for engine in ("java", "grep"))
        expected_jvm.update((identifier, fork, phase, iteration)
            for fork in range(1, profile["jvm"]["forks"] + 1)
            for phase, count in (("warmup", profile["jvm"]["warmups"]),
                                 ("measure", profile["jvm"]["samples_per_fork"]))
            for iteration in range(1, count + 1))
    actual_cli = {(r["case_id"], r["phase"], r["block"], r["iteration"], r["engine"]) for r in cli}
    actual_jvm = {(r["case_id"], r["fork"], r["phase"], r["iteration"]) for r in jvm}
    if actual_cli != expected_cli or len(cli) != len(expected_cli):
        raise ValueError("Observations CLI incomplètes ou dupliquées")
    if actual_jvm != expected_jvm or len(jvm) != len(expected_jvm):
        raise ValueError("Observations JVM incomplètes ou dupliquées")
    if [r["sequence"] for r in cli] != list(range(1, len(cli) + 1)):
        raise ValueError("Ordre CLI incohérent")
    for first, second in zip(cli[::2], cli[1::2]):
        if (first["position"], second["position"]) != (1, 2) or first["engine"] == second["engine"] or any(
                first[key] != second[key] for key in ("case_id", "phase", "block", "iteration")):
            raise ValueError("Paire CLI incohérente")
    for identifier, case in cases.items():
        if case.get("cli", True):
            for block in range(1, profile["cli"]["blocks"] + 1):
                first_java = sum(r["position"] == 1 for r in cli if r["case_id"] == identifier
                    and r["phase"] == "measure" and r["block"] == block and r["engine"] == "java")
                if first_java * 2 != profile["cli"]["pairs_per_block"]:
                    raise ValueError("Ordre des moteurs déséquilibré")
    groups = {}
    for row in cli + jvm:
        if row["matching_lines"] != manifest["validation"][row["case_id"]]["matching_lines"]:
            raise ValueError("Comptage différent de la validation")
        if any(value < 0 for key, value in row.items() if key.endswith("_ns")):
            raise ValueError("Durée négative")
    for row in jvm:
        if cases[row["case_id"]]["strategy"] in {"DFA", "KMP"} and row["minimization_ns"] != 0:
            raise ValueError("La minimisation doit être absente du témoin DFA/KMP")
        group = (row["case_id"], row["fork"])
        if groups.setdefault(group, row["sequence"]) != row["sequence"]:
            raise ValueError("Fork réparti sur plusieurs processus")
        if row["total_ns"] != row["preparation_ns"] + row["scan_ns"] or row["total_lines"] != manifest[
                "corpora"][cases[row["case_id"]]["corpus"]]["lines"]:
            raise ValueError("Observation JVM incohérente")
    if set(groups.values()) != set(range(1, len(groups) + 1)):
        raise ValueError("Identifiants des processus JVM incohérents")


def main():
    parser = argparse.ArgumentParser(description="Pipeline expérimental reproductible du projet")
    parser.add_argument("--purge", action="store_true",
                        help="après publication, supprimer les CSV/TXT générés localement")
    parser.add_argument("--allow-dirty", action="store_true",
                        help="autoriser exceptionnellement une campagne sur un arbre Git non propre")
    args = parser.parse_args()
    cache = ROOT / ".cache"
    cache.mkdir(exist_ok=True)
    with (cache / "report.lock").open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError("Une autre génération du rapport est en cours.") from error
        execute(cache, purge=args.purge, allow_dirty=args.allow_dirty)

def execute(cache, purge=False, allow_dirty=False):
    profile = load_profile()
    environment = sanitized_environment()
    git_status = subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True)
    if git_status and not allow_dirty:
        raise ValueError("Arbre Git non propre : committer/stasher les changements avant une campagne, ou utiliser --allow-dirty pour un essai non publiable.")
    java = str(Path(environment["JAVA_HOME"]) / "bin/java")
    java_version = subprocess.run([java, "-version"], capture_output=True, text=True, env=environment, check=True).stderr
    if f'version "{profile["java_release"]}.' not in java_version:
        raise ValueError(f"Ce profil exige Java {profile['java_release']}, version détectée : {java_version}")
    grep, grep_version = find_grep(None)
    print("1/7 Préflight, dépendances de tracé, nettoyage, compilation et tests", flush=True)
    plot_python = plotting_python(environment)
    inputs_before = fingerprint()
    clean_generated()
    build = ["mvn", "--batch-mode", "--no-transfer-progress", "-Dstyle.color=never"]
    if environment.get("MAVEN_OFFLINE") == "1":
        build.append("--offline")
    build += ["-DskipTests=false", "-Dmaven.test.skip=false", "-Dmaven.test.failure.ignore=false", "clean", "test"]
    with (cache / "report-build.log").open("w") as log:
        subprocess.run(build, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, env=environment, check=True)
        subprocess.run([sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-v"],
                       cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, env=environment, check=True)
    work = ROOT / "target/report-work"
    work.mkdir(parents=True)
    destination = work / "results"
    destination.mkdir()
    classes = work / "classes"
    shutil.copytree(ROOT / "target/classes", classes)
    subprocess.run([str(Path(environment["JAVA_HOME"]) / "bin/javac"), "-cp", str(classes), "-d", str(classes),
                    str(ROOT / "scripts/java/com/sorbonne/benchmark/ReportBenchmark.java"),
                    str(ROOT / "scripts/java/com/sorbonne/benchmark/AutomatonProfile.java")], check=True, env=environment)
    class_hashes = tree_hashes(classes)
    java_prefix = [java, *profile["java_options"], "-cp", str(classes)]
    paths, corpus_metadata = prepare_inputs(work, profile)
    started = datetime.now(timezone.utc).isoformat()
    validation, commands = {}, {}
    print(f"2/7 Validation exacte des lignes et numéros pour {len(profile['experiments'])} cas", flush=True)
    for case in profile["experiments"]:
        common = [str(paths[case["corpus"]]), case["regex"], case["strategy"]]
        check_commands = {"java": java_prefix + ["com.sorbonne.Main", "--print", *common],
                          "grep": [grep, "--color=never", "-E", "-n", "--", case["regex"], common[0]]}
        validation[case["id"]] = validate_case(work, case, check_commands, environment, profile["cli"]["timeout_seconds"])
        commands[case["id"]] = {"java": java_prefix + ["com.sorbonne.Main", "--count", *common],
                                 "grep": [grep, "-E", "-c", "--", case["regex"], common[0]]}
    expected = {key: value["matching_lines"] for key, value in validation.items()}
    print("3/7 Profil structurel NFA/DFA/DFAM hors chronométrage", flush=True)
    automata_rows = collect_automata(profile, java_prefix, environment, destination)
    print("4/7 Commandes complètes : blocs équilibrés, processus indépendants", flush=True)
    cli_rows = collect_cli(profile, [case for case in profile["experiments"] if case.get("cli", True)],
                           commands, expected, environment, destination)
    print("5/7 Pipelines Java : 5 JVM indépendantes par cas", flush=True)
    jvm_rows = collect_jvm(profile, profile["experiments"], java_prefix, paths, expected, environment, destination)
    cli_summary, jvm_summary = summaries(profile, cli_rows, jvm_rows)
    write_csv(destination / "cli-summary.csv", cli_summary)
    write_csv(destination / "jvm-summary.csv", jvm_summary)
    shutil.copyfile(PROFILE, destination / "profile.json")
    shutil.copyfile(cache / "report-build.log", destination / "validation.txt")
    manifest = {"schema_version": 2, "started_utc": started, "finished_utc": datetime.now(timezone.utc).isoformat(),
                "profile": profile, "profile_sha256": sha256_file(PROFILE), "source_sha256": inputs_before,
                "git_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                "git_dirty": bool(git_status),
                "java_version": java_version.strip(), "grep_version": grep_version, "locale": environment["LC_ALL"],
                "python": sys.version, "os": platform.platform(), "cpu": platform.processor(),
                "logical_cpu_count": os.cpu_count(), "load_average_at_end": os.getloadavg(),
                "class_sha256": class_hashes, "corpora": corpus_metadata, "validation": validation,
                "automata": {row["case_id"]: row for row in automata_rows},
                "commands": commands, "outlier_filter": None, "minimization_implemented": True,
                "cli_scope": "new process, JVM startup + preparation + IO + count",
                "jvm_scope": "fresh preparation + IO per invocation, warmed process, summarized by fork",
                "integrity": tree_hashes(destination)}
    write_json(destination / "campaign.json", manifest)
    (destination / "report.md").write_text(report_markdown(manifest, cli_summary, jvm_summary), encoding="utf-8")
    print("6/7 Tracé depuis les seules observations validées", flush=True)
    assets = work / "figures"
    assets.mkdir()
    environment["MPLCONFIGDIR"] = str(cache / "matplotlib")
    subprocess.run([str(plot_python), str(ROOT / "scripts/plot-report.py"), str(destination), "--output", str(assets)],
                   check=True, env=environment)
    for name in FIGURES:
        for suffix in ("svg", "png"):
            if not (assets / f"{name}.{suffix}").is_file():
                raise ValueError("Une figure attendue manque")
    write_json(assets / "provenance.json", {"campaign": "target/report/results/campaign.json",
               "campaign_sha256": sha256_file(destination / "campaign.json"), "profile_sha256": manifest["profile_sha256"],
               "generated_utc": datetime.now(timezone.utc).isoformat(),
               "figures": {f"{name}.{suffix}": sha256_file(assets / f"{name}.{suffix}")
                           for name in FIGURES for suffix in ("svg", "png")}})
    if fingerprint() != inputs_before or tree_hashes(classes) != class_hashes:
        raise ValueError("Sources ou classes modifiées pendant la campagne : publication annulée.")
    if any(sha256_file(paths[key]) != value["sha256"] for key, value in corpus_metadata.items()):
        raise ValueError("Corpus modifié pendant la campagne")
    print("7/7 Publication atomique des résultats et assets Markdown", flush=True)
    complete = work / "complete"
    complete.mkdir()
    os.replace(destination, complete / "results")
    os.replace(assets, complete / "figures")
    os.replace(complete, ROOT / "target/report")
    shutil.rmtree(work)
    if git_status:
        print("Essai --allow-dirty : résultats locaux conservés, docs/assets non modifié.", flush=True)
    else:
        publish_assets(ROOT / "target/report")
        validate_markdown_assets()
        shutil.rmtree(ROOT / "target/report/figures")
        print("Assets reproduits atomiquement dans docs/assets/.", flush=True)
    if purge:
        removed = purge_raw_results(ROOT / "target/report")
        print(f"Purge : {len(removed)} fichiers CSV/TXT supprimés après validation/publication.", flush=True)
    print("Rapport local : target/report/results/report.md", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(f"ERREUR : {error}\nLa publication des assets est transactionnelle. Voir .cache/report-build.log et target/report-work/.", file=sys.stderr)
        sys.exit(1)
