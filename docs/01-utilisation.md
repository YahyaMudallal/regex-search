[← Accueil](../README.md) · [Chapitre suivant : conception →](02-conception.md)

# 01 — Installer, lancer et reproduire

Le chemin le plus court consiste à vérifier les outils, lancer les tests, puis essayer une recherche sur le fichier fourni. Le script de comparaison vient ensuite : il mesure des processus complets et demande quelques précautions supplémentaires sur le texte et le motif.

## 1. Environnement de travail

Le projet est compilé pour Java 21. Il faut un **JDK complet** : une installation qui fournit seulement l’exécution de Java ne suffit pas, puisque Maven appelle aussi le compilateur. Les scripts utilisent le JDK désigné par `JAVA_HOME`. Si cette variable n’est pas définie, ils récupèrent le répertoire du `java` présent dans le `PATH`.

```bash
java -version
javac -version
mvn --version
python3 --version
```

Les versions minimales attendues sont Java 21, Maven 3.6.3 et Python 3.9. Le POM déclare JUnit 6.1.3, Maven Compiler Plugin 3.14.0, Surefire 3.5.3 et Maven JAR Plugin 3.4.2. Ces versions sont celles du dépôt ; elles ne constituent pas une recommandation générale d’utiliser la version la plus récente de chaque outil.

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

### Afficher les lignes correspondantes

Pour utiliser le moteur comme une recherche interactive de type `egrep -n`, afficher chaque ligne correspondante avec son numéro :

```bash
./scripts/search.sh Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy'
```

La sortie est écrite au format `numéro:contenu`. Le motif est préparé une seule fois et les lignes sont transmises au fur et à mesure, sans conserver tout le fichier en mémoire. Une ligne contenant plusieurs occurrences n’est affichée qu’une fois.

La stratégie peut être imposée en dernier argument :

```bash
./scripts/search.sh Samples/PrideAndPrejudice.txt 'Elizabeth' KMP
./scripts/search.sh Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy' AUTOMATON
```

Ce mode n’affiche ni timings ni compteurs supplémentaires. Pour obtenir uniquement le nombre de lignes, utiliser `--count` ou `scripts/compare-egrep.sh`.

```bash
./scripts/benchmark.sh Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy' AUTO
```

Les trois arguments sont le chemin du fichier, l’expression régulière et une stratégie optionnelle. Les guillemets simples empêchent le shell d’interpréter `|`, `*`, les parenthèses ou la barre oblique inverse.

| Stratégie   | Comportement                                                                   |
| :---------- | :----------------------------------------------------------------------------- |
| `AUTO`      | Analyse l’arbre et choisit KMP si le motif est une concaténation de lettres    |
| `KMP`       | Impose KMP ; une expression non littérale est refusée                          |
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

| Fichier de résultat | Contenu                                                                    |
| :------------------ | :------------------------------------------------------------------------- |
| `runs.csv`          | Chaque mesure, sa position dans la paire, le moteur et le nombre de lignes |
| `summary.csv`       | Moyenne, médiane, écart type d’échantillon, minimum et maximum             |
| `metadata.json`     | Motif, stratégie, versions, locale et empreinte du corpus normalisé        |

Les mesures brutes sont en nanosecondes ; les résumés sont en millisecondes. L’écart type décrit la dispersion des observations, pas un intervalle de confiance sur la moyenne.

Les expressions utilisent le sous-ensemble commun décrit au [chapitre 2](02-conception.md). Le comparateur refuse notamment `+`, les classes de caractères et les ancres : ces opérateurs ne sont pas implémentés dans le moteur du projet. Le fichier doit être du texte UTF-8 valide, sans NUL ni caractères hors BMP. Les raisons de ces restrictions sont détaillées dans le [protocole expérimental](05-experiences.md).

## 5. Reproduire toute la campagne du rapport

```bash
# Campagne de référence : l'arbre Git doit être propre.
./scripts/report-campaign.sh

# Même campagne puis suppression des CSV/TXT générés localement.
./scripts/report-campaign.sh --purge

# Essai de développement : résultats locaux uniquement, aucun remplacement de docs/assets/.
./scripts/report-campaign.sh --allow-dirty

# Avec le cache Maven déjà rempli :
MAVEN_OFFLINE=1 ./scripts/report-campaign.sh
```

Les paramètres de mesure sont figés dans [`scripts/report-profile.json`](../scripts/report-profile.json). La campagne de référence exige **Java 21**, **Python 3.12 ou supérieur**, GNU grep et une locale UTF-8. L'environnement de tracé est mis en cache dans `.cache/report-venv/`, donc il ne pollue ni l'archive de rendu ni les fichiers suivis par Git.

Le protocole suit sept phases ordonnées :

1. vérifier Git et les prérequis, nettoyer les sorties locales, compiler et lancer les tests ;
2. reconstruire les corpus dérivés puis vérifier leur empreinte ;
3. comparer **octet par octet** les sorties numérotées Java et `grep -E -n` ;
4. compter hors chronométrage les états/transitions du NFA et du DFA de recherche ;
5. mesurer les commandes complètes avec ordre équilibré ;
6. mesurer les phases dans plusieurs JVM indépendantes puis recalculer les résumés ;
7. tracer, vérifier les empreintes, publier `docs/assets/` atomiquement et supprimer les fichiers temporaires.

| Sortie locale, ignorée par Git | Contenu |
| :--- | :--- |
| `target/report/results/report.md` | Regex développées, statistiques, structure des automates et limites |
| `target/report/results/campaign.json` | Paramètres, Git HEAD/dirty, versions, commandes, empreintes et contrôles |
| `target/report/results/automata.csv` | Longueur de regex, états/arcs NFA et DFA de recherche |
| `target/report/results/cli.csv`, `jvm.csv` | Toutes les observations, y compris les prépassages |
| `target/report/results/*-summary.csv` | Statistiques recalculables depuis les observations |
| `target/report/results/validation.txt` | Sortie des tests Java/Python |
| `docs/assets/` | Six SVG, `benchmark.md` et `benchmark.json` utilisés directement par les Markdown |

La publication est transactionnelle : les assets existants ne sont remplacés qu'après validation complète. Un échec conserve donc la référence précédente. `--allow-dirty` autorise un essai exploratoire mais interdit la publication dans `docs/assets/`. `--purge` supprime les CSV et TXT **après** la publication ; `benchmark.md`, `benchmark.json`, les SVG et le rapport local restent disponibles.

Pour retracer manuellement une campagne locale non purgée :

```bash
.cache/report-venv/bin/python scripts/plot-report.py
./scripts/freeze-report.sh   # alias historique : republie la dernière campagne validée
```

Pour nettoyer toutes les sorties locales sans toucher aux assets publiés :

```bash
./scripts/clean-report.sh
```

Pour produire le rendu final avec le JAR et une archive minimale :

```bash
./scripts/package.sh
```

Le packaging exige un arbre Git propre, prend uniquement les fichiers suivis par Git, ajoute `regex-search.jar`, crée une archive déterministe et refuse un ZIP supérieur à 10 Mio. `.git`, `target`, environnements Python, caches et fichiers IDE sont exclus par construction.

## 6. Diagnostic rapide

| Symptôme                                   | Vérification utile                                                                      |
| :----------------------------------------- | :-------------------------------------------------------------------------------------- |
| `JAVA_HOME must point to a full JDK`       | Vérifier `JAVA_HOME/bin/java` et `JAVA_HOME/bin/javac`                                  |
| Maven hors ligne ne trouve pas un plugin   | Relancer sans `MAVEN_OFFLINE=1` avec accès réseau                                       |
| Le script refuse le grep du système        | Utiliser GNU grep, éventuellement avec `--grep`                                         |
| Le motif semble interprété par le terminal | Entourer la regex de guillemets simples                                                 |
| KMP refuse `a.b`                           | Le point est un opérateur ; utiliser `AUTO`, ou `a\.b` pour un point littéral           |
| Le dossier de résultats existe déjà        | Choisir un autre `--output-dir` pour préserver l’expérience précédente                  |
| Le fichier UTF-8 est rejeté                | Vérifier son encodage ; le lecteur ne remplace pas silencieusement les octets invalides |
| Les temps varient entre deux appels        | Consulter toutes les répétitions ; ne pas comparer une seule observation                |

[← Accueil](../README.md) · [Chapitre suivant : conception →](02-conception.md)
