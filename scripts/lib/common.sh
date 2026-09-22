#!/usr/bin/env bash
# Fonctions partagées ; ce fichier doit être sourcé par les scripts publics.
# Aucun changement du répertoire courant : les chemins fournis restent ceux de l'appelant.
PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
    printf 'ERREUR : %s\n' "$1" >&2
    exit 1
}

section() {
    printf '\n================================================================\n%s\n' "$1"
    printf '================================================================\n'
}

# Vérifie le JDK et Maven sans rien installer ; Maven résoudra les dépendances du projet.
check_requirements() {
    [[ -f "$PROJECT_ROOT/pom.xml" ]] || fail "pom.xml introuvable."
    command -v sed >/dev/null 2>&1 || fail "sed est requis."
    command -v mvn >/dev/null 2>&1 || fail "Maven 3.6.3 ou supérieur est requis."
    # Read the Java release already configured in this project's POM.
    required_java="$(sed -n 's/.*<maven.compiler.release>\([0-9][0-9]*\)<\/maven.compiler.release>.*/\1/p' "$PROJECT_ROOT/pom.xml")"
    [[ "$required_java" =~ ^[0-9]+$ ]] || fail "Set a numeric maven.compiler.release in pom.xml."

    # Respect an explicit JAVA_HOME. Otherwise, discover the JDK used by java on PATH.
    if [[ -z "${JAVA_HOME:-}" ]]; then
        command -v java >/dev/null 2>&1 || fail "Install JDK $required_java or newer and set JAVA_HOME."
        java_settings="$(java -XshowSettings:properties -version 2>&1)"
        JAVA_HOME="$(printf '%s\n' "$java_settings" | sed -n 's/^[[:space:]]*java.home = //p')"
    fi

    [[ -n "$JAVA_HOME" && -x "$JAVA_HOME/bin/java" && -x "$JAVA_HOME/bin/javac" ]] ||
        fail "JAVA_HOME must point to a full JDK with java and javac (Java $required_java or newer)."

    # Use the same JDK for Maven, compilation and the final application launch.
    export JAVA_HOME
    export PATH="$JAVA_HOME/bin:$PATH"

    javac_version="$("$JAVA_HOME/bin/javac" -version 2>&1)"
    [[ "$javac_version" =~ javac[[:space:]]+([0-9]+) ]] ||
        fail "Could not read the compiler version: $javac_version"
    java_major="${BASH_REMATCH[1]}"
    (( java_major >= required_java )) ||
        fail "JDK $required_java or newer is required; detected $javac_version. Update JAVA_HOME."

    # The compiler and JAR plugins used by this project require at least Maven 3.6.3.
    maven_version="$(mvn -Dstyle.color=never --version)"
    [[ "$maven_version" =~ Apache[[:space:]]Maven[[:space:]]([0-9]+)\.([0-9]+)\.([0-9]+) ]] ||
        fail "Could not determine the Maven version. Run mvn --version to check your installation."
    maven_major="${BASH_REMATCH[1]}"
    maven_minor="${BASH_REMATCH[2]}"
    maven_patch="${BASH_REMATCH[3]}"
    (( maven_major > 3 || (maven_major == 3 && maven_minor > 6) ||
        (maven_major == 3 && maven_minor == 6 && maven_patch >= 3) )) ||
        fail "Maven 3.6.3 or newer is required; detected $maven_major.$maven_minor.$maven_patch."


    printf 'Projet : %s\nJDK : %s\nMaven : %s.%s.%s\n' \
        "$PROJECT_ROOT" "$javac_version" "$maven_major" "$maven_minor" "$maven_patch"
}

# Exécute Maven à la racine dans un sous-shell sans déplacer l'appelant.
# MAVEN_OFFLINE=1 permet de travailler uniquement avec les dépendances déjà présentes.
run_maven() (
    cd -- "$PROJECT_ROOT" || exit
    local_flags=(--batch-mode --no-transfer-progress -Dstyle.color=never)
    if [[ "${MAVEN_OFFLINE:-0}" == 1 ]]; then
        local_flags+=(--offline)
    fi
    mvn "${local_flags[@]}" "$@"
)

# Compilation hors chronométrage ; les tests sont lancés séparément par run.sh.
build_main() {
    section "Compilation du projet (hors mesure)"
    run_maven -DskipTests compile
}

# Python sert aux statistiques et aux tests du protocole, sans paquet à installer.
check_python() {
    command -v python3 >/dev/null 2>&1 || fail "Python 3.9 ou supérieur est requis."
    python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)' || \
        fail "Python 3.9 ou supérieur est requis."
}
