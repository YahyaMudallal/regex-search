#!/usr/bin/env bash
# Construit le JAR et une archive de rendu minimale à partir des seuls fichiers suivis par Git.
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

if [[ $# -eq 1 && ( "$1" == --help || "$1" == -h ) ]]; then
    cat <<'TXT'
Usage : ./scripts/package.sh

Préconditions : arbre Git propre, JDK 21+ et Maven.
Le script exécute les tests, construit le JAR puis crée dist/regex-search-submission.zip
à partir des seuls fichiers suivis par Git. .git, target, caches, venv et fichiers IDE
ne peuvent donc pas entrer dans l'archive. L'archive est refusée au-delà de 10 Mio.
TXT
    exit 0
fi
[[ $# -eq 0 ]] || fail "package.sh ne prend pas d'argument."

section "1/4 — Préflight reproductible"
check_requirements
check_python
command -v git >/dev/null 2>&1 || fail "Git est requis pour construire un rendu depuis les fichiers suivis."
[[ -z "$(git -C "$PROJECT_ROOT" status --porcelain)" ]] || \
    fail "Arbre Git non propre. Committer/stasher les changements avant de produire le rendu."

section "2/4 — Tests et construction du JAR"
run_maven -DskipTests=false -Dmaven.test.skip=false -Dmaven.test.failure.ignore=false clean package
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s "$PROJECT_ROOT/scripts/tests" -p 'test_*.py' -v
jar_path="$PROJECT_ROOT/target/regex-search-1.0-SNAPSHOT.jar"
[[ -f "$jar_path" ]] || fail "JAR final introuvable : $jar_path"

section "3/4 — Archive minimale"
python3 "$PROJECT_ROOT/scripts/lib/package_submission.py" "$jar_path"

section "4/4 — Résultat"
ls -lh "$PROJECT_ROOT/dist/regex-search-submission.zip" "$PROJECT_ROOT/dist/regex-search.jar"
