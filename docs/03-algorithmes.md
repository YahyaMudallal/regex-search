[← Conception](02-conception.md) · [Accueil](../README.md) · [Validation →](04-validation.md)

# 03 — Algorithmes, invariants et coûts

L’analyse qui suit sépare trois choses : le problème mathématique, la construction choisie et les coûts du code présent dans le dépôt. Une borne connue pour un algorithme ne se transfère pas automatiquement à une implémentation qui recopie ses structures à chaque étape.

## 1. Notations

| Symbole | Signification |
| :--- | :--- |
| $m$ | Longueur de l’expression, ou du motif littéral selon la section |
| $n$ | Longueur d’une ligne en unités UTF-16 |
| $C$, $L$ | Nombre total d’unités UTF-16 du contenu des lignes, et nombre de lignes |
| $N$, $E$ | Nombre d’états et d’arcs du NFA à déterminer |
| $X$ | Nombre d’exclusions de caractères stockées dans ce graphe |
| $K$ | Nombre de classes de caractères considérées par la déterminisation |
| $R$ | Nombre de sous-ensembles accessibles effectivement construits |
| $B$, $M$ | Taille du tampon de lecture et longueur de la plus grande ligne |

Les coûts « moyens » ci-dessous supposent des accès moyens constants aux tables de hachage. La taille d’un ensemble d’états intervient dans son calcul de hash : utiliser cet ensemble comme clé n’est pas une opération gratuite.

## 2. Construire l’arbre syntaxique

Le [parseur](../src/main/java/com/sorbonne/regex/RegexParser.java) distingue les lettres des opérateurs, puis utilise une pile d’opérandes et une pile d’opérateurs. Les priorités sont étoile, concaténation et alternative ; les parenthèses délimitent les réductions. Les opérateurs binaires sont associatifs à gauche.

Pour `a|bc*`, la structure est :

```mermaid
flowchart TB
    OR["ALTERNATION · |"] --> A["LETTER · a"]
    OR --> CAT["CONCATENATION"]
    CAT --> B["LETTER · b"]
    CAT --> STAR["STAR · *"]
    STAR --> C["LETTER · c"]
```

L’arbre garde le rôle des caractères. La feuille issue de `\.` représente un point littéral ; un point non échappé devient un nœud `DOT`. C’est cette distinction qui permet au benchmark de sélectionner le bon moteur.

**Coût du code actuel.** Chaque jeton est lu une fois, chaque opérateur est empilé et dépilé une fois : temps et mémoire $O(m)$, arbre compris. Les groupes ne produisent pas de nœuds de protection intermédiaires. Les échappements, y compris un antislash final littéral, sont conservés.

La profondeur de l’arbre peut atteindre $O(m)$, mais ni le parseur ni le constructeur NFA n’utilisent la récursion Java. Les tests couvrent 20 000 lettres concaténées et 20 000 niveaux de groupes ou d’étoiles sans seuil temporel dépendant de la machine.

## 3. Passer de l’arbre à un NFA avec ε

La construction de [NFA](../src/main/java/com/sorbonne/regex/NFA.java) suit la décomposition structurelle présentée dans le chapitre fourni, section 10.8. Chaque sous-expression produit un fragment avec une entrée et une sortie. Les fragments sont reliés sans énumérer les mots du langage, qui peuvent être en nombre infini.

| Nœud de l’arbre | Construction |
| :--- | :--- |
| Lettre / point | Deux états reliés par une transition qui consomme un caractère |
| Concaténation $rs$ | Relier la sortie du fragment $r$ à l’entrée du fragment $s$ par ε |
| Alternative $r\mid s$ | Ajouter une entrée qui rejoint les deux branches et une sortie commune |
| Étoile $r^*$ | Ajouter un passage direct pour le mot vide, une entrée dans $r$, une boucle et une sortie |

Un graphe illustratif pour `a|bc*` est le suivant. Les noms servent à suivre les chemins, pas à imposer la numérotation du constructeur.

```mermaid
flowchart LR
    START(("entrée")) -->|ε| A0(("a₀"))
    A0 -->|a| A1(("a₁"))
    A1 -->|ε| END((("sortie")))
    START -->|ε| B0(("b₀"))
    B0 -->|b| B1(("b₁"))
    B1 -->|ε| S(("entrée *"))
    S -->|ε| T(("sortie *"))
    S -->|ε| C0(("c₀"))
    C0 -->|c| C1(("c₁"))
    C1 -->|ε| C0
    C1 -->|ε| T
    T -->|ε| END
```

### Pourquoi cette construction reconnaît le bon langage

La justification se fait par induction sur l’arbre. Une feuille reconnaît exactement son caractère. Une concaténation doit traverser successivement les deux fragments. Une alternative choisit l’une des branches. L’étoile peut éviter le fragment, le parcourir une fois ou reprendre sa boucle autant de fois que nécessaire. Les transitions ε assurent ces compositions sans ajouter de caractère au mot reconnu.

Le constructeur travaille dans un graphe commun. Une pile explicite effectue le parcours postordre ; chaque fragment ne conserve que son entrée et sa sortie. Tous les états sont initialement intermédiaires, puis seules les extrémités du fragment final reçoivent les statuts initial et final.

### Taille et construction linéaires

Chaque nœud ajoute un nombre constant d’états et d’arcs, sans recopier les sous-graphes ni rechercher leurs extrémités. La construction est $O(m)$ en temps moyen avec les ensembles de hachage, et $O(m)$ en mémoire. Elle ne modifie pas l’arbre fourni.

## 4. Déterminiser par les sous-ensembles

Un NFA peut être dans plusieurs états après avoir lu le même préfixe. L’idée de la [déterminisation](../src/main/java/com/sorbonne/regex/DFA.java) consiste à représenter cet ensemble par un état unique du DFA.

Pour un ensemble $S$, sa fermeture ε, notée $\varepsilon\text{-fermeture}(S)$, rassemble les états atteignables sans lire de caractère. Le départ du DFA est :

$$
S_0=\varepsilon\text{-fermeture}(\{q_0\}).
$$

Après lecture de $c$ :

$$
\delta_D(S,c)=\varepsilon\text{-fermeture}\left(\bigcup_{q\in S}\delta_N(q,c)\right).
$$

L’ensemble obtenu est final s’il contient au moins un état final du NFA. Une file traite les ensembles accessibles ; une table associe chaque `BitSet`, jamais modifié après insertion, à son état DFA. Les cycles ε terminent parce que la fermeture mémorise les états déjà visités.

**Invariant.** Après lecture d’un préfixe $u$, l’état courant du DFA représente exactement tous les états dans lesquels le NFA pourrait se trouver après $u$, transitions ε comprises. Le critère d’acceptation découle directement de cet invariant.

Le résultat est un **DFA partiel**. Quand aucun état n’est atteignable, aucun arc n’est ajouté. L’absence d’arc représente le rejet ; un état puits explicite n’est pas obligatoire pour cette utilisation.

### Le point universel : un piège de déterminisation

Supposons que deux branches proposent un arc sur `a` et un arc universel. À la lecture de `a`, **les deux destinations doivent contribuer** au sous-ensemble suivant. Donner arbitrairement priorité à la lettre ferait perdre une partie du langage.

Pour chaque sous-ensemble courant, le code découpe l’alphabet en classes disjointes : un singleton par lettre utile dans cet état, puis la classe complémentaire si nécessaire. Les exclusions de ses arcs `ANY` participent au découpage. Les lettres présentes ailleurs dans le NFA ne créent pas d’arcs inutiles ici.

| Classe | Arcs du NFA à prendre en compte |
| :--- | :--- |
| `a` | Tous les arcs littéraux `a` et les arcs `ANY` qui acceptent `a` |
| Autres caractères | Les arcs `ANY` qui acceptent le représentant de cette classe |

Dans le DFA, la seconde classe est stockée par `anyExcept`, sans créer 65 536 transitions pour les 65 536 valeurs possibles d’un `char` Java. Cette partition rend les décisions disjointes ; ce n’est pas un ordre de priorité entre deux arcs concurrents.

### Coût

Les index séparent les arcs ε, les arcs littéraux groupés par lettre et les arcs ANY. Un déplacement ignore les arcs portant d’autres lettres, et une fermeture ne suit que les arcs ε. L’indexation et la lecture des exclusions sont bornées par $O(N+E+X)$. Chaque paire « sous-ensemble, classe » peut parcourir au plus les états et arcs de l’entrée pour effectuer le déplacement et la fermeture. La borne utilisée est donc :

$$
T_D=O\bigl(N+E+X+RK(N+E)\bigr),\qquad R\le 2^N.
$$

La mémoire supplémentaire est de l’ordre de :

$$
O\bigl(N+E+X+R(\lceil N/64\rceil+K)\bigr).
$$

Elle comprend les index, les bitsets mémorisés en mots de 64 bits, le graphe produit et ses exclusions locales. $K$ majore le nombre de classes locales. Aucune table de toutes les fermetures ε, potentiellement quadratique, n’est pré-calculée. L’alphabet `char` est borné ; la recherche d’un représentant de la classe complémentaire est donc elle aussi bornée par sa taille. L’explosion du nombre de sous-ensembles reste la difficulté principale : indexer les arcs évite des parcours inutiles, mais ne supprime pas cette croissance possible.

## 5. La minimisation : une étape prévue, pas un résultat acquis

[DFAM](../src/main/java/com/sorbonne/regex/DFAM.java) expose pour l’instant :

```java
public static Automaton minimize(Automaton dfa)
```

La méthode contrôle seulement la non-nullité et retourne le même objet. Son coût actuel est $O(1)$ en temps et en mémoire supplémentaire. Il serait trompeur d’attribuer ce coût à une véritable minimisation.

Le contrat de la future version devra préserver le langage, traiter correctement les transitions absentes et respecter les classes de caractères. Un DFA partiel demande notamment de décider comment représenter le rejet lors du raffinement des classes d’états. Le chapitre des [perspectives](06-perspectives.md) décrit les validations attendues.

## 6. Trouver une occurrence avec NativeSearch

Le DFA précédent reconnaît un **mot entier**. Pour chercher ce mot partout dans une ligne, il ne suffit pas de revenir à son état initial après chaque échec. Exemple : chercher `ab` dans `aab`. Après avoir consommé le premier `a`, un échec sur le second `a` peut faire oublier que ce second caractère est lui-même le début d’une occurrence.

La [préparation](../src/main/java/com/sorbonne/search/NativeSearch.java) traite tous les départs simultanément, directement depuis le NFA. Si $S_0$ est la fermeture ε du départ, la construction de `DFA.forSearch` utilise :

$$
S' = \varepsilon\text{-fermeture}(\operatorname{move}(S,c)) \cup S_0.
$$

Réinjecter $S_0$ permet de recommencer au caractère suivant sans oublier les occurrences en cours. Le moteur teste l’acceptation avant la lecture et après chaque caractère.

Tous les sous-ensembles acceptants partagent un état terminal sans transitions. La recherche s’arrête immédiatement à cet état : développer ses continuations serait inutile. Ce DFA est destiné à la détection d’une occurrence, **pas à la reconnaissance d’un mot entier**. Pour cette dernière opération, `DFA.convert` conserve les transitions après acceptation.

### Une seule déterminisation dans le pipeline

`Benchmark` construit le NFA, appelle `DFA.forSearch`, transmet le résultat à l’étape DFAM inchangée puis l’indexe avec `NativeSearch.fromSearchDfa`. Il n’y a plus de deuxième déterminisation. `NativeSearch.prepareNfa` fournit également cette préparation directe ; l’ancienne méthode `prepare` conserve son contrat de validation d’un DFA de mots entiers.

Si l’arbre accepte ε, `Benchmark` utilise directement un moteur toujours vrai. Il parcourt encore le fichier pour compter ou restituer les lignes et détecter les erreurs de décodage. La stratégie annoncée reste AUTOMATON pour un motif non littéral, mais les phases NFA/DFA/DFAM non exécutées valent zéro.

L’index contient des identifiants entiers, une classification globale des caractères et des pages de transitions avec destination par défaut. Seules les pages contenant des exceptions sont allouées. L’indexation reste bornée par $O(RK+T+X)$ au pire, mais n’alloue pas systématiquement une table dense $RK$. Le parcours fait des accès directs : $O(n)$ temps au pire et $O(1)$ mémoire supplémentaire hors index.

La déterminisation reste potentiellement exponentielle. Aucun budget d’états ni repli sur une simulation NFA n’est encore implémenté. `NativeSearch` ne délègue ni à `String.contains` ni à `Pattern`.

## 7. KMP pour les motifs littéraux

KMP évite de reconsidérer inutilement les caractères du texte après un échec partiel. Il prépare une table LPS, pour *Longest Proper Prefix which is also Suffix*. À chaque position du motif, la table donne la longueur du plus long préfixe propre qui est aussi un suffixe de la portion déjà reconnue.

Pour `ababac` :

| Position | 0 | 1 | 2 | 3 | 4 | 5 |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| Caractère | a | b | a | b | a | c |
| LPS | 0 | 0 | 1 | 2 | 3 | 0 |

Si `ababa` a été reconnu et que le caractère suivant ne permet pas de lire `c`, le suffixe `aba` reste un préfixe candidat. Le moteur ajuste l’indice du motif au lieu de faire reculer celui du texte.

**Invariant.** Après traitement d’un préfixe du texte, l’indice $j$ représente la longueur du préfixe du motif reconnu à la fin de ce texte traité. Un repli vers `lps[j-1]` conserve un suffixe compatible ; les candidats éliminés ne peuvent pas former une occurrence.

La construction LPS est $O(m)$ en temps et en mémoire. La recherche est $O(n)$ : l’indice du texte ne recule jamais, et le nombre total de replis du motif est amorti par ses avancées. Le moteur préparé ajoute seulement deux indices locaux pendant le parcours.

Sur un fichier entier, avec une préparation unique :

$$
T_{KMP}=O(m+C+L),\qquad M_{KMP}=O(m+B)\quad\text{en comptage}.
$$

Le parseur et l’extraction du littéral sont eux aussi linéaires : la borne temporelle couvre donc toute la préparation et le comptage. Un curseur KMP conserve son indice entre les blocs, puis est réinitialisé à chaque frontière de ligne. Le mode d’affichage garde une ligne entière et ajoute $O(M)$ mémoire.

## 8. Bilan des coûts et portée des optimisations

| Étape | Temps / propriété du code actuel | Limite à garder en tête |
| :--- | :--- | :--- |
| Analyse syntaxique | $O(m)$ | Piles explicites, arbre de taille $O(m)$ |
| NFA | $O(m)$ temps moyen et mémoire | Graphe commun, aucune copie de fragment |
| DFA | $O(N+E+X+RK(N+E))$ en moyenne | $R$ peut atteindre $2^N$ |
| DFAM | $O(1)$ actuellement | Identité provisoire, aucune minimisation |
| Préparation NativeSearch | Une déterminisation directe, puis indexation par pages | Explosion possible pendant la préparation |
| Recherche NativeSearch préparée | $O(n)$ au pire | Coût et taille de l’index exclus de ce parcours |
| Préparation KMP | $O(m)$ | Motif littéral uniquement |
| Recherche KMP préparée | $O(n)$ | Préparation et parseur à ajouter au temps complet |
| Comptage par blocs | $O(C+L)$ temps, $O(B)$ mémoire hors moteur | Affichage : une ligne entière reste allouée |

Le rendu d’`Automaton` utilise aussi un index des arcs sortants : $O(N+E+Z)$ pour $Z$ caractères produits. Le rendu de `SyntaxTree` est itératif et utilise un accumulateur unique ; son coût dépend de la taille de sortie, qui peut être quadratique avec l’indentation d’un arbre profond. Les optimisations ne suppriment pas l’explosion possible du DFA et ne constituent pas une minimisation. Les mesures archivées du 22 septembre décrivent la version précédente.

[← Conception](02-conception.md) · [Accueil](../README.md) · [Validation →](04-validation.md)
