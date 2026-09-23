# Optimisations hors minimisation — 23 septembre 2026

`DFAM.java` est conservé à l’identique du commit `243e8b6`. Aucune minimisation n’est implémentée par cette modification. Le contrat de recherche reste la présence d’une sous-chaîne dans chaque ligne, en unités UTF-16.

| Étape | Modification | Effet |
| :--- | :--- | :--- |
| Analyse syntaxique | Deux piles, priorités explicites, sans réduction répétée de listes | Temps et mémoire O(m), sans récursion |
| NFA | Parcours postordre itératif, fragments dans un graphe commun | Construction O(m), sans copie de sous-graphes |
| DFA | Bitsets, arcs séparés par type, alphabet local à chaque sous-ensemble | Moins de calculs et d’arcs inutiles ; pire cas exponentiel conservé |
| Préparation de recherche | NFA → DFA de recherche, arrêt de construction aux états acceptants | Une seule déterminisation dans le pipeline |
| Motif nullable | Calcul sur l’arbre, résultat vrai pour chaque ligne | Aucun automate construit dans le pipeline |
| Exécution DFA | États entiers, classification des caractères et pages de transitions | Accès directs O(1) par char, pas de matrice dense systématique |
| KMP | Parcours simplifié, rejet des textes trop courts, curseur réutilisable | Temps O(m+n) conservé |
| Comptage | Lecture par blocs, curseur réinitialisé aux séparateurs | Mémoire O(B) hors motif préparé, indépendante de la longueur des lignes |
| Diagnostic | Index des arcs sortants, accumulateur unique pour l’arbre | Coût proportionnel au graphe/arbre et au texte produit |
| Campagne | Normalisation, réplication et SHA-256 par blocs | Le corpus source entier ne reste plus en mémoire |

Le mode `--print` conserve une ligne entière pour en restituer le début. Le mode `--count` utilise les blocs. Les LF, CRLF, CR, dernières lignes sans séparateur et erreurs UTF-8 restent pris en charge. Le comptage continue de décoder le fichier après une correspondance.

## Vérifications

- `mvn --offline --batch-mode --no-transfer-progress -Dstyle.color=never package` : **3 673 tests Java**, aucune erreur ; JAR produit.
- `python3 -m unittest discover -s scripts/tests -v` : **21 tests Python**, aucune erreur.
- Les tests génératifs comparent le nouveau chemin NFA direct, l’ancien contrat DFA et les curseurs à `Pattern.find` ou à un simulateur NFA indépendant.
- Les sorties complètes avec numéros de lignes sont aussi comparées à l’oracle, au-delà du seul comptage.
- Régressions sur 20 000 lettres concaténées, 20 000 groupes ou étoiles imbriqués, un alphabet de 1 536 caractères, les frontières de pages et les coupures CRLF/UTF-16.

Avec `-Xmx32m`, un fichier composé d’une ligne de **48 Mio** suivie de `ab`, puis d’une ligne sans correspondance, donne le résultat **1** avec KMP et AUTOMATON. La version précédente échoue avec `OutOfMemoryError` dans les deux stratégies. Cette limite concerne le tas Java, pas toute la mémoire du processus.

## Mesures du code optimisé

La [campagne fixe](05-experiences.md) mesure désormais ces sources. Les [figures et statistiques publiées](assets/benchmark.md) remplacent les anciennes images ; leurs empreintes permettent d'identifier le code exécuté même avant un commit. Les anciens essais exploratoires avant/après et leurs dossiers datés ne sont plus publiés dans Git.

La nouvelle campagne sépare le temps des commandes complètes de la préparation et du parcours dans plusieurs JVM. Les chaînes regex et leurs tailles sont explicites ; les longues concaténations mesurent le coût de préparation. Les observations ne doivent pas être assimilées à une preuve des bornes de complexité décrites ci-dessus.

## Limites restantes

Un DFA peut toujours avoir une taille exponentielle. La construction à la demande, un budget d'états et un repli sur une simulation NFA restent des évolutions possibles. La minimisation est laissée intacte. Un affichage d'arbre très profond produit lui-même beaucoup de texte ; un accumulateur linéaire dans la sortie ne peut pas supprimer ce volume.

[← Perspectives](06-perspectives.md) · [Accueil](../README.md)
