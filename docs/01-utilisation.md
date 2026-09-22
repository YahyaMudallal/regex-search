[← Accueil](../README.md) · [Chapitre suivant : conception →](02-conception.md)

# 01 — Installer, lancer et reproduire

Le chemin le plus court consiste à vérifier les outils, lancer les tests, puis essayer une recherche sur le fichier fourni. Le script de comparaison vient ensuite : il mesure des processus complets et demande quelques précautions supplémentaires sur le texte et le motif.

## 1. Environnement de travail

Le projet est compilé pour Java 25. Il faut un **JDK complet** : une installation qui fournit seulement l’exécution de Java ne suffit pas, puisque Maven appelle aussi le compilateur. Les scripts utilisent le JDK désigné par `JAVA_HOME`. Si cette variable n’est pas définie, ils récupèrent le répertoire du `java` présent dans le `PATH`.

```bash
java -version
javac -version
mvn --version
python3 --version
```

Les versions minimales attendues sont Java 25, Maven 3.6.3 et Python 3.9. Le POM déclare JUnit 6.1.3, Maven Compiler Plugin 3.14.0, Surefire 3.5.3 et Maven JAR Plugin 3.4.2. Ces versions sont celles du dépôt ; elles ne constituent pas une recommandation générale d’utiliser la version la plus récente de chaque outil.

Bash orchestre les commandes. Python ne participe pas à la recherche du motif : sa bibliothèque standard sert à chronométrer les processus, vérifier leurs sorties et calculer les statistiques. Sous Windows, un environnement Linux tel que WSL permet d’utiliser les scripts Bash ; la campagne publiée a été exécutée sous macOS arm64.

Pour la comparaison, vérifier **GNU grep**, pas seulement la présence d’une commande nommée `grep` :

```bash
grep --version
# Sur un système où GNU grep est installé sous ce nom :
ggrep --version
locale -a
```

Le script essaie `ggrep`, puis `grep`, et vérifie sa version. `--grep /chemin/vers/grep` permet de sélectionner un autre exécutable. Une locale UTF-8 doit être installée ; le script cherche notamment `C.UTF-8` et `en_US.UTF-8`.

## 2. Nettoyer et tester

```bash
./run.sh
```

Cette commande vérifie les prérequis, lance `mvn clean test`, puis les tests Python du protocole. Un échec arrête le script. Elle ne lance ni démonstration ni benchmark et n’installe pas le JAR dans le dépôt Maven local.

Au premier lancement, Maven peut télécharger les dépendances et plugins. Quand ils sont déjà disponibles :

```bash
MAVEN_OFFLINE=1 ./run.sh
```

Le mode hors ligne ne remplace pas une installation initiale : si un artefact manque dans le cache, Maven le signale.

Les rapports Java sont dans `target/surefire-reports/`. Pour exécuter une suite précise sans nettoyer tout le projet :

```bash
mvn -Dtest=DFATest,DFAPropertyTest test
mvn -Dtest=KMPSearchTest,KMPSearchPropertyTest test
mvn -Dtest=BenchmarkTest,BenchmarkPropertyTest test
```

## 3. Rechercher dans un fichier

```bash
./scripts/benchmark.sh Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy' AUTO
```

Les trois arguments sont le chemin du fichier, l’expression régulière et une stratégie optionnelle. Les guillemets simples empêchent le shell d’interpréter `|`, `*`, les parenthèses ou la barre oblique inverse.

| Stratégie | Comportement |
| :--- | :--- |
| `AUTO` | Analyse l’arbre et choisit KMP si le motif est une concaténation de lettres |
| `KMP` | Impose KMP ; une expression non littérale est refusée |
| `AUTOMATON` | Impose NFA → DFA → DFAM → préparation de la recherche, même pour un mot simple |

```bash
./scripts/benchmark.sh Samples/PrideAndPrejudice.txt 'Elizabeth' KMP
./scripts/benchmark.sh Samples/PrideAndPrejudice.txt 'Elizabeth' AUTOMATON
./scripts/benchmark.sh Samples/PrideAndPrejudice.txt 'Eli.*beth'
```

Le script compile avant le lancement. Cette compilation n’entre pas dans les durées affichées. La mesure Java comprend l’analyse du motif, les étapes de préparation exécutées et le parcours du fichier. Le démarrage de la JVM n’appartient pas au chronomètre interne de `Benchmark`.

La sortie indique le moteur choisi, le nombre total de lignes et le nombre de lignes correspondantes. Elle décompose aussi la préparation. La durée de DFAM ne représente, pour le moment, qu’un appel retournant la même référence d’automate.

### Appeler directement Main

Après compilation :

```bash
# Démonstrations progressives, dont les exemples d’automates.
java -cp target/classes com.sorbonne.Main

# Mesure détaillée d’un fichier.
java -cp target/classes com.sorbonne.Main Samples/PrideAndPrejudice.txt 'Elizabeth'

# Nombre seul, utilisé par le comparateur.
java -cp target/classes com.sorbonne.Main --count \
  Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy' AUTO
```

Le dernier appel affiche `1050` sur le corpus du dépôt. Un résultat nul affiche `0` et reste une exécution Java réussie. C’est différent du statut de sortie de grep, qui vaut 1 en l’absence de correspondance ; le comparateur prend cette différence en compte.

### Construire le binaire

```bash
mvn package
java -jar target/regex-search-1.0-SNAPSHOT.jar \
  Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy' AUTO
```

Le manifeste du JAR désigne `com.sorbonne.Main`. Le nom de l’archive dépend de la version déclarée dans `pom.xml`. Le JAR contient le moteur, mais pas les livres : le chemin du fichier à parcourir reste un argument.

## 4. Comparer à GNU grep

```bash
./scripts/compare-egrep.sh Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy' \
  --runs 20 --warmups 3 --seed 42 --timeout 60 \
  --output-dir target/benchmarks/essai-01
```

Le dossier de sortie doit être nouveau. La commande crée une copie temporaire normalisée du fichier, vérifie que Java et grep annoncent le même nombre de lignes, puis alterne les exécutions dans un ordre pseudo-aléatoire reproductible.

| Fichier de résultat | Contenu |
| :--- | :--- |
| `runs.csv` | Chaque mesure, sa position dans la paire, le moteur et le nombre de lignes |
| `summary.csv` | Moyenne, médiane, écart type d’échantillon, minimum et maximum |
| `metadata.json` | Motif, stratégie, versions, locale et empreinte du corpus normalisé |

Les mesures brutes sont en nanosecondes ; les résumés sont en millisecondes. L’écart type décrit la dispersion des observations, pas un intervalle de confiance sur la moyenne.

Les expressions utilisent le sous-ensemble commun décrit au [chapitre 2](02-conception.md). Le comparateur refuse notamment `+`, les classes de caractères et les ancres : ces opérateurs ne sont pas implémentés dans le moteur du projet. Le fichier doit être du texte UTF-8 valide, sans NUL ni caractères hors BMP. Les raisons de ces restrictions sont détaillées dans le [protocole expérimental](05-experiences.md).

## 5. Reproduire toute la campagne du rapport

```bash
./run.sh
./scripts/report-campaign.sh target/rapport-reproduction --runs 20
```

La campagne exécute dix expériences en série. Elle recrée deux corpus dérivés, en répétant le livre huit et trente-deux fois, dans `target/report-corpora/`. Les empreintes des sources et la configuration sont conservées dans `campaign.json`. Les répétitions modifient le volume, pas la diversité linguistique du texte.

Pour régénérer les figures, utiliser **Python 3.11 ou supérieur** avec Matplotlib 3.11.2, une dépendance de visualisation distincte :

```bash
python3 -m venv /tmp/regex-report-venv
/tmp/regex-report-venv/bin/python -m pip install -r scripts/requirements-report.txt
MPLCONFIGDIR=/tmp/regex-report-mpl /tmp/regex-report-venv/bin/python \
  scripts/plot-report.py target/rapport-reproduction --output target/rapport-figures
```

Cette installation est optionnelle. Elle ne modifie ni le POM ni les dépendances du moteur. Les SVG publiés dans `docs/assets/` se lisent directement dans le dépôt.

## 6. Diagnostic rapide

| Symptôme | Vérification utile |
| :--- | :--- |
| `JAVA_HOME must point to a full JDK` | Vérifier `JAVA_HOME/bin/java` et `JAVA_HOME/bin/javac` |
| Maven hors ligne ne trouve pas un plugin | Relancer sans `MAVEN_OFFLINE=1` avec accès réseau |
| Le script refuse le grep du système | Utiliser GNU grep, éventuellement avec `--grep` |
| Le motif semble interprété par le terminal | Entourer la regex de guillemets simples |
| KMP refuse `a.b` | Le point est un opérateur ; utiliser `AUTO`, ou `a\.b` pour un point littéral |
| Le dossier de résultats existe déjà | Choisir un autre `--output-dir` pour préserver l’expérience précédente |
| Le fichier UTF-8 est rejeté | Vérifier son encodage ; le lecteur ne remplace pas silencieusement les octets invalides |
| Les temps varient entre deux appels | Consulter toutes les répétitions ; ne pas comparer une seule observation |

[← Accueil](../README.md) · [Chapitre suivant : conception →](02-conception.md)
