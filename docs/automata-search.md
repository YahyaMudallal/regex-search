# Déterminisation et recherche : analyse des algorithmes

## Problèmes corrigés

La première version de `DFA.convert` séparait les arcs littéraux et les arcs `ANY`.
Sur `ac|.b`, lire `a` doit pourtant conserver les deux chemins : ils permettent
ensuite d'accepter respectivement `ac` et `ab`. Donner priorité à la lettre perd
le second chemin ; conserver deux arcs compatibles ne produit pas un DFA.

La conversion réunit maintenant toutes les destinations compatibles, puis leur
fermeture ε. Les étiquettes de sortie sont disjointes. Un arc `ANY` peut exclure
les lettres traitées explicitement : `Transition.anyExcept(source, destination,
exclusions)`. Les exclusions sont immuables et participent réellement à `matches`.
Elles ne constituent pas une convention de priorité dépendant du moteur de recherche.

La première version de `NativeSearch` oubliait les départs qui se chevauchent.
Elle pouvait manquer `ab` dans `aab`, ou `aab` dans `aaab`. Réessayer seulement
le caractère courant ne suffit pas dans tous les cas. Elle ne vérifiait pas non
plus l'acceptation du mot vide avant la lecture : `(ab)*` doit correspondre même
au texte `a`. Enfin, son index écrasait les arcs concurrents et ignorait les ε.
Ces entrées invalides sont maintenant signalées.

`NativeSearch` prépare un automate de recherche : un nouveau départ peut
consommer un préfixe quelconque, puis rejoindre le départ du motif par ε.
La déterminisation conserve simultanément les positions de départ pertinentes.
Le parcours s'arrête au premier état final ; il cherche donc une occurrence,
pas une reconnaissance du texte entier.

## Contrat et utilisation

`SearchAlgorithm<P>` représente une opération commune : une sous-chaîne du texte
est-elle reconnue par le motif ? Le type du motif dépend de l'algorithme.

```java
SearchAlgorithm<String> kmp = new KMPSearch();
boolean literalFound = kmp.search("bonjour monsieur", "monsieur");

Automaton nfa = NFA.buildNFA(RegexParser.parse("ac|.b"));
Automaton dfa = DFA.convert(nfa);
SearchAlgorithm<Automaton> automataSearch = new NativeSearch();
boolean regexFound = automataSearch.search("xxab", dfa);
```

Le motif de KMP est littéral. Le motif de `NativeSearch` est un DFA, et non une
String contenant une regex. Le nom `NativeSearch` est conservé, mais cette classe
n'appelle pas le moteur Java. L'ancienne méthode statique `search(dfa, text)`
est remplacée par le contrat d'instance `search(text, dfa)`.

Pour plusieurs lignes, il faut préparer une seule fois :

```java
NativeSearch.Prepared prepared = NativeSearch.prepare(dfa);
boolean first = prepared.search("xxab");
boolean second = prepared.search("xxac");
boolean third = prepared.search("xxxx");
```

La préparation capture une copie indépendante du langage. Les changements
ultérieurs des statuts ou des transitions du graphe original n'affectent pas
le résultat préparé. Les appels à `search` n'ont pas d'état d'exécution partagé.
Le graphe ne doit pas être modifié pendant la préparation.

## Complexités et limites

On note N le nombre d'états du NFA, E ses arcs, X le nombre total d'exclusions
stockées dans ces arcs, K le nombre de classes de caractères et R le nombre
de sous-ensembles accessibles. R peut atteindre 2^N. Les bornes moyennes
ci-dessous supposent les accès aux tables de hachage en temps constant.

| Opération | Temps | Mémoire supplémentaire | Justification |
| --- | --- | --- | --- |
| Indexation du NFA et partition des caractères | O(N + E + X) | O(N + E + X) | Un parcours des états, arcs et exclusions |
| Déplacement pour une classe | O(N + E) au pire | O(N) | Chaque arc sortant de l'ensemble courant est examiné au plus une fois |
| Fermeture ε | O(N + E) au pire | O(N) | Ensemble des états visités et parcours des seuls arcs indexés |
| `DFA.convert` | O(N + E + X + R K (N + E)) | O(N + E + X + R(N + K)) | Chaque sous-ensemble accessible est traité pour chaque classe |
| Préparation de `NativeSearch` | Copie/indexation puis déterminisation du graphe augmenté | Potentiellement exponentielle dans la taille du motif | Les départs possibles d'une occurrence sont déterminisés ensemble |
| `Prepared.search` sur n char | O(n) | O(1) | Un passage, un état courant, aucun tableau de copie du texte |
| `NativeSearch.search(text, dfa)` | Préparation + O(n) | Préparation + O(1) | Cette méthode de commodité prépare à chaque appel |
| KMP sur un texte de n char et un motif de m char | O(n + m) | O(m) | Table LPS puis parcours sans recul dans le texte |

La conversion n'appelle plus `Automaton.getOutgoingTransitions` à chaque visite,
car cette méthode parcourt toute la liste des arcs. Elle construit un index local
une fois et calcule l'alphabet une fois. Elle partage aussi les exclusions immuables
de sortie au lieu de matérialiser 65 536 transitions pour un simple point.

La recherche préparée est linéaire dans la longueur du texte, ce qui est optimal
au pire pour lire cette entrée. Cela **ne signifie pas** que la chaîne complète
est linéaire dans la taille de la regex : une déterminisation peut nécessiter un
nombre exponentiel d'états. Cette solution privilégie les recherches répétées
avec un motif fixe. Une simulation par ensembles actifs éviterait la construction
complète, avec un coût par caractère plus élevé ; ce serait une autre stratégie
à comparer dans le benchmark. Pour un motif purement littéral, KMP reste adapté.

Le DFA est partiel et n'est pas minimisé. La classe `DFAM` reste à implémenter.
`NativeSearch` accepte un format canonique : sans ε, au plus un arc par lettre
et un arc ANY par source, avec des étiquettes disjointes. Les doublons sont refusés,
même lorsqu'ils mènent à la même destination.

Les moteurs du projet travaillent sur les unités UTF-16 de Java. Le point
accepte aussi les sauts de ligne, sauf exclusion explicite. Pour reproduire une
recherche par lignes comme egrep, la lecture/découpe du fichier reste à intégrer.
Cette analyse ne prétend pas couvrir toute la norme ERE ni un moteur Unicode
fondé sur les points de code.

## Validation

- `DFATest` : mots simples, mot vide, cycles ε, arcs parallèles, états inaccessibles,
  exclusions et stabilité du langage après une seconde conversion. Un scénario
  examine les 65 536 valeurs char pour vérifier l'absence de chevauchement.
- `DFAPropertyTest` : 300 graphes générés comparés à un simulateur NFA indépendant,
  et 250 arbres regex comparés à `Pattern.matches` avec `DOTALL`.
- `NativeSearchTest` : exemples, chevauchements, erreurs, copie du motif,
  appels concurrents, 3 276 petits textes/motifs exhaustifs et texte long.
- `NativeSearchPropertyTest` : 400 motifs littéraux, 300 regex et 150 graphes
  générés. Oracles : `String.contains`, KMP, `Pattern.find` et recherche exhaustive
  de sous-chaînes par un simulateur NFA.
- `AutomatonBuilder` construit les graphes de test directement ; `SearchGenerators`
  définit les bornes, alphabets et oracles. Les tests aléatoires utilisent des
  graines fixes affichées par JUnit, sans réduction automatique des contre-exemples.

Les comparaisons à `Pattern` sont limitées au sous-ensemble syntaxique commun,
avec textes BMP et option `DOTALL`. Les caractères supplémentaires et substituts
isolés sont testés séparément contre le contrat littéral UTF-16, pour ne pas
confondre deux sémantiques Unicode différentes.

```bash
mvn test
mvn -Dtest=DFATest,DFAPropertyTest,NativeSearchTest,NativeSearchPropertyTest test
```

Les tests de grands textes vérifient la correction sans imposer de seuil de durée
fragile. Ils ne remplacent pas des mesures de performance dans le benchmark.
