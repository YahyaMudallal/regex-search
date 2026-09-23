[← Expériences](05-experiences.md) · [Accueil](../README.md) · [Références →](references.md)

# 06 — Limites actuelles, prochaines étapes et rendu

Le projet permet déjà de suivre une expression jusqu’à une recherche dans un fichier, de vérifier des propriétés sur les transformations d’automates et de confronter les résultats à GNU grep. La campagne fixe confronte KMP et les automates au même motif littéral. Elle montre aussi que le temps complet d’une commande ne se résume pas au coût théorique de sa boucle de recherche.

La suite du travail doit maintenant fermer les écarts avec le sujet et approfondir les mesures. Ajouter un algorithme sans tests de préservation du langage, ou annoncer un gain sans refaire la campagne, laisserait le rapport et le code décrire deux versions différentes.

## 1. État des exigences

| Élément du sujet                          | État dans cette version                | Preuve ou limite                                     |
| :---------------------------------------- | :------------------------------------- | :--------------------------------------------------- |
| Sous-ensemble regex demandé               | Implémenté pour les opérateurs retenus | Parseur, tests de priorité et d’échappement          |
| Arbre → NFA avec ε                        | Implémenté                             | Construction structurelle et tests                   |
| NFA → DFA par sous-ensembles              | Implémenté                             | Tests de langage et de déterminisme                  |
| DFA équivalent minimal                    | **Implémenté**                          | Hopcroft, puits implicite et alphabet symbolique      |
| Recherche dans les lignes d’un fichier    | Implémentée sous forme de comptage     | Lecture bufferisée et tests sur fichiers             |
| Affichage des lignes comme egrep          | Implémenté                             | `search.sh`, lignes numérotées et validation exacte         |
| KMP expliqué et confronté aux automates   | Présent                                | Chapitres 3 et 5, même mot et même corpus            |
| Comparaison des performances à egrep      | Campagne reproductible avec assets publiés           | GNU grep sur macOS ; protocole de processus complets |
| Tests et rapport argumenté                | Présents, à maintenir                  | Exemples, génération, données brutes et figures      |
| Rapport final LaTeX synthétique | En cours de composition | 12 pages de contenu prévues ; couverture, sommaire et bibliographie séparés |

Le sujet illustre une commande contenant `+`, mais son périmètre obligatoire énumère un sous-ensemble plus restreint. Le rapport et les exemples du projet s’en tiennent aux opérateurs réellement implémentés ; il ne faut pas présenter l’exemple illustratif du sujet comme une expression déjà supportée telle quelle.

## 2. Minimisation implémentée ; limites restantes

La minimisation n'est plus une perspective : `DFAM` applique Hopcroft avec alphabet symbolique et état puits implicite. Elle produit un nouveau graphe, élimine les états inaccessibles et conserve le langage des DFA classiques comme le comportement d'arrêt précoce des DFA de recherche.

La limite principale se situe **avant** cette étape. La construction par sous-ensembles peut créer exponentiellement beaucoup d'états ; minimiser ensuite ne rembourse ni le temps ni la mémoire déjà dépensés pour les générer. Une perspective pertinente serait donc une déterminisation à la demande ou une simulation NFA lorsqu'un budget d'états est dépassé. Une autre piste serait de mesurer sur des familles spécialement construites pour produire beaucoup d'états équivalents, afin d'étudier quand le coût `O(k n log n)` de Hopcroft est amorti par la réduction du moteur indexé.

## 3. Maintenir le contrat de sortie de recherche

Le mode `scripts/search.sh` affiche désormais les lignes avec leur numéro, ce qui rapproche l’interface de l’usage attendu d’egrep. Il permet de vérifier que les **mêmes lignes**, et pas seulement le même nombre de lignes, sont sélectionnées.

Cette sortie reste séparée du protocole de comptage. Comparer un moteur qui imprime toutes les lignes à un autre qui n’imprime qu’un entier changerait le travail mesuré. La campagne fixe contrôle les lignes exactes avant le chronométrage, puis les deux commandes mesurées conservent le mode compteur.

## 4. Approfondir les coûts observés

La campagne mesure désormais une famille d’automates à profondeur 5, 7 et 9 et enregistre directement le nombre d’états/transitions du NFA et du DFA de recherche. Cela remplace la longueur d’un motif littéral par une variable structurelle réellement liée au coût de déterminisation.

Le parcours est aussi mesuré dans cinq JVM par cas, avec lecture et décodage inclus. Charger tout le corpus en mémoire répondrait à une autre question et modifierait le profil mémoire ; ce choix devrait apparaître dans le protocole.

Quelques familles de cas restent à étudier :

| Axe                                      | Pourquoi il est utile                                          |
| :--------------------------------------- | :------------------------------------------------------------- |
| Plusieurs livres et types de textes      | Ne pas généraliser à partir d’une seule distribution de lignes |
| Motifs plus longs et préfixes répétitifs | Étudier KMP et le coût du parseur au-delà des exemples courts  |
| Alternances et étoiles plus complexes    | Mesurer le nombre de sous-ensembles accessibles                |
| Longues lignes isolées                   | Mesurer la mémoire du comptage par blocs et de l’affichage par ligne |
| Différentes tailles de tampon            | Justifier empiriquement le compromis des 64 K caractères       |
| Plusieurs campagnes et machines          | Séparer une tendance robuste du bruit local                    |

Les optimisations envisageables doivent être reliées à un coût identifié. Le parseur itératif, les fragments NFA dans un graphe commun, les ensembles de bits, la préparation directe du DFA de recherche et le comptage par blocs sont maintenant implémentés. Une simulation NFA ou une déterminisation à la demande pourraient éviter de construire tout le DFA avant de parcourir un petit fichier. La simulation NFA et la déterminisation à la demande ne sont pas implémentées ; la référence expérimentale publiée mesure les optimisations actuelles.

<a id="rendu"></a>

## 5. Passer de cette documentation au rapport de rendu

Le [sujet fourni](../src/main/java/com/sorbonne/specifications/daar_projet1.pdf) recommande 5 à 10 pages et fixe une limite formelle de 12 pages. Le rendu LaTeX est donc conçu comme une synthèse autonome, tandis que les chapitres Markdown restent la documentation technique détaillée. Pour la version demandée par le binôme, les 12 pages de contenu sont composées séparément de la page de garde, du sommaire et de la bibliographie.

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

Les CSV complets restent locaux dans `target/report/results/` ; le profil, les statistiques publiées et les guides sont versionnés. Ils n’ont pas besoin d’occuper les pages du rapport principal. L’objectif est qu’un lecteur puisse vérifier les affirmations sans être obligé de parcourir des dizaines de tableaux.

### Préparer l’archive

Le sujet demande le code commenté, la documentation, un binaire, les instances de test et un moyen de construction, avec un volume de l’ordre d’une dizaine de mégaoctets. Le dépôt fournit Maven **et** `build.xml` pour Ant. La cible Java est revenue à **Java 21**, version LTS largement disponible et suffisante pour les sources actuelles.

Le packaging est automatisé :

```bash
./scripts/package.sh
```

Le script exige un arbre Git propre, relance les tests, construit le JAR, puis archive uniquement les chemins retournés par `git ls-files` et `regex-search.jar`. `.git`, `target`, `.cache`, `.venv*`, caches Python, fichiers IDE et artefacts de modernisation ne peuvent donc pas entrer dans le ZIP. L’archive est refusée si elle dépasse 10 Mio et contient un manifeste avec le commit et le SHA-256 du JAR.

## 6. Ce que cette version permet de conclure

La chaîne implémentée sait préparer un motif et rechercher ses occurrences sans conserver tout le fichier en mémoire. Les tests donnent des éléments solides sur la préservation du langage et le comportement des deux moteurs dans le domaine couvert. Les statistiques publiées permettent de comparer les moteurs sur chaque motif et chaque périmètre mesuré.

La minimisation est désormais intégrée au chemin `DFAM`. Les prochaines améliorations doivent donc viser les coûts réellement dominants — déterminisation, démarrage JVM et débit du scan — puis être évaluées en réexécutant exactement le même protocole, sans changer simultanément motifs, corpus et méthode de mesure.

[← Expériences](05-experiences.md) · [Accueil](../README.md) · [Références →](references.md)
