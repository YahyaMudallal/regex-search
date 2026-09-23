#!/usr/bin/env python3
"""Compare Java à GNU grep -E sur un même texte et exporte les mesures brutes.

L'interface publique est scripts/compare-egrep.sh. Aucune dépendance Python externe.
Les durées englobent chaque processus, sa préparation, ses IO et son comptage.
"""

import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import random
import shutil
import statistics
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone

PROJECT_ROOT = Path(__file__).resolve().parents[2]


def validate_expression(expression):
    """Accepte le sous-ensemble commun du projet sur un motif ASCII."""
    if not expression:
        raise ValueError("Le parseur du projet refuse une expression vide.")
    depth, expects_atom, starred, escaped = 0, True, False, False
    for char in expression:
        if char in "\r\n\0" or ord(char) > 0x7f:
            raise ValueError("La regex doit etre ASCII et tenir sur une seule ligne.")
        if escaped:
            if char not in ".*|()\\":
                raise ValueError("Echappements communs autorises : \\. \\* \\| \\( \\) et \\\\.")
            escaped, expects_atom, starred = False, False, False
        elif char == "\\":
            escaped = True
        elif char in "+?[]{}^$":
            raise ValueError(f"Operateur non pris en charge par le projet : {char!r}")
        elif char == "(":
            depth += 1
            expects_atom, starred = True, False
        elif char == ")":
            if depth == 0 or expects_atom:
                raise ValueError("Parenthese incorrecte ou groupe vide.")
            depth -= 1
            expects_atom, starred = False, False
        elif char == "|":
            if expects_atom:
                raise ValueError("Une alternative doit avoir deux operandes.")
            expects_atom, starred = True, False
        elif char == "*":
            if expects_atom or starred:
                raise ValueError("Une etoile doit suivre un operande ; les etoiles repetees sont exclues.")
            starred = True
        else:
            expects_atom, starred = False, False
    if escaped or depth or expects_atom:
        raise ValueError("Expression incomplete ou parentheses desequilibrees.")


def prepare_corpus(source, destination):
    """Copie les octets et normalise CRLF/CR vers LF, hors mesure."""
    digest = hashlib.sha256()
    size = 0
    pending_cr = False
    with source.open("rb") as reader, destination.open("wb") as writer:
        while chunk := reader.read(64 * 1024):
            output = bytearray()
            index = 0
            if pending_cr:
                output.append(0x0A)
                if chunk and chunk[0] == 0x0A:
                    index = 1
                pending_cr = False
            while index < len(chunk):
                value = chunk[index]
                if value == 0x0D:
                    if index + 1 < len(chunk):
                        output.append(0x0A)
                        if chunk[index + 1] == 0x0A:
                            index += 1
                    else:
                        pending_cr = True
                else:
                    output.append(value)
                index += 1
            if output:
                writer.write(output)
                digest.update(output)
                size += len(output)
        if pending_cr:
            writer.write(b"\n")
            digest.update(b"\n")
            size += 1
    return {"bytes": size, "sha256": digest.hexdigest(), "normalization": "octets, CRLF/CR -> LF"}


def find_grep(requested):
    """Choisit GNU grep, y compris ggrep sur macOS, et conserve sa version exacte."""
    executable = shutil.which(requested) if requested else shutil.which("ggrep") or shutil.which("grep")
    if not executable:
        raise ValueError("GNU grep introuvable ; installer GNU grep ou préciser --grep /chemin/grep.")
    version = subprocess.run([executable, "--version"], capture_output=True, text=True, check=False)
    if version.returncode or "GNU grep" not in version.stdout:
        raise ValueError("GNU grep est requis pour comparer à la version Linux ; utiliser --grep ou ggrep.")
    return executable, version.stdout.splitlines()[0]


def run_count(command, environment, timeout, grep=False):
    """Chronomètre un processus complet et valide sa sortie numérique.

    Le statut 1 de grep signifie zéro correspondance, pas une erreur. Tout autre
    échec, résultat inattendu ou dépassement du délai interrompt l'expérience.
    """
    start = time.perf_counter_ns()
    completed = subprocess.run(command, env=environment, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, timeout=timeout, check=False)
    duration = time.perf_counter_ns() - start
    output = completed.stdout.strip()
    accepted = (0, 1) if grep else (0,)
    if completed.returncode not in accepted or not output.isdigit():
        detail = completed.stderr.decode(errors="replace").strip()
        raise ValueError(f"Échec de {command[0]} (statut {completed.returncode}) : {detail or output!r}")
    count = int(output)
    if grep and (completed.returncode == 1) != (count == 0):
        raise ValueError("Statut et comptage de grep incohérents.")
    return count, duration


def positive_int(value):
    """Valide un nombre strictement positif pour les répétitions."""
    number = int(value)
    if number < 1:
        raise argparse.ArgumentTypeError("La valeur doit être positive.")
    return number


def parse_args():
    """Décrit les paramètres du protocole, avec des valeurs par défaut reproductibles."""
    parser = argparse.ArgumentParser(prog="scripts/compare-egrep.sh", description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("file", type=Path, help="fichier a parcourir, relatif au repertoire courant")
    parser.add_argument("regex", help="regex commune au projet et à grep, à protéger avec des guillemets")
    parser.add_argument("--strategy", choices=("AUTO", "KMP", "DFA", "DFAM", "AUTOMATON"), default="AUTO")
    parser.add_argument("--runs", type=positive_int, default=10, help="mesures par moteur (défaut : 10)")
    parser.add_argument("--warmups", type=int, default=3, help="passages préalables non mesurés (défaut : 3)")
    parser.add_argument("--seed", type=int, default=42, help="graine de l'ordre aléatoire (défaut : 42)")
    parser.add_argument("--timeout", type=float, default=60, help="délai maximal par processus en secondes")
    parser.add_argument("--grep", help="exécutable GNU grep ; cherche ggrep puis grep par défaut")
    parser.add_argument("--output-dir", type=Path, help="nouveau dossier recevant CSV et métadonnées")
    args = parser.parse_args()
    if args.warmups < 0 or not math.isfinite(args.timeout) or args.timeout <= 0:
        parser.error("warmups doit être positif ou nul et timeout strictement positif et fini.")
    return args


def main():
    """Vérifie les comptages, répète les processus puis exporte les résultats réussis."""
    args = parse_args()
    validate_expression(args.regex)
    source = args.file.resolve(strict=True)
    if not source.is_file():
        raise ValueError("Le chemin doit désigner un fichier ordinaire.")
    grep, grep_version = find_grep(args.grep)
    environment = os.environ.copy()
    environment["LC_ALL"] = "C"
    for name in ("GREP_OPTIONS", "GREP_COLORS", "POSIXLY_CORRECT"):
        environment.pop(name, None)
    java = str(Path(environment["JAVA_HOME"]) / "bin/java")
    java_version = subprocess.run([java, "-version"], capture_output=True, text=True, check=True).stderr.strip()
    destination = args.output_dir or PROJECT_ROOT / "target" / "benchmarks" / (
        datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + f"-{os.getpid()}")
    # Ne pas écraser un résultat existant, même en cas d'erreur ultérieure.
    destination.mkdir(parents=True, exist_ok=False)
    rows = []
    randomizer = random.Random(args.seed)
    with tempfile.TemporaryDirectory(prefix="regex-benchmark-") as temporary:
        corpus = Path(temporary) / "corpus.txt"
        corpus_info = prepare_corpus(source, corpus)
        commands = {
            "java": [java, "-cp", str(PROJECT_ROOT / "target/classes"), "com.sorbonne.Main",
                     "--count", str(corpus), args.regex, args.strategy],
            "grep": [grep, "-a", "-E", "-c", "--", args.regex, str(corpus)],
        }
        print(f"Source : {source}\nCorpus commun : {corpus_info['bytes']} octets / LF", flush=True)
        print(f"Référence : {grep_version} | locale : {environment['LC_ALL']}", flush=True)
        print("Mesure de processus complets : démarrage JVM, préparation, IO et comptage inclus.", flush=True)
        print("Passages préalables : cache de fichiers sollicité ; chaque JVM redémarre.", flush=True)
        print("DFAM : minimisation de Hopcroft activée.", flush=True)

        # Validation hors chronométrage publié, puis vérification à chaque répétition.
        expected, _ = run_count(commands["grep"], environment, args.timeout, grep=True)
        actual, _ = run_count(commands["java"], environment, args.timeout)
        if actual != expected:
            raise ValueError(f"Comptages différents : Java={actual}, grep={expected}. Mesures abandonnées.")
        print(f"Comptage validé : {expected} lignes correspondantes.", flush=True)
        for phase, count in (("warmup", args.warmups), ("measure", args.runs)):
            for iteration in range(1, count + 1):
                order = list(commands)
                randomizer.shuffle(order)
                for position, engine in enumerate(order, 1):
                    found, duration = run_count(commands[engine], environment, args.timeout, grep=engine == "grep")
                    if found != expected:
                        raise ValueError(f"Comptage différent pendant {phase} : {engine}={found}, attendu={expected}.")
                    if phase == "measure":
                        rows.append({"iteration": iteration, "position": position, "engine": engine,
                                     "elapsed_ns": duration, "matching_lines": found})
                print(f"  {phase} : {iteration}/{count}", flush=True)
        metadata = {
            "source": str(source), "corpus": corpus_info, "regex": args.regex,
            "strategy": args.strategy, "runs": args.runs, "warmups": args.warmups,
            "seed": args.seed, "timeout_seconds": args.timeout, "locale": environment["LC_ALL"],
            "grep_version": grep_version, "java_version": java_version,
            "platform": sys.platform, "commands": commands, "matching_lines": expected,
            "scope": "process wall time including startup, preparation, IO and count output",
            "minimization_implemented": True,
            "temporary_corpus_deleted_after_run": True,
        }

    with (destination / "runs.csv").open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    summaries = []
    for engine in commands:
        values = [row["elapsed_ns"] / 1_000_000 for row in rows if row["engine"] == engine]
        summaries.append({"engine": engine, "runs": len(values), "mean_ms": statistics.mean(values),
                          "median_ms": statistics.median(values),
                          "sample_stdev_ms": statistics.stdev(values) if len(values) > 1 else "",
                          "min_ms": min(values), "max_ms": max(values)})
    with (destination / "summary.csv").open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=list(summaries[0]))
        writer.writeheader()
        writer.writerows(summaries)
    (destination / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("\nMoteur    Moyenne (ms)    Médiane (ms)    Écart type (ms)")
    for summary in summaries:
        deviation = summary["sample_stdev_ms"]
        label = f"{deviation:.3f}" if deviation != "" else "n/a"
        print(f"{summary['engine']:8} {summary['mean_ms']:13.3f} {summary['median_ms']:15.3f} {label:>18}")
    print(f"\nRésultats : {destination.resolve()}")
    print("Ces durées comparent les commandes complètes, pas les seuls algorithmes en mémoire.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(f"ERREUR : {error}", file=sys.stderr)
        sys.exit(1)
