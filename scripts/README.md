# Scripts du projet

Depuis la racine du dépôt :

```bash
# Nettoyage, compilation et tests Java + tests du protocole de comparaison.
./run.sh

# Une mesure Java détaillée, sur le fichier original.
./scripts/benchmark.sh Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy' AUTO

# Affichage des lignes correspondantes avec leur numéro, sans timings.
./scripts/search.sh Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy'

# Comparaison des commandes Java et GNU grep -E.
./scripts/compare-egrep.sh Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy'

# Imposer les automates pour comparer ce chemin à grep, même sur un mot littéral.
./scripts/compare-egrep.sh Samples/PrideAndPrejudice.txt 'Elizabeth' \
  --strategy AUTOMATON --runs 20 --warmups 3
```

Les scripts peuvent être lancés depuis un autre répertoire : les chemins de fichiers
fournis sont relatifs au **répertoire de l'appelant**. Toujours protéger la regex et
les chemins contenant des espaces avec des guillemets.

`run.sh` ne lance plus `Main` et n'installe plus le JAR dans le dépôt Maven local.
Les scripts de recherche et de benchmark compilent avant de lancer Java ; cette
compilation est exclue des résultats affichés ou mesurés. Ils ne relancent pas les
tests : exécuter `./run.sh` au préalable.

## Prérequis

- Bash, JDK compatible avec `pom.xml` (actuellement 21 ou supérieur), Maven ≥ 3.6.3.
- Python ≥ 3.9 pour la comparaison et les tests des scripts ; bibliothèque standard uniquement.
- GNU grep et une locale UTF-8 pour la comparaison. Sur macOS, `ggrep` est recherché
  avant `grep`, car le grep fourni par macOS n'est pas GNU grep.
  L'option `--grep /chemin/vers/grep` permet de choisir explicitement l'exécutable.

Le JDK indiqué par `JAVA_HOME` est utilisé pour compiler et lancer le projet.
Si `JAVA_HOME` est absent, les scripts découvrent le JDK de `java` dans le PATH.
Maven télécharge les dépendances manquantes. Pour utiliser uniquement son cache :

```bash
MAVEN_OFFLINE=1 ./run.sh
MAVEN_OFFLINE=1 ./scripts/benchmark.sh Samples/PrideAndPrejudice.txt 'Elizabeth'
```

## Ce que compare `compare-egrep.sh`

Les deux programmes comptent les **lignes contenant au moins une occurrence**, avec
la même casse et sans imprimer le texte. Java utilise `Main --count` et grep utilise
`grep -E -c`. Le statut 1 de grep est accepté lorsqu'il annonce zéro correspondance.
L'option `-E` correspond à l'ancien usage d'`egrep`, selon le
[manuel GNU grep](https://www.gnu.org/s/grep/manual/html_node/Usage.html).

Le protocole est le suivant :

1. Copier le fichier en UTF-8 et convertir les fins de ligne CRLF/CR en LF. Les deux
   programmes lisent **exactement cette même copie temporaire** ; l'original reste intact.
   La conversion se fait par blocs et n'est pas chronométrée. Elle évite que
   `BufferedReader` et grep interprètent différemment les séparateurs.
2. Utiliser une locale UTF-8 installée et un motif appartenant au sous-ensemble commun :
   concaténation, alternative `|`, étoile `*`, point `.` et parenthèses. Les extensions
   non implémentées (`+`, `?`, classes, ancres, quantificateurs, etc.) sont refusées.
   Les seuls échappements admis sont `\.`, `\*`, `\|`, `\(`, `\)` et `\\`.
3. Refuser le texte UTF-8 invalide, les octets NUL et les caractères hors BMP, dans le
   texte ou le motif. Les accents usuels sont acceptés. Les caractères hors BMP,
   comme les emoji, sont exclus car le moteur Java travaille en unités UTF-16,
   tandis que grep en locale UTF-8 travaille en caractères. Cette restriction porte
   uniquement sur la comparaison ; le benchmark Java garde son contrat existant.
4. Vérifier les comptages, effectuer 3 passages préalables par moteur puis 10 mesures
   par moteur, avec un ordre mélangé à chaque paire et une graine fixée.
5. Vérifier à nouveau le comptage après chaque exécution. Toute différence, erreur ou
   expiration du délai interrompt l'expérience sans publier de statistiques finales.

**La durée mesurée est celle du processus complet**, depuis son lancement jusqu'à
sa terminaison : démarrage de la JVM, préparation du motif, lecture, recherche,
comptage et sortie du nombre. Les sorties sont capturées pour les deux moteurs.
Il ne faut pas comparer ces durées au seul temps interne de recherche de Java.

Chaque mesure Java lance une **nouvelle JVM**. Les passages préalables sollicitent le
cache de fichiers du système ; ils ne constituent pas un échauffement du JIT conservé
entre mesures. Le protocole compare donc les usages en ligne de commande. La campagne fixe ci-dessous complète cette mesure avec plusieurs JVM persistantes, tout en incluant les IO du fichier. L'ordre aléatoire atténue un biais d'ordre, sans supprimer le bruit
lié au système. Éviter les autres charges lourdes pendant une expérience.

La phase `DFAM` est encore un placeholder : les résultats ne représentent pas
les performances d'une minimisation effective.

## Options et résultats

```bash
./scripts/compare-egrep.sh --help
./scripts/compare-egrep.sh Samples/PrideAndPrejudice.txt 'Elizabeth|Darcy' \
  --runs 20 --warmups 3 --seed 42 --timeout 60 \
  --output-dir target/benchmarks/experience-01
```

`--strategy` accepte `AUTO`, `KMP` ou `AUTOMATON`. KMP forcé refuse une regex non
littérale. Le délai est en secondes **par processus**, pas pour l'expérience entière.
Une seule répétition est autorisée pour vérifier le fonctionnement, mais son écart
type est indiqué comme indisponible ; utiliser plusieurs répétitions pour analyser
la dispersion. Les appels de validation s'ajoutent toujours aux passages préalables.

Par défaut, un nouveau dossier daté est créé dans `target/benchmarks/`, contenant :

- `runs.csv` : mesures brutes en nanosecondes, ordre, moteur et comptage ;
- `summary.csv` : moyenne, médiane, écart type d'échantillon, minimum et maximum en ms ;
- `metadata.json` : motif, stratégie, versions, locale, paramètres, commandes et
  empreinte SHA-256 du corpus normalisé. Le chemin temporaire des commandes est
  conservé à titre descriptif ; le fichier temporaire est supprimé en fin d'expérience.

Un dossier de sortie existant est refusé pour préserver les résultats précédents.
Les exports sous `target/` sont supprimés par le prochain `./run.sh` (`mvn clean`) :
utiliser un autre `--output-dir` pour conserver une campagne.

`lib/common.sh` partage les vérifications et `lib/compare_egrep.py` gère les mesures
et statistiques. Le point d'entrée utilisateur reste le script Bash. `search.sh`
affiche les lignes au format `numéro:contenu`; `benchmark.sh` affiche les compteurs
et les durées.

## Campagne de référence, assets et rendu

```bash
./scripts/report-campaign.sh             # pipeline complet + publication atomique de docs/assets/
./scripts/report-campaign.sh --purge     # idem puis suppression des CSV/TXT locaux
./scripts/report-campaign.sh --allow-dirty # essai local ; ne publie jamais docs/assets/
./scripts/freeze-report.sh               # alias historique : republie la dernière campagne validée
./scripts/clean-report.sh                # efface les résultats locaux uniquement
./scripts/package.sh                     # tests + JAR + ZIP de rendu < 10 Mio
```

Le profil [`report-profile.json`](report-profile.json) est versionné et fixe la campagne. Les mots littéraux sont des témoins KMP ; les cas automate emploient des alternatives, wildcards et une famille de croissance `(a|b)*a(a|b)…(a|b)b` aux profondeurs 5, 7 et 9. Un helper Java compte les états/transitions NFA et DFA **hors chronométrage**, afin de relier les temps à la structure réellement construite.

La campagne de référence nécessite **Java 21**, Python ≥ 3.12, GNU grep et une locale UTF-8. L'environnement Matplotlib est mis en cache dans `.cache/report-venv/`. Une campagne normale refuse un arbre Git sale ; `--allow-dirty` existe seulement pour explorer localement et bloque la publication.

Après validation des sorties numérotées contre GNU grep, le système recueille les observations CLI et JVM, recalcule les résumés, génère les six figures dans un dossier temporaire puis remplace `docs/assets/` avec restauration automatique en cas d'échec. Les corpus dérivés et doubles PNG sont ensuite supprimés. Sans `--purge`, les CSV/TXT restent sous `target/report/results/` ; avec `--purge`, ils sont supprimés uniquement après la publication.

`package.sh` refuse lui aussi un arbre Git sale. Il archive uniquement `git ls-files` et le JAR final, ce qui exclut mécaniquement `.git`, `target`, `.cache`, environnements virtuels et fichiers IDE. L'archive est déterministe et le script échoue si elle dépasse 10 Mio.

`compare-egrep.sh` reste disponible pour les essais libres avec paramètres ; ces essais ne produisent pas la référence du README. Voir le [protocole](../docs/05-experiences.md) et les [commandes détaillées](../docs/01-utilisation.md#5-reproduire-toute-la-campagne-du-rapport).
