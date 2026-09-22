#!/usr/bin/env python3
"""Reproduit la campagne publiée : motifs variés puis même livre répété 1, 8 et 32 fois."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]


def main():
    """Crée des corpus temporaires reproductibles et exécute les expériences en série."""
    parser = argparse.ArgumentParser(prog="scripts/report-campaign.sh", description=__doc__)
    parser.add_argument("output", type=Path, help="nouveau dossier de résultats, jamais écrasé")
    parser.add_argument("--runs", type=int, default=20, help="répétitions par moteur et expérience")
    args = parser.parse_args()
    if args.runs < 2:
        parser.error("Au moins deux répétitions sont nécessaires pour l'écart type.")
    destination = args.output.resolve()
    destination.mkdir(parents=True, exist_ok=False)
    corpus_directory = ROOT / "target/report-corpora"
    corpus_directory.mkdir(parents=True, exist_ok=True)
    source = ROOT / "Samples/PrideAndPrejudice.txt"
    # Le séparateur final garantit que deux copies ne fusionnent jamais leurs dernières lignes.
    with source.open(encoding="utf-8", newline=None) as reader:
        normalized = reader.read()
    if normalized and not normalized.endswith("\n"):
        normalized += "\n"
    corpora = {1: source}
    for factor in (8, 32):
        path = corpus_directory / f"pride-{factor}x.txt"
        with path.open("w", encoding="utf-8", newline="\n") as writer:
            for _ in range(factor):
                writer.write(normalized)
        corpora[factor] = path

    # AUTO est volontairement comparé à AUTOMATON sur le même mot littéral.
    experiments = [
        ("literal-auto", "Littéral · KMP", "Elizabeth", "AUTO", 1),
        ("literal-automaton", "Littéral · automate", "Elizabeth", "AUTOMATON", 1),
        ("alternative", "Alternative", "Elizabeth|Darcy", "AUTO", 1),
        ("wildcard", "Point + étoile", "Eli.*beth", "AUTO", 1),
        ("absent", "Littéral absent", "ZZZ_NOT_PRESENT_2026", "AUTO", 1),
        ("nullable", "Mot vide accepté", "a*", "AUTO", 1),
        ("scale-auto-8", "KMP ×8", "Elizabeth", "AUTO", 8),
        ("scale-automaton-8", "Automate ×8", "Elizabeth", "AUTOMATON", 8),
        ("scale-auto-32", "KMP ×32", "Elizabeth", "AUTO", 32),
        ("scale-automaton-32", "Automate ×32", "Elizabeth", "AUTOMATON", 32),
    ]
    tracked_inputs = sorted(list((ROOT / "src/main/java").rglob("*.java"))
                            + list((ROOT / "scripts").rglob("*.py"))
                            + list((ROOT / "scripts").rglob("*.sh"))
                            + [ROOT / "pom.xml", ROOT / "run.sh"])
    manifest = {
        "started_utc": datetime.now(timezone.utc).isoformat(),
        "os": platform.platform(), "architecture": platform.machine(),
        "logical_cpu_count": os.cpu_count(), "python": platform.python_version(),
        "git_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "git_dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT)),
        "source_sha256": {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
                          for path in tracked_inputs},
        "original_corpus": str(source.relative_to(ROOT)),
        "original_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "original_bytes": source.stat().st_size,
        "normalized_lines": normalized.count("\n"),
        "replication": "UTF-8, CRLF/CR -> LF, trailing LF if absent, concatenated copies",
        "runs_per_engine": args.runs, "warmups_per_engine": 3, "seed": 42,
        "experiments": [],
    }
    for identifier, label, regex, strategy, factor in experiments:
        print(f"\n>>> {identifier} ({args.runs} mesures par moteur)", flush=True)
        command = [sys.executable, str(ROOT / "scripts/lib/compare_egrep.py"),
                   str(corpora[factor]), regex, "--strategy", strategy,
                   "--runs", str(args.runs), "--warmups", "3", "--seed", "42",
                   "--output-dir", str(destination / identifier)]
        with (destination / f"{identifier}.txt").open("w", encoding="utf-8") as output:
            subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, check=True)
        manifest["experiments"].append({"id": identifier, "label": label, "regex": regex,
                                       "strategy": strategy, "factor": factor})
        print((destination / identifier / "summary.csv").read_text(), flush=True)
    manifest["finished_utc"] = datetime.now(timezone.utc).isoformat()
    (destination / "campaign.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Campagne terminée : {destination}")


if __name__ == "__main__":
    main()
