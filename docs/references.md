[← Perspectives](06-perspectives.md) · [Accueil](../README.md)

# Sources, bibliographie et traçabilité

Les explications algorithmiques s’appuient d’abord sur le sujet et sur le chapitre fourni dans le dépôt. Les affirmations propres à l’implémentation sont reliées aux classes Java. Les chiffres du rapport proviennent de la référence expérimentale publiée et versionnée, et non d’un résultat produit pour un autre moteur.

## 1. Documents fournis avec le projet

**B. M. Bui-Xuan — _Projet 1 – automate. Clone de egrep avec support partiel des ERE_, DAAR, 20 septembre 2026.**  
[Sujet, deux pages](../src/main/java/com/sorbonne/specifications/daar_projet1.pdf).

Ce document fixe le périmètre : opérateurs retenus, stratégie par automates, variante KMP, importance des tests, comparaison à egrep et présentation des performances. Il recommande les corpus Gutenberg et les figures avec dispersion. Les contraintes de longueur et de contenu du rendu sont rappelées au chapitre 6.

**_Patterns, Automata, and Regular Expressions_, chapitre 10 fourni.**  
[PDF local](../src/main/java/com/sorbonne/specifications/ch10.pdf).

Les sections 10.2–10.4 présentent les automates et la construction des sous-ensembles. À la fin de la section 10.4 (p. 555–556 du PDF), le passage **« Minimization of Automata »** définit l'équivalence d'états par la séparation final/non-final puis par propagation à travers les transitions, et demande de compléter un DFA partiel par un *dead state*. `DFAM` respecte cette définition ; l'algorithme de Hopcroft est utilisé comme méthode de raffinement plus efficace. Les sections 10.5–10.8 relient ensuite les expressions régulières aux automates.

## 2. Références algorithmiques et techniques

**Robert Sedgewick et Kevin Wayne — ressources _Algorithms_, recherche de sous-chaînes.**  
[Présentation des recherches de sous-chaînes](https://algs4.cs.princeton.edu/53substring/) · [Variante KMPplus](https://algs4.cs.princeton.edu/53substring/KMPplus.java.html).

Ces ressources donnent une référence indépendante sur KMP. Leur code est une variante de présentation ; la table LPS du projet et ses coûts sont expliqués à partir de l’implémentation locale, pas présentés comme une copie de cette variante.

**GNU Project — manuel GNU grep.**  
[Usage et relation entre egrep et grep -E](https://www.gnu.org/s/grep/manual/html_node/Usage.html) · [Performance](https://www.gnu.org/s/grep/manual/html_node/Performance.html) · [Encodage des caractères](https://www.gnu.org/software/grep/manual/html_node/Character-Encoding.html).

Ces pages motivent l’usage de `grep -E`, le contrôle de la locale et la prudence sur les algorithmes internes utilisés selon le motif. La version réellement exécutée pendant les expériences est enregistrée dans les métadonnées.

**Oracle — documentation Java SE 21, BufferedReader.**  
[API BufferedReader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/BufferedReader.html).

Référence sur le lecteur bufferisé et le comportement de `readLine`. La taille de 64 K caractères est un choix du projet, pas une valeur optimale prescrite par cette documentation.

**Matplotlib — guide d’utilisation.**  
[Quick start](https://matplotlib.org/stable/users/explain/quick_start.html).

Bibliothèque utilisée pour produire les SVG et PNG à partir des CSV. La dépendance de tracé est optionnelle et distincte des dépendances de recherche.

## 3. Origine du corpus

Le corpus mesuré est la copie locale de **Jane Austen, _Pride and Prejudice_**, dont l’en-tête indique Project Gutenberg, eBook n° 1342. Cette identification est lue dans le fichier présent dans `Samples/`. Les conditions et notices du fichier sont conservées ; le benchmark ne les retire pas.

Les autres textes du répertoire `Samples/` constituent des instances disponibles pour de futures campagnes. Ils ne doivent pas être présentés comme mesurés dans les figures publiées : celles-ci utilisent un livre, ses répétitions et un corpus synthétique déterministe.

## 4. Retrouver les preuves locales

| Affirmation | Source vérifiable |
| :--- | :--- |
| Versions de compilation et de test | [pom.xml](../pom.xml) |
| Construction NFA | [NFA.java](../src/main/java/com/sorbonne/regex/NFA.java) |
| Sous-ensembles et classes de caractères | [DFA.java](../src/main/java/com/sorbonne/regex/DFA.java) |
| Minimisation de Hopcroft | [DFAMHopcroft.java](../src/main/java/com/sorbonne/regex/DFAMHopcroft.java) |
| Recherche de facteur par automate | [NativeSearch.java](../src/main/java/com/sorbonne/search/NativeSearch.java) |
| Préparation et parcours KMP | [KMPSearch.java](../src/main/java/com/sorbonne/search/KMPSearch.java) |
| Frontières du chronométrage Java | [Benchmark.java](../src/main/java/com/sorbonne/benchmark/Benchmark.java) |
| Frontières du chronométrage externe | [compare_egrep.py](../scripts/lib/compare_egrep.py) |
| Sources et environnement mesurés | [benchmark.json](assets/benchmark.json) |
| État des tests | [Protocole de validation](04-validation.md), journal local `target/report/results/validation.txt` |
| Calcul et présentation des figures | [plot-report.py](../scripts/plot-report.py) |

Les pages web complètent les sources locales ; elles ne servent pas de preuve aux durées mesurées. Chaque campagne locale conserve ses paramètres et observations jusqu’au prochain nettoyage ; seul le résumé de référence et les figures sont versionnés.
