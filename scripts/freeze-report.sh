#!/usr/bin/env bash
# Alias historique : republie manuellement la dernière campagne locale validée.
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
check_python
exec python3 "$PROJECT_ROOT/scripts/lib/report_artifacts.py" publish "$@"
