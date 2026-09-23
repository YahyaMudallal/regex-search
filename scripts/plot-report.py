#!/usr/bin/env python3
"""Trace une campagne validée dans target/report/figures avant publication atomique."""

import argparse
import json
import os
from pathlib import Path
import statistics
import sys
import textwrap

ROOT = Path(__file__).resolve().parents[1]
os.environ.setdefault("MPLCONFIGDIR", str(ROOT / ".cache/matplotlib"))
os.environ.setdefault("XDG_CACHE_HOME", str(ROOT / ".cache"))
sys.path.insert(0, str(ROOT / "scripts/lib"))
from fixed_report import load_validated_results

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

NAVY, TEAL, ORANGE, INK = "#233b63", "#137e72", "#bc6b22", "#233249"


def case_label(case):
    return case["label"] + "\nregex : " + "\n".join(textwrap.wrap(
        case["regex"], width=60, break_long_words=True, break_on_hyphens=False))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("campaign", type=Path, nargs="?", default=ROOT / "target/report/results")
    parser.add_argument("--output", type=Path, default=ROOT / "target/report/figures")
    args = parser.parse_args()
    if args.output.resolve().is_relative_to((ROOT / "docs/assets").resolve()):
        parser.error("Tracer d’abord dans target/report/figures ; report-campaign.sh publie ensuite docs/assets atomiquement.")
    manifest, cli, jvm, cli_summaries, jvm_summaries = load_validated_results(args.campaign)
    profile = manifest["profile"]
    cases = profile["experiments"]
    by_id = {case["id"]: case for case in cases}
    cli_stats = {(row["case_id"], row["engine"]): row for row in cli_summaries}
    jvm_stats = {(row["case_id"], row["metric"]): row for row in jvm_summaries}
    automata = manifest["automata"]
    args.output.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 10, "text.color": INK,
                         "axes.labelcolor": INK, "axes.spines.top": False, "axes.spines.right": False,
                         "axes.edgecolor": "#bdc7d2", "figure.facecolor": "white", "axes.facecolor": "white",
                         "svg.hashsalt": profile["protocol"], "svg.fonttype": "none", "text.parse_math": False})
    provenance = f"{profile['protocol']} · {manifest['finished_utc'][:10]} · profil {manifest['profile_sha256'][:10]} · DFAM non implémenté"

    def save(figure, name, title, selected):
        figure.text(0.01, 0.006, provenance, fontsize=8, color="#526477")
        metadata = {"Title": title, "Description": json.dumps(
            {case["id"]: case["regex"] for case in selected}, ensure_ascii=False)}
        figure.savefig(args.output / f"{name}.svg", bbox_inches="tight",
                       metadata={**metadata, "Date": manifest["finished_utc"]})
        figure.savefig(args.output / f"{name}.png", bbox_inches="tight", dpi=145, metadata=metadata)
        plt.close(figure)

    def latency(selected, name, title, corpus):
        figure, axis = plt.subplots(figsize=(14.8, 2.4 + len(selected) * 0.92))
        positions = np.arange(len(selected))
        for engine, shift, color, label in (("java", -0.18, NAVY, "Java"), ("grep", 0.18, TEAL, "GNU grep -E")):
            rows = [cli_stats[case["id"], engine] for case in selected]
            medians = np.array([row["median_ms"] for row in rows])
            errors = np.array([[row["median_ms"] - row["q25_ms"] for row in rows],
                               [row["q75_ms"] - row["median_ms"] for row in rows]])
            bars = axis.barh(positions + shift, medians, height=0.30, xerr=errors,
                             color=color, label=label, capsize=3)
            for bar, row in zip(bars, rows):
                axis.annotate(f"{row['median_ms']:.1f}" + (" *" if row["noisy"] else ""),
                              (row["q75_ms"], bar.get_y() + bar.get_height() / 2),
                              xytext=(6, 0), textcoords="offset points", va="center", fontsize=9)
        axis.set_yticks(positions, [case_label(case) for case in selected], fontsize=9)
        axis.invert_yaxis()
        axis.set_xlim(0, axis.get_xlim()[1] * 1.18)
        axis.set_xlabel("Commande complète (ms) · médiane et intervalle interquartile [Q1 ; Q3]")
        axis.grid(axis="x", alpha=0.18)
        axis.set_axisbelow(True)
        axis.legend(loc="lower right", frameon=False)
        axis.set_title(f"{corpus} · 30 processus par moteur · démarrage JVM inclus\n* IQR/médiane > 15 % ; aucun point retiré ; dispersion ≠ intervalle de confiance",
                       fontsize=10, loc="left", pad=12)
        figure.suptitle(title, fontsize=17, weight="bold", x=0.02, ha="left")
        figure.tight_layout(rect=(0, 0.04, 1, 0.95))
        save(figure, name, title, selected)

    book = [case for case in cases if case["group"] == "book"]
    latency(book, "latency", "Motifs simples, intermédiaires et complexes", "Pride and Prejudice · corpus ×1")
    stress = [case for case in cases if case["group"] in {"stress", "growth"} and case.get("cli", True)]
    latency(stress, "stress", "Préfixes répétés et croissance du DFA", "Corpus synthétique fixe · 10 240 lignes")

    figure, axis = plt.subplots(figsize=(11.5, 6))
    identifiers = ["complex", "scale-complex-8", "scale-complex-32"]
    for label, color, engine in (("Java · automate", ORANGE, "java"), ("GNU grep -E", TEAL, "grep")):
        sizes = [manifest["corpora"][by_id[key]["corpus"]]["bytes"] / 1024**2 for key in identifiers]
        rows = [cli_stats[key, engine] for key in identifiers]
        axis.errorbar(sizes, [row["median_ms"] for row in rows],
                      yerr=[[row["median_ms"] - row["q25_ms"] for row in rows],
                            [row["q75_ms"] - row["median_ms"] for row in rows]],
                      fmt="o-", color=color, capsize=4, linewidth=2, label=label)
    axis.set(xlabel="Taille UTF-8 normalisée (Mio) · même livre répété 1, 8 et 32 fois",
             ylabel="Commande complète (ms)", xlim=(0, None), ylim=(0, None))
    axis.grid(alpha=0.18)
    axis.legend(frameon=False)
    axis.set_title("Même regex complexe AUTOMATON aux trois volumes\nMédiane et IQR des processus ; préparation incluse", loc="left", pad=14)
    figure.suptitle("Effet du volume à automate constant", fontsize=17, weight="bold", x=0.08, ha="left")
    figure.tight_layout(rect=(0, 0.04, 1, 0.94))
    save(figure, "scaling", "Effet du volume — regex complexe constante", [by_id[key] for key in identifiers])

    figure, axes = plt.subplots(1, 3, figsize=(13.8, 5.2))
    for axis, (label, color, engine, identifier) in zip(axes, [
            ("Java · KMP", NAVY, "java", "literal-auto"),
            ("Java · automate", ORANGE, "java", "literal-automaton"),
            ("GNU grep", TEAL, "grep", "literal-auto")]):
        values = [row["elapsed_ns"] / 1e6 for row in cli if row["case_id"] == identifier
                  and row["engine"] == engine and row["phase"] == "measure"]
        axis.plot(range(1, len(values) + 1), values, "o-", markersize=4, linewidth=0.8, color=color)
        row = cli_stats[identifier, engine]
        axis.axhspan(row["q25_ms"], row["q75_ms"], color=color, alpha=0.1, label="IQR")
        axis.axhline(row["median_ms"], color=INK, linestyle="--", label="Médiane")
        axis.set(title=label, xlabel="Ordre relatif d'exécution", ylabel="Processus complet (ms)", ylim=(0, None))
        axis.grid(alpha=0.15)
        axis.legend(fontsize=8, frameon=False)
    figure.suptitle("Toutes les observations · regex exacte : Elizabeth", fontsize=16, weight="bold", x=0.05, ha="left")
    figure.text(0.05, 0.89, "Corpus ×1 · 5 blocs de 6 paires · axes verticaux propres à chaque moteur · aucun point retiré", fontsize=10)
    figure.tight_layout(rect=(0, 0.04, 1, 0.86))
    save(figure, "distribution", "Observations dans leur ordre d'exécution — regex Elizabeth", [by_id["literal-auto"], by_id["literal-automaton"]])

    figure, axes = plt.subplots(1, 3, figsize=(17.5, 9.3), sharey=True)
    for axis, metric, title, color in zip(axes, ("preparation_ns", "scan_ns", "total_ns"),
                                         ("Préparation", "Lecture + recherche", "Pipeline complet"),
                                         (ORANGE, TEAL, NAVY)):
        for i, case in enumerate(book):
            row = jvm_stats[case["id"], metric]
            means = [statistics.mean(raw[metric] / 1e6 for raw in jvm if raw["case_id"] == case["id"]
                                     and raw["fork"] == fork and raw["phase"] == "measure")
                     for fork in range(1, profile["jvm"]["forks"] + 1)]
            axis.plot([row["min_ms"], row["max_ms"]], [i, i], color=color, linewidth=2, alpha=0.5)
            axis.scatter(means, [i] * len(means), color=color, s=20, alpha=0.65)
            axis.scatter([row["median_ms"]], [i], color=INK, marker="|", s=130)
        axis.set(title=title, xlabel="ms · moyennes de chaque JVM", xlim=(0, None))
        axis.locator_params(axis="x", nbins=5)
        axis.grid(axis="x", alpha=0.15)
    axes[0].set_yticks(range(len(book)), [case_label(case) for case in book], fontsize=9)
    axes[0].invert_yaxis()
    figure.suptitle("Étapes du pipeline dans des JVM échauffées", fontsize=17, weight="bold", x=0.02, ha="left")
    figure.text(0.02, 0.925, "5 JVM par cas · 10 prépassages + 10 mesures/JVM · chaque point = moyenne d'une JVM\nTrait noir = médiane des 5 moyennes ; segment = min–max · IO incluses dans le parcours", fontsize=10)
    figure.tight_layout(rect=(0, 0.04, 1, 0.89))
    save(figure, "phases", "Préparation et parcours par regex — 5 JVM indépendantes", book)

    growth = [case for case in cases if case["group"] == "growth"]
    figure, axes = plt.subplots(1, 2, figsize=(13.8, 6.2))
    depths = [case["branch_depth"] for case in growth]
    nfa_states = [automata[case["id"]]["nfa_states"] for case in growth]
    dfa_states = [automata[case["id"]]["search_dfa_states"] for case in growth]
    axes[0].plot(depths, nfa_states, "o-", label="NFA de Thompson")
    axes[0].plot(depths, dfa_states, "o-", label="DFA de recherche")
    axes[0].set(xlabel="Profondeur d de la famille (a|b)*a(a|b)^d b", ylabel="Nombre d’états")
    axes[0].set_xticks(depths)
    axes[0].grid(alpha=0.18)
    axes[0].legend(frameon=False)
    for x, y in zip(depths, dfa_states):
        axes[0].annotate(str(y), (x, y), xytext=(4, 4), textcoords="offset points")

    for metric, label in (("dfa_ns", "Déterminisation"), ("preparation_ns", "Préparation totale")):
        rows = [jvm_stats[case["id"], metric] for case in growth]
        axes[1].errorbar(dfa_states, [row["median_ms"] for row in rows],
                         yerr=[[row["median_ms"] - row["min_ms"] for row in rows],
                               [row["max_ms"] - row["median_ms"] for row in rows]],
                         fmt="o-", capsize=4, label=label)
    axes[1].set(xlabel="États du DFA de recherche (profil structurel hors chronométrage)", ylabel="Temps (ms)", ylim=(0, None))
    axes[1].grid(alpha=0.18)
    axes[1].legend(frameon=False)
    figure.suptitle("Croissance contrôlée de la complexité de l’automate", fontsize=16, weight="bold", x=0.06, ha="left")
    figure.text(0.06, 0.90, "Famille : (a|b)*a suivie de d blocs (a|b), puis b ; d = 5, 7, 9.\nLe profil structurel est séparé du chronométrage ; aucune minimisation n’est attribuée à DFAM.", fontsize=10)
    figure.tight_layout(rect=(0, 0.04, 1, 0.85))
    save(figure, "compilation", "Croissance du DFA de recherche selon une famille exponentielle", growth)
    print(f"Six figures SVG et PNG créées dans {args.output}")


if __name__ == "__main__":
    main()
