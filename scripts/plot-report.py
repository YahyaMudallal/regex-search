#!/usr/bin/env python3
"""Trace les résultats d'une campagne réelle, sans relancer ni modifier les mesures.

Usage : python scripts/plot-report.py docs/results/2026-09-22 --output docs/assets
Dépendance optionnelle : scripts/requirements-report.txt.
"""

import argparse
import csv
import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

NAVY = "#233b63"
TEAL = "#137e72"
ORANGE = "#bc6b22"
INK = "#233249"


def main():
    """Lit les CSV publiés et produit trois figures SVG et PNG avec leurs unités."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("campaign", type=Path)
    parser.add_argument("--output", type=Path, default=Path("docs/assets"))
    args = parser.parse_args()
    manifest = json.loads((args.campaign / "campaign.json").read_text())
    experiments = manifest["experiments"]
    summaries, runs = {}, {}
    for case in experiments:
        location = args.campaign / case["id"]
        with (location / "summary.csv").open() as stream:
            summaries[case["id"]] = {row["engine"]: row for row in csv.DictReader(stream)}
        with (location / "runs.csv").open() as stream:
            runs[case["id"]] = list(csv.DictReader(stream))
    args.output.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 11,
                         "text.color": INK, "axes.labelcolor": INK,
                         "axes.spines.top": False, "axes.spines.right": False,
                         "axes.edgecolor": "#bdc7d2", "axes.titleweight": "bold",
                         "figure.facecolor": "#ffffff", "axes.facecolor": "#ffffff",
                         "svg.hashsalt": "regex-search-report-2026"})

    def save(figure, name, title):
        """Conserve une version vectorielle et une version raster pour inspection/export."""
        figure.savefig(args.output / f"{name}.svg", bbox_inches="tight",
                       metadata={"Title": title, "Date": manifest["started_utc"]})
        figure.savefig(args.output / f"{name}.png", bbox_inches="tight", dpi=170)
        plt.close(figure)

    # Une même échelle part de zéro pour les deux commandes ; les erreurs sont des écarts types.
    cases = experiments[:6]
    figure, axis = plt.subplots(figsize=(11.4, 5.8))
    positions = np.arange(len(cases))
    for engine, shift, color, label in [("java", -0.18, NAVY, "Java"), ("grep", 0.18, TEAL, "GNU grep -E")]:
        means = [float(summaries[case["id"]][engine]["mean_ms"]) for case in cases]
        deviations = [float(summaries[case["id"]][engine]["sample_stdev_ms"]) for case in cases]
        bars = axis.barh(positions + shift, means, height=0.31, xerr=deviations,
                         color=color, label=label, capsize=3, error_kw={"ecolor": INK, "elinewidth": 1})
        for bar, mean, deviation in zip(bars, means, deviations):
            axis.text(mean + deviation + 1.8, bar.get_y() + bar.get_height() / 2,
                      f"{mean:.1f}", va="center", fontsize=9)
    axis.set_yticks(positions, [case["label"] for case in cases])
    axis.invert_yaxis()
    axis.set_xlabel("Temps complet du processus (ms) · moyenne ± un écart type")
    axis.set_xlim(left=0, right=axis.get_xlim()[1] * 1.14)
    axis.grid(axis="x", alpha=0.18)
    axis.set_axisbelow(True)
    axis.legend(loc="lower right", frameon=False)
    figure.suptitle("Un même livre, plusieurs chemins de recherche", x=0.26, ha="left", fontsize=17, weight="bold")
    axis.set_title(f"Pride and Prejudice · {manifest['runs_per_engine']} mesures par moteur · JVM comprise · DFAM provisoire",
                   loc="left", fontsize=10, weight="normal", pad=15)
    figure.tight_layout()
    save(figure, "latency", "Temps complets des commandes selon le motif")

    # Les points ×1, ×8 et ×32 ne changent pas la distribution des lignes du livre.
    figure, axis = plt.subplots(figsize=(10.6, 5.8))
    series = [("KMP (AUTO)", NAVY, "java", ["literal-auto", "scale-auto-8", "scale-auto-32"]),
              ("Automate imposé", ORANGE, "java", ["literal-automaton", "scale-automaton-8", "scale-automaton-32"]),
              ("GNU grep (campagne KMP)", TEAL, "grep", ["literal-auto", "scale-auto-8", "scale-auto-32"])]
    for label, color, engine, identifiers in series:
        sizes = []
        for identifier in identifiers:
            meta = json.loads((args.campaign / identifier / "metadata.json").read_text())
            sizes.append(meta["corpus"]["bytes"] / (1024 * 1024))
        means = [float(summaries[identifier][engine]["mean_ms"]) for identifier in identifiers]
        deviations = [float(summaries[identifier][engine]["sample_stdev_ms"]) for identifier in identifiers]
        axis.errorbar(sizes, means, yerr=deviations, fmt="o-", color=color, label=label,
                      linewidth=2, capsize=4, markersize=6)
    axis.set_xlabel("Taille du corpus normalisé (Mio) · livre répété 1, 8 et 32 fois")
    axis.set_ylabel("Temps complet du processus (ms)")
    axis.set_ylim(bottom=0)
    axis.set_xlim(left=0)
    axis.grid(alpha=0.18)
    axis.legend(frameon=False)
    figure.suptitle("Quand le volume augmente", x=0.085, ha="left", fontsize=17, weight="bold")
    axis.set_title('Même motif : « Elizabeth » · moyenne ± un écart type · les segments relient seulement les observations',
                   loc="left", fontsize=10, weight="normal", pad=15)
    figure.tight_layout()
    save(figure, "scaling", "Temps selon le volume, à motif et corpus de base constants")

    # Histogrammes : mêmes nombres d'observations et mêmes axes d'effectifs ; abscisses propres à chaque moteur.
    figure, axes = plt.subplots(1, 3, figsize=(12.3, 4.6), sharey=True)
    for axis, (label, color, engine, identifier) in zip(axes, [
            ("Java · KMP", NAVY, "java", "literal-auto"),
            ("Java · automate", ORANGE, "java", "literal-automaton"),
            ("GNU grep", TEAL, "grep", "literal-auto")]):
        values = [int(row["elapsed_ns"]) / 1_000_000 for row in runs[identifier] if row["engine"] == engine]
        axis.hist(values, bins=6, color=color, alpha=0.9, edgecolor="white")
        median = float(summaries[identifier][engine]["median_ms"])
        axis.axvline(median, linestyle="--", color=INK, linewidth=1.5, label=f"Médiane : {median:.1f} ms")
        axis.set_title(label, fontsize=12)
        axis.set_xlabel("Durée (ms)")
        axis.legend(fontsize=9, frameon=False)
        axis.grid(axis="y", alpha=0.15)
        axis.set_axisbelow(True)
    axes[0].set_ylabel("Nombre de mesures")
    figure.suptitle('Dispersion sur « Elizabeth » · corpus ×1', x=0.055, ha="left", fontsize=17, weight="bold")
    figure.text(0.055, 0.89,
                f"{manifest['runs_per_engine']} observations par panneau · échelles horizontales différentes · aucun point retiré",
                fontsize=10)
    figure.tight_layout(rect=(0, 0, 1, 0.86))
    save(figure, "distribution", "Distribution des temps de processus sur un motif littéral")
    print(f"Figures créées dans {args.output}")


if __name__ == "__main__":
    main()
