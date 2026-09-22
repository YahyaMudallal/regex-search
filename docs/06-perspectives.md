[← Expériences](05-experiences.md) · [Accueil](../README.md) · [Références →](references.md)

# 06 — Limites actuelles, prochaines étapes et rendu

Le projet permet déjà de suivre une expression jusqu’à une recherche dans un fichier, de vérifier des propriétés sur les transformations d’automates et de confronter les résultats à GNU grep. La campagne confirme l’intérêt pratique du chemin KMP sur le motif littéral étudié. Elle montre aussi que le temps complet d’une commande ne se résume pas au coût théorique de sa boucle de recherche.

La suite du travail doit maintenant fermer les écarts avec le sujet et approfondir les mesures. Ajouter un algorithme sans tests de préservation du langage, ou annoncer un gain sans refaire la campagne, laisserait le rapport et le code décrire deux versions différentes.

## 1. État des exigences

| Élément du sujet                          | État dans cette version                | Preuve ou limite                                     |
| :---------------------------------------- | :------------------------------------- | :--------------------------------------------------- |
| Sous-ensemble regex demandé               | Implémenté pour les opérateurs retenus | Parseur, tests de priorité et d’échappement          |
| Arbre → NFA avec ε                        | Implémenté                             | Construction structurelle et tests                   |
| NFA → DFA par sous-ensembles              | Implémenté                             | Tests de langage et de déterminisme                  |
| DFA équivalent minimal                    | **À implémenter**                      | `DFAM.minimize` est une identité                     |
| Recherche dans les lignes d’un fichier    | Implémentée sous forme de comptage     | Lecture bufferisée et tests sur fichiers             |
| Affichage des lignes comme egrep          | **À compléter**                        | Aucun mode de sortie des lignes actuellement         |
| KMP expliqué et confronté aux automates   | Présent                                | Chapitres 3 et 5, même mot et même corpus            |
| Comparaison des performances à egrep      | Première campagne disponible           | GNU grep sur macOS ; protocole de processus complets |
| Tests et rapport argumenté                | Présents, à maintenir                  | Exemples, génération, données brutes et figures      |
| Rapport final de 5 à 10 pages, 12 maximum | À composer pour le rendu               | Cette documentation constitue la matière détaillée   |

Le sujet illustre une commande contenant `+`, mais son périmètre obligatoire énumère un sous-ensemble plus restreint. Le rapport et les exemples du projet s’en tiennent aux opérateurs réellement implémentés ; il ne faut pas présenter l’exemple illustratif du sujet comme une expression déjà supportée telle quelle.

## 2. Implémenter la minimisation avec un contrat testable

Deux états sont équivalents si aucune continuation ne permet de les distinguer : depuis chacun, les mêmes suffixes conduisent à une acceptation. La future implémentation devra identifier ces classes d’équivalence et construire leur graphe quotient.

Avant de choisir les structures, plusieurs points doivent être fixés :

1. **DFA partiel.** Une transition manquante représente le rejet. Le traitement devra être équivalent à une complétion par un état puits, même si le résultat final reste partiel.
2. **Classes de caractères.** Les arcs littéraux et complémentaires doivent être traités comme une partition cohérente de l’alphabet. Deux états ne peuvent pas être déclarés équivalents en ignorant leurs exclusions.
3. **Mutabilité.** Il faut documenter si le résultat est un nouveau graphe ou si l’entrée est modifiée. Une nouvelle construction faciliterait les tests avant/après et éviterait des effets sur les états partagés.
4. **Position dans la chaîne.** Minimiser le DFA du motif ne garantit pas que l’automate obtenu après la préparation de `NativeSearch` sera lui aussi minimal.

La validation devra comparer les langages avant et après, vérifier le déterminisme, examiner des automates avec états inaccessibles et vérifier l’idempotence en nombre d’états et en langage. Le test actuel `assertSame` de DFAM décrit seulement le placeholder : il devra évoluer lors de cette implémentation.

La campagne pourra alors publier le nombre d’états et d’arcs avant/après, le coût de minimisation et l’effet sur la recherche. Une réduction de taille ne sera pas automatiquement un gain de temps sur de petits fichiers : il faudra amortir son coût de préparation.

## 3. Compléter la sortie de recherche

Le mode `scripts/search.sh` affiche désormais les lignes avec leur numéro, ce qui rapproche l’interface de l’usage attendu d’egrep. Il permet de vérifier que les **mêmes lignes**, et pas seulement le même nombre de lignes, sont sélectionnées.

Cette sortie devra rester séparée du protocole de comptage. Comparer un moteur qui imprime toutes les lignes à un autre qui n’imprime qu’un entier changerait le travail mesuré. Le contrôle exact des résultats peut s’effectuer avant le chronométrage, puis les deux commandes mesurées conserver le mode compteur.

## 4. Approfondir les coûts observés

Le premier travail utile serait de mesurer les phases de préparation sur des motifs de tailles variées : parseur, construction NFA, déterminisation et préparation de recherche. Cela permettrait de relier les courbes aux recopies et aux sous-ensembles réellement produits.

Sur le parcours, une campagne dans une JVM persistante compléterait l’usage en ligne de commande. Elle devrait annoncer précisément si elle inclut le décodage, la création des lignes et la lecture du fichier. Charger tout le corpus en mémoire répondrait à une autre question et modifierait le profil mémoire ; ce choix devrait apparaître dans le protocole.

Quelques familles de cas restent à étudier :

| Axe                                      | Pourquoi il est utile                                          |
| :--------------------------------------- | :------------------------------------------------------------- |
| Plusieurs livres et types de textes      | Ne pas généraliser à partir d’une seule distribution de lignes |
| Motifs plus longs et préfixes répétitifs | Étudier KMP et le coût du parseur au-delà des exemples courts  |
| Alternances et étoiles plus complexes    | Mesurer le nombre de sous-ensembles accessibles                |
| Longues lignes isolées                   | Examiner les allocations de `readLine` et la mémoire maximale  |
| Différentes tailles de tampon            | Justifier empiriquement le compromis des 64 K caractères       |
| Plusieurs campagnes et machines          | Séparer une tendance robuste du bruit local                    |

Les optimisations envisageables doivent être reliées à un coût identifié. Des fragments NFA assemblés dans un graphe commun éviteraient des recopies. Des identifiants d’états compacts et des ensembles de bits pourraient réduire les allocations pendant la déterminisation. Une simulation NFA ou une déterminisation à la demande pourraient éviter de construire tout le DFA avant de parcourir un petit fichier. Ces pistes ne sont ni implémentées ni mesurées dans cette version.

<a id="rendu"></a>

## 5. Passer de cette documentation au rapport de rendu

Le [sujet fourni](../src/main/java/com/sorbonne/specifications/daar_projet1.pdf) recommande 5 à 10 pages et fixe une limite formelle de 12 pages. Les chapitres Markdown sont conçus comme une documentation consultable et détaillée ; les concaténer tels quels ne respecte pas nécessairement cette contrainte.

Un plan de synthèse de dix pages peut reprendre :

| Pages indicatives | Matière à retenir                                                |
| :---------------- | :--------------------------------------------------------------- |
| 1                 | Objectif, définition de la recherche de facteur, périmètre réel  |
| 2                 | Architecture et structures de données                            |
| 3–4               | Arbre, NFA, déterminisation et gestion du point universel        |
| 5                 | Recherche de facteur et KMP, avec coûts de préparation/parcours  |
| 6                 | Minimisation : algorithme final ou limite explicitement déclarée |
| 7                 | Tests, générateurs, oracles et exemple de cas difficile          |
| 8–9               | Protocole, deux ou trois figures, résultats et discussion        |
| 10                | Limites, conclusion et références                                |

Les CSV complets, commandes détaillées et guides d’installation restent consultables dans l’archive. Ils n’ont pas besoin d’occuper les pages du rapport principal. L’objectif est qu’un lecteur puisse vérifier les affirmations sans être obligé de parcourir des dizaines de tableaux.

### Préparer l’archive

Le sujet demande le code commenté, la documentation, un binaire, les instances de test et un moyen de construction, avec un volume de l’ordre d’une dizaine de mégaoctets. Il mentionne Makefile ou Ant ; le dépôt utilise actuellement Maven et Bash. Cette différence d’outillage doit être vérifiée pour le rendu, sans prétendre qu’un Makefile est déjà fourni.

Ne pas inclure `target/report-corpora/`, le cache Maven ou un environnement virtuel Python : les corpus ×8 et ×32 sont régénérables, et leur présence gonflerait inutilement l’archive. Conserver en revanche le JAR construit pour le rendu, les sources, les fichiers texte nécessaires, les données brutes publiées et leurs instructions de reproduction.

## 6. Ce que cette version permet de conclure

La chaîne implémentée sait préparer un motif et rechercher ses occurrences sans conserver tout le fichier en mémoire. Les tests donnent des éléments solides sur la préservation du langage et le comportement des deux moteurs dans le domaine couvert. Sur la campagne publiée, KMP est le chemin le moins coûteux du projet pour le mot littéral choisi ; GNU grep reste plus rapide en temps de commande complet.

La minimisation et la sortie des lignes restent nécessaires pour achever le périmètre annoncé. Les ajouter, puis réexécuter les mêmes protocoles, donnera une base de comparaison plus informative que de changer simultanément les motifs, les fichiers et la méthode de mesure.

[← Expériences](05-experiences.md) · [Accueil](../README.md) · [Références →](references.md)
