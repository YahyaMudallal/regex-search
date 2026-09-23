[← Algorithmes](03-algorithmes.md) · [Accueil](../README.md) · [Expériences →](05-experiences.md)

# 04 — Vérifier avant de chronométrer

Un temps d’exécution n’a d’intérêt que si le résultat correspond au problème posé. Dans ce projet, une erreur discrète peut se cacher derrière des exemples apparemment convaincants : une recherche qui oublie les occurrences chevauchantes trouvera souvent `monsieur` dans une phrase simple, mais échouera déjà sur `ab` dans `aab`.

La validation combine donc des exemples dont on connaît la réponse, des cas construits pour provoquer un problème précis et des propriétés vérifiées sur des données générées.

## 1. Trois niveaux de tests

### Exemples simples

Le premier niveau fixe le contrat : présence au début, au milieu et à la fin ; absence ; motif plus long que le texte ; casse ; texte vide ; motif littéral vide. L’exemple `bonjour monsieur bienvenue` avec le motif `monsieur` doit donner `true`.

Ces exemples restent utiles même avec une grande suite générative. Ils expriment directement le comportement attendu et rendent une régression facile à comprendre.

### Cas ciblés

Les motifs répétitifs comme `ababac` et `aaaab` sollicitent les replis de KMP. Les graphes avec cycles ε vérifient que les fermetures terminent. Les points universels mêlés à des lettres explicites vérifient que la déterminisation réunit toutes les destinations possibles.

Les tests de `NativeSearch` examinent aussi le contrat d’entrée : un NFA avec ε, deux arcs concurrents sur la même lettre ou un arc universel chevauchant un littéral ne doivent pas être acceptés silencieusement comme un DFA valide.

Pour les fichiers, les cas ciblés portent sur les fins de ligne, l’encodage, les longues lignes et les erreurs d’accès. Une occurrence ne doit jamais être composée de la fin d’une ligne et du début de la suivante.

### Propriétés générées

Une propriété décrit un comportement qui doit rester vrai pour beaucoup d’entrées : insérer un motif dans un texte doit le rendre présent ; déterminer un NFA doit préserver son langage ; ajouter du contexte autour d’une occurrence ne doit pas la supprimer.

Les générateurs limitent les longueurs, les alphabets et la profondeur des arbres. Les petits alphabets produisent davantage de répétitions et donc davantage de cas de chevauchement. Les graphes arbitraires couvrent aussi des formes qui ne proviennent pas directement du constructeur NFA.

## 2. Des oracles adaptés à chaque contrat

| Objet testé | Résultat de référence | Précaution |
| :--- | :--- | :--- |
| KMP | `String.contains` et propriétés d’insertion/extraction | Comparaison littérale en unités UTF-16 |
| DFA | Simulation explicite du NFA avant conversion | Tester la reconnaissance d’un mot entier |
| NFA et DFA issus d’un arbre | `Pattern.matcher(...).matches()` | Syntaxe commune, textes BMP et `DOTALL` |
| NativeSearch | `Pattern.matcher(...).find()` | Chercher une occurrence, pas seulement reconnaître toute la chaîne |
| NativeSearch sur petits graphes | Énumération des sous-chaînes puis simulation NFA | Oracle volontairement lent, réservé aux petites entrées |
| Benchmark fichier | Nombre de lignes acceptées par l’oracle | Inclure lignes vides et séparateurs |
| Commandes Java / grep | Comptage obtenu par GNU grep | Même copie du texte, même motif et locale compatible |

`Pattern.DOTALL` est utilisé dans les tests en mémoire parce que notre transition universelle accepte aussi un `char` de saut de ligne. Dans le parcours des fichiers, les séparateurs sont retirés avant la recherche. Les tests contre `Pattern` se limitent au BMP lorsque le point intervient, afin de ne pas confondre unités UTF-16 et points de code supplémentaires.

Le moteur Java de référence est uniquement un **oracle de test**. Il ne remplace aucun des algorithmes de production.

## 3. Ce qui est réellement généré

Les [générateurs communs](../src/test/java/com/sorbonne/support/SearchGenerators.java) construisent des textes bornés, des expressions et des graphes. L’[AutomatonBuilder](../src/test/java/com/sorbonne/support/AutomatonBuilder.java) rend lisibles les graphes de test en nommant les états et les transitions.

| Famille | Cas dynamiques | Travail effectué à l’intérieur d’un cas |
| :--- | ---: | :--- |
| KMP | 2 000 | Accord avec `contains`, insertion, extraction, symbole absent et ajout de contexte |
| DFA, NFA arbitraires | 300 | 20 mots par graphe, comparés avant/après déterminisation |
| DFA, expressions générées | 250 | 20 mots par expression, comparés au moteur Java |
| NativeSearch, littéraux | 400 | Recherche, insertion, extraction et absence garantie |
| NativeSearch, expressions | 300 | 16 textes par préparation |
| NativeSearch, graphes arbitraires | 150 | 12 textes par graphe avec oracle exhaustif de sous-chaînes |
| Benchmark sur fichiers | 120 | Un fichier généré, comparaison de `AUTO`, `DFA`, `DFAM` et `AUTOMATON` à l’oracle |

KMP possède également un test exhaustif sur **945 couples** de petits mots binaires : les textes de longueur 0 à 5 sont croisés avec les motifs de longueur 0 à 3. Ce test vaut une seule entrée dans le compteur JUnit, même s’il contient de nombreuses assertions.

Les chiffres ci-dessus ne doivent donc pas être additionnés comme s’ils décrivaient tous le même niveau de travail. Un cas JUnit peut vérifier un seul exemple ou plusieurs centaines de combinaisons.

### Ressemblance et différence avec Hypothesis

L’idée est la même que dans une approche par propriétés : définir un domaine d’entrées puis vérifier une règle sur des exemples variés. Ici, les générateurs sont écrits directement avec `Random` et les fabriques dynamiques de JUnit.

Chaque cas utilise une graine fixe, incluse dans son nom. Un échec peut être reproduit sans dépendre de l’ordre d’exécution. En revanche, il n’y a **pas de réduction automatique** vers un contre-exemple minimal, contrairement à des outils comme Hypothesis. Une entrée fautive doit être simplifiée manuellement avant d’être ajoutée comme cas de régression explicite.

## 4. Le parcours de fichier est testé comme tel

Les tests de `Benchmark` écrivent de vrais fichiers dans des répertoires temporaires JUnit. Ils couvrent notamment :

- une ligne avec plusieurs occurrences, qui ne doit compter qu’une fois ;
- `a` puis `b` sur deux lignes, qui ne doivent pas produire une occurrence de `ab` ;
- un fichier vide et un fichier contenant une ligne vide ;
- les séparateurs LF, CRLF et CR, ainsi qu’une dernière ligne sans séparateur ;
- une ligne dépassant le tampon de 64 K caractères, avec un motif multioctet près de sa limite ;
- un fichier absent, un répertoire donné à la place d’un fichier et des octets UTF-8 invalides ;
- le fast path `byte[]` du comptage, y compris emoji/UTF-8 multioctet à cheval sur 64 Kio et CRLF aux frontières ;
- deux appels successifs au même benchmark, dont les compteurs doivent repartir de zéro.

Les durées sont vérifiées par leurs relations : valeurs non négatives, étapes non utilisées à zéro et total égal à préparation plus parcours. Aucun test ne suppose qu’une recherche doit finir en moins d’un nombre arbitraire de millisecondes. Les performances appartiennent au protocole expérimental, pas à une assertion sensible à la charge de la machine.

## 5. Les scripts font partie du résultat à valider

Le comparateur doit distinguer zéro correspondance d’une erreur de processus. Il doit également refuser une sortie qui ressemble à un message humain au lieu d’un entier, propager un délai dépassé et ne pas comparer deux interprétations différentes du même texte.

Les tests Python du comparateur vérifient le sous-ensemble de regex accepté, la normalisation des séparateurs sans modification de la source, le rejet des encodages ou caractères exclus, les statuts de grep, les sorties incohérentes et les délais dépassés.

Trois tests Java de `Main` protègent le mode `--count` : sortie numérique seule pour chaque stratégie, compte nul correct et rejet d’une commande incomplète. Des essais de bout en bout ont en outre couvert un lancement depuis un autre répertoire, un chemin contenant des espaces, des caractères accentués et une recherche sans résultat.

## 6. État vérifié du code optimisé

Les tests de `DFAMHopcroftTest` vérifient les exemples et propriétés de minimisation, dont la conservation du langage et du comportement de recherche. Les tests du benchmark couvrent les chemins DFA sans minimisation, DFAM et le raccourci nullable. Les tests Python refusent aussi de comparer des moteurs sur des entrées différentes.

La campagne fixe exécute les tests avant toute mesure : **4 140 tests Java et 24 tests Python**. Le journal est conservé localement dans `target/report/results/validation.txt`. Les tests Python supplémentaires protègent l'équilibrage des moteurs, le calcul par fork, les contrôles d'intégrité, le refus des observations dupliquées, la restauration après une publication interrompue et la conservation des assets lors du nettoyage.

La version optimisée ajoute les longues expressions sans récursion, la comparaison du chemin NFA direct, les curseurs entre blocs, les numéros et contenus exacts des lignes et les corpus normalisés par blocs. Voir le [relevé des optimisations](07-optimisations.md).

Ces chiffres ne constituent ni un pourcentage de couverture ni une preuve formelle. `AppTest`, fourni à l'initialisation, vérifie seulement une assertion vraie ; il est compté mais ne valide aucun comportement algorithmique.

## 7. Les limites de cette validation

Les expressions et graphes générés sont petits pour que les oracles exhaustifs restent utilisables. Cette borne laisse de côté certaines explosions de taille. Des régressions distinctes couvrent maintenant les limites de pile sur les longues expressions.

Les oracles ne sont pas tous entièrement indépendants : le simulateur NFA partage le modèle de transitions avec le code de production. La comparaison à `Pattern` et les exemples explicites réduisent ce risque de défaut commun, sans le supprimer mathématiquement.

L’égalité des comptes avec grep ne garantit pas, à elle seule, que les mêmes lignes ont été sélectionnées : deux ensembles différents peuvent avoir le même cardinal. Le mode retournant les numéros et contenus des lignes est maintenant comparé à un oracle sur les fichiers générés, hors chronométrage.

Les tests de `DFAM` et de son implémentation Hopcroft couvrent maintenant la conservation du langage avant/après minimisation, la fusion d'états indiscernables, la suppression des états inaccessibles, les transitions partielles via un puits implicite, les classes `ANY`, l'idempotence du nombre d'états et le comportement du DFA spécialisé de recherche. Deux property tests génèrent des centaines de regex et des milliers de mots/textes reproductibles.

[← Algorithmes](03-algorithmes.md) · [Accueil](../README.md) · [Expériences →](05-experiences.md)
