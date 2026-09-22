#!/usr/bin/env bash
# Reproduit les dix expériences du rapport sans lancer plusieurs benchmarks en parallèle.
set -Eeuo pipefail
# shellcheck source=scripts/lib/common.sh
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
check_python
if [[ $# -eq 0 || "${1:-}" == --help || "${1:-}" == -h ]]; then
    exec python3 "$PROJECT_ROOT/scripts/lib/report_campaign.py" --help
fi
check_requirements
build_main
exec python3 "$PROJECT_ROOT/scripts/lib/report_campaign.py" "$@"
