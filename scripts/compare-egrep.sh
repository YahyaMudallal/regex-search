#!/usr/bin/env bash
# Compare des processus complets : Java contre GNU grep -E, avec sortie limitée au comptage.
# Python (bibliothèque standard uniquement) gère l'horloge monotone, les statistiques et le CSV.
set -Eeuo pipefail
# shellcheck source=scripts/lib/common.sh
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
check_python
if [[ $# -eq 0 || "${1:-}" == --help || "${1:-}" == -h ]]; then
    exec python3 "$PROJECT_ROOT/scripts/lib/compare_egrep.py" --help
fi
check_requirements
build_main
exec python3 "$PROJECT_ROOT/scripts/lib/compare_egrep.py" "$@"
