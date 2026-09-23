#!/usr/bin/env bash
# Supprime les sorties locales d'expérimentation ; ne touche pas aux assets publiés.
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
check_python
exec python3 "$PROJECT_ROOT/scripts/lib/report_artifacts.py" clean "$@"
