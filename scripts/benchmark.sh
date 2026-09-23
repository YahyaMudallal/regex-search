#!/usr/bin/env bash
# Lance une mesure détaillée dans Main ; les arguments sont transmis sans interprétation shell.
set -Eeuo pipefail
# shellcheck source=scripts/lib/common.sh
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
if [[ $# -eq 1 && ( "$1" == --help || "$1" == -h ) ]]; then
    printf "Usage : %s <fichier> <regex> [AUTO|KMP|DFA|DFAM|AUTOMATON]\n" "$0"
    printf "Exemple : %s Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy' AUTO\n" "$0"
    exit 0
fi
[[ $# -ge 2 && $# -le 3 ]] || fail "Usage : $0 <fichier> <regex> [AUTO|KMP|DFA|DFAM|AUTOMATON]"
[[ -f "$1" ]] || fail "Fichier introuvable ou non ordinaire : $1"
# Un chemin absolu évite qu'un nom de fichier tel que --count soit pris pour une option.
input_file="$(cd -- "$(dirname -- "$1")" && pwd)/$(basename -- "$1")"
shift
check_requirements
build_main
section "Benchmark Java — préparation puis lecture et recherche"
exec "$JAVA_HOME/bin/java" -cp "$PROJECT_ROOT/target/classes" com.sorbonne.Main "$input_file" "$@"
