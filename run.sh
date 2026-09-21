#!/usr/bin/env bash
# Check prerequisites, rebuild the project, run tests, install the artifact locally,
# and launch the Main class declared in the JAR manifest.
# Usage: ./run.sh [arguments forwarded to Main]
# Requires a JDK compatible with pom.xml and Maven 3.6.3 or newer.
# Maven downloads missing project dependencies; Java and Maven must be installed first.

set -Eeuo pipefail

# Resolve paths relative to this script so it can be launched from any directory.
PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PHASE="initialization"

# Stop immediately on errors, including failed tests, and identify the failed phase.
trap 'printf "\nERROR: %s failed (line %s).\n" "$PHASE" "$LINENO" >&2' ERR

# Print a visible separator before each major step.
# $1: description of the phase about to run.
section() {
    PHASE="$1"
    printf '\n================================================================\n'
    printf '%s\n' "$PHASE"
    printf '================================================================\n'
}

# Report a prerequisite or artifact problem and stop before continuing.
# $1: explanation and, where possible, instructions to resolve the problem.
fail() {
    printf 'ERROR: %s\n' "$1" >&2
    exit 1
}

cd -- "$PROJECT_ROOT"

# ============================================================================
# 1. CHECK THE PROJECT AND REQUIRED TOOLS
# ============================================================================
section "1/3 — Checking project requirements"

[[ -f pom.xml ]] || fail "pom.xml was not found beside this script."
command -v sed >/dev/null 2>&1 || fail "sed is required to read the project configuration."
command -v mvn >/dev/null 2>&1 || fail "Install Apache Maven 3.6.3 or newer and add mvn to PATH."

# Read the Java release already configured in this project's POM.
required_java="$(sed -n 's/.*<maven.compiler.release>\([0-9][0-9]*\)<\/maven.compiler.release>.*/\1/p' pom.xml)"
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

printf 'Project: %s\n' "$PROJECT_ROOT"
printf 'Required Java release: %s\n' "$required_java"
printf 'JAVA_HOME: %s\n' "$JAVA_HOME"
printf 'Compiler: %s\n' "$javac_version"
printf '%s\n' "$maven_version"

# ============================================================================
# 2. CLEAN, RESOLVE DEPENDENCIES, COMPILE, TEST, PACKAGE AND INSTALL
# ============================================================================
section "2/3 — Cleaning, building, testing and installing locally"

printf 'Maven downloads missing dependencies and plugins as needed.\n'
printf 'The install lifecycle runs tests before packaging and local installation.\n'

# Run the lifecycle once. A failure prevents both local installation and Main.
# install writes the project artifact into Maven's local repository (usually ~/.m2).
mvn --batch-mode --no-transfer-progress -Dstyle.color=never \
    -DskipTests=false -Dmaven.test.skip=false -Dmaven.test.failure.ignore=false \
    clean install

# ============================================================================
# 3. RUN MAIN FROM THE GENERATED JAR
# ============================================================================
section "3/3 — Running com.sorbonne.Main"

# clean removes stale versions. This single-module project produces one JAR.
# Discover its name so changing the project version does not require a script edit.
shopt -s nullglob
artifacts=("$PROJECT_ROOT"/target/*.jar)
[[ ${#artifacts[@]} -eq 1 ]] ||
    fail "Expected one application JAR in target/ after the build; found ${#artifacts[@]}."

printf 'Application: %s\n\n' "${artifacts[0]}"
"$JAVA_HOME/bin/java" -jar "${artifacts[0]}" "$@"
