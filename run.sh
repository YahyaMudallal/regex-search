#!/usr/bin/env bash
# Nettoie, compile et exécute les tests. Aucun benchmark ni lancement de Main ici.
# Usage : ./run.sh ; MAVEN_OFFLINE=1 ./run.sh pour utiliser le cache Maven.
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/scripts/lib/common.sh"
if [[ $# -gt 0 ]]; then
    if [[ $# -eq 1 && ( "$1" == --help || "$1" == -h ) ]]; then
        printf 'Usage : ./run.sh\nTests uniquement. Benchmark : ./scripts/benchmark.sh --help\n'
        exit 0
    fi
    fail "run.sh ne prend pas d'arguments. Utiliser scripts/benchmark.sh pour lancer une recherche."
fi
section "1/3 — Vérification des prérequis"
check_requirements
check_python
section "2/3 — Nettoyage, compilation et tests"
run_maven -DskipTests=false -Dmaven.test.skip=false -Dmaven.test.failure.ignore=false clean test
section "3/3 — Tests du protocole de benchmark"
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s "$PROJECT_ROOT/scripts/tests" -p 'test_*.py' -v
