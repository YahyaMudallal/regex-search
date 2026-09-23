#!/usr/bin/env bash
# Pipeline expérimental complet : validation, mesures, assets Markdown, nettoyage.
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

usage() {
    cat <<'TXT'
Usage : ./scripts/report-campaign.sh [--purge] [--allow-dirty]

Pipeline fixe décrit par scripts/report-profile.json :
  1. vérifie Git, Java/Maven/Python/GNU grep ;
  2. nettoie les sorties locales précédentes ;
  3. compile et exécute les tests ;
  4. matérialise les corpus dérivés ;
  5. valide chaque regex octet par octet contre GNU grep -E -n ;
  6. profile la taille NFA/DFA, mesure CLI et JVM, agrège ;
  7. régénère atomiquement les assets utilisés par README/docs puis nettoie le temporaire.

Options :
  --purge        supprime après succès les CSV/TXT générés dans target/report/results ;
                 benchmark.md/json et les SVG publiés dans docs/assets restent disponibles.
  --allow-dirty  essai de développement sur un arbre Git non propre ; les résultats restent
                 locaux et docs/assets n'est PAS remplacé.
  -h, --help     affiche cette aide.
TXT
}

args=()
for arg in "$@"; do
    case "$arg" in
        --purge|--allow-dirty) args+=("$arg") ;;
        -h|--help) usage; exit 0 ;;
        *) fail "Option inconnue : $arg" ;;
    esac
done

unset JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS JAVA_OPTIONS CLASSPATH
check_requirements
check_python
exec python3 "$PROJECT_ROOT/scripts/lib/fixed_report.py" "${args[@]}"
