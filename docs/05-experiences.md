[← Validation](04-validation.md) · [Accueil](../README.md) · [Perspectives →](06-perspectives.md)

# 05 — Protocole expérimental reproductible

Le protocole de référence est défini par [`scripts/report-profile.json`](../scripts/report-profile.json) (`readme-v2`). Une campagne propre exécute toute la chaîne, reconstruit les six figures utilisées par les Markdown et publie ensemble les SVG, [`benchmark.md`](assets/benchmark.md) et [`benchmark.json`](assets/benchmark.json). **DFAM reste une identité : la refonte expérimentale ne modifie pas la minimisation.**

`benchmark.json` reste l'autorité pour savoir avec quel commit, quel profil et quelles versions les assets actuellement versionnés ont été produits. Modifier le protocole ne transforme pas rétroactivement une ancienne campagne en nouvelle mesure : il faut relancer `report-campaign.sh` sur un arbre Git propre.

## 1. Une campagne est une transaction

Le système sépare trois catégories d'artefacts :

| Catégorie | Emplacement | Durée de vie |
| :--- | :--- | :--- |
| Travail temporaire | `target/report-work/` | supprimé après succès |
| Données auditables | `target/report/results/` | conservées localement, sauf `--purge` |
| Assets publiés | `docs/assets/` | versionnés et remplacés atomiquement après validation |

L'ordre des étapes est fixé :

1. **Préflight** : JDK 21, Maven, Python, GNU grep, locale UTF-8 et verrou exclusif. Une campagne de référence refuse un arbre Git sale.
2. **Validation du code** : `mvn clean test`, puis tests Python du protocole.
3. **Matérialisation des entrées** : normalisation du livre en LF, corpus ×8/×32 et corpus synthétique, avec SHA-256.
4. **Oracle fonctionnel** : pour chaque cas, `Main --print` et `grep -E -n` doivent produire exactement les mêmes octets.
5. **Profil structurel hors mesure** : nombre d'états/arcs du NFA de Thompson et du DFA spécialisé pour la recherche.
6. **Mesures CLI** : processus complets Java/grep, ordres équilibrés et graine fixe.
7. **Mesures JVM** : plusieurs JVM indépendantes, chacune avec prépassages puis mesures ; les statistiques utilisent les moyennes de forks comme unités indépendantes.
8. **Intégrité** : recalcul des résumés, contrôle des observations, hashes des sources/classes/corpus.
9. **Rendu** : six SVG + PNG de travail sont construits depuis les seules observations validées.
10. **Publication** : remplacement transactionnel de `docs/assets/`; en cas d'erreur, la référence précédente est restaurée.
11. **Nettoyage** : suppression des corpus et figures de travail. Les PNG ne sont pas publiés en double.

Avec `--allow-dirty`, les étapes de mesure sont disponibles pour un essai de développement mais `docs/assets/` n'est jamais modifié. Ainsi une mesure non rattachable à un commit ne peut pas devenir accidentellement la référence du rapport.

## 2. Des regex qui exercent réellement les automates

Les motifs littéraux `Elizabeth` et `ababababac` sont conservés comme **témoins** pour comparer KMP au chemin automate. Ils ne servent plus à démontrer le coût de la déterminisation.

Les expériences automate utilisent notamment :

| Famille | Exemple / construction | But |
| :--- | :--- | :--- |
| Alternatives larges | `(Elizabeth|Darcy|Bennet|Bingley|Collins|Wickham)` | augmenter les branches NFA |
| Alternatives + wildcard | `(Elizabeth|Darcy|Bennet|Bingley).*(said|replied|answered|cried)` | combiner branchement, boucle et classe `ANY` |
| Complexe sur le livre | `((Elizabeth|Darcy|Bennet|Bingley).*(said|replied|answered|cried))|((Mr|Mrs)\..*(Bennet|Darcy|Bingley))` | solliciter tout le pipeline automate sur un corpus réel |
| Complexe absent | `(ZXQ|QXZ).*(NEVER|PRESENT)` | parcourir le texte sans arrêt précoce sur une occurrence |
| Croissance contrôlée | `(a|b)*a` + `d` blocs `(a|b)` + `b` | faire croître le nombre de sous-ensembles accessibles |

Pour la famille de croissance, le profil utilise `d = 5, 7, 9`. Sur l'implémentation actuelle, le DFA de recherche possède approximativement **66, 258 puis 1 026 états**. Cette série teste donc la complexité de l'automate lui-même, contrairement à une longue concaténation littérale qui augmente surtout la longueur du motif.

La variante `d = 9` n'est pas utilisée pour le benchmark CLI complet : elle est réservée au profil structurel et aux JVM échauffées afin de ne pas multiplier inutilement les démarrages de processus sur le cas exponentiel. C'est un choix d'efficacité du protocole, pas un retrait d'observation gênante.

## 3. Séparer structure, préparation et parcours

Le fichier `automata.csv` contient, pour chaque regex :

- longueur développée du motif ;
- nombre d'états et de transitions du NFA ;
- nombre d'états et de transitions du DFA de recherche.

Ces valeurs sont collectées **une seule fois hors chronométrage**. Elles permettent d'interpréter le temps de déterminisation sans ajouter le coût du comptage structurel aux mesures.

Les mesures JVM distinguent ensuite : parsing, NFA, DFA, appel de `DFAM`, indexation du moteur, préparation totale, parcours du fichier et total. Comme `DFAM.minimize()` reste actuellement une identité, la campagne l'indique explicitement et n'attribue aucun gain à une minimisation inexistante.

Le graphe `compilation.svg` met désormais en regard la profondeur de la famille contrôlée, le nombre d'états réellement construit et le temps de déterminisation/préparation. Le graphe `scaling.svg` garde **la même regex complexe et le même automate** sur le livre ×1, ×8 et ×32 : la variable expérimentale devient alors uniquement le volume de texte.

## 4. Publication, nettoyage et `--purge`

Commande normale :

```bash
./scripts/report-campaign.sh
```

Après succès, `docs/assets/` contient exactement la référence que les Markdown affichent. Les observations brutes restent dans `target/report/results/` afin de pouvoir recalculer les statistiques ou retracer les figures.

Commande avec purge :

```bash
./scripts/report-campaign.sh --purge
```

La purge intervient **après** la validation et la publication. Elle supprime les fichiers générés `*.csv` et `*.txt`, notamment les observations et le journal de tests. Elle conserve `campaign.json`, `report.md`, `purge.json` ainsi que les assets publiés. Une campagne purgée ne peut plus être retracée à partir des données brutes : il faut la relancer, ce qui est précisément le sens de cette option.

Le nettoyage complet des résultats locaux reste distinct :

```bash
./scripts/clean-report.sh
```

Il efface `target/report/`, `target/report-work/`, `target/report-corpora/` et `target/benchmarks/` sans toucher à `docs/assets/`.

## 5. Limites d'interprétation

Les intervalles affichés décrivent la dispersion des observations, pas un intervalle de confiance universel. Le cache du système de fichiers, l'ordonnancement du système, la température et la charge de fond ne sont pas contrôlés. L'échauffement fixe ne prouve pas la convergence complète du JIT.

Le protocole améliore trois points importants : il rattache normalement les mesures à un **commit Git propre**, il valide les sorties avant tout chronométrage, et il mesure explicitement la **taille de l'automate** au lieu d'utiliser la longueur de la regex comme substitut. Il ne transforme cependant pas quelques cas bornés en preuve asymptotique générale ; la borne exponentielle de la déterminisation reste une propriété algorithmique à discuter séparément.
