# Optimisations du chemin chaud — 23 septembre 2026

La minimisation est maintenant accessible par `DFAM.minimize`, qui délègue à l'implémentation de Hopcroft. Les mesures ont surtout montré un autre fait : **réduire le nombre d'états ne réduit pas automatiquement le nombre de transitions exécutées pendant le scan**. DFA et DFAM consomment toujours un symbole après l'autre ; si une transition est déjà un accès O(1), Hopcroft améliore principalement la taille de l'index et son comportement cache, pas la complexité asymptotique du parcours.

L'optimisation a donc été déplacée vers les opérations exécutées pour chaque caractère du fichier.

| Étape | Avant | Maintenant | But |
| :--- | :--- | :--- | :--- |
| Index DFA | classes paginées + pages de transitions | table plate `state × classe` lorsque sa taille reste bornée | supprimer les indirections du chemin chaud |
| ASCII | classification UTF-16 puis transition | table directe `state × 128` | une lecture de table par octet ASCII |
| Gros DFA | même structure paginée | repli paginé conservé au-delà d'un budget mémoire | ne pas échanger la vitesse contre un risque mémoire non borné |
| Lecture `--count` | `InputStreamReader` UTF-8 → `char[]` | scan UTF-8 strict directement sur `byte[]` | éviter le décodage/copie des longues plages ASCII |
| UTF-8 non ASCII | décodeur standard | décodeur strict intégré, émission des mêmes unités UTF-16 | garder exactement la sémantique du moteur |
| KMP en fichier | appel `accept(char)` répété | traitement par plage ASCII avec copie `byte[]` du motif ASCII | amortir les appels et les conversions du curseur |
| États acceptants | tableau `finals[]` interrogé après transition | sentinelle interne `MATCH` | supprimer un accès mémoire par symbole |

## Pourquoi DFAM n'était pas plus rapide

Pour un texte de longueur $n$, DFA comme DFAM exécutent au plus une transition par unité consommée :

$$
T_{scan}=\Theta(n).
$$

Passer, par exemple, de 159 à 101 états peut réduire la mémoire de la table, mais ne transforme pas $n$ transitions en 101 transitions. Pour une invocation unique, DFAM paie en plus la minimisation :

$$
T_{DFAM}=T_{DFA}+T_{Hopcroft}+T_{scan,min}.
$$

Il ne gagne donc au total que si l'amélioration du scan amortit `T_Hopcroft`, ce qui dépend de la réduction structurelle, du volume de texte et du nombre de fichiers parcourus avec le même motif. C'est une conclusion expérimentale importante : **minimal en nombre d'états ne signifie pas minimal en temps CPU**.

## Fast path UTF-8/ASCII

Le sous-ensemble du projet utilise des motifs ASCII, tandis que les corpus Gutenberg restent UTF-8. Dans le chemin `--count`, `FileLoader` lit maintenant des blocs d'octets. Une plage dont tous les octets sont ASCII est envoyée directement au curseur via `acceptAscii`; aucun `String` ni `char[]` intermédiaire n'est créé. Lorsqu'un octet multioctet apparaît, le décodeur intégré vérifie les séquences surlongues, substituts UTF-16, code points hors plage et séquences tronquées, puis produit exactement une ou deux unités UTF-16 comme auparavant.

Les séparateurs LF, CR et CRLF gardent le même comportement, y compris à la frontière de deux blocs. Le fichier entier est encore validé après une correspondance : une séquence UTF-8 invalide en fin de fichier reste une erreur.

## Mesure diagnostique avant/après

La table suivante est un **diagnostic local**, pas le résultat de la campagne officielle. Même JDK 21, même machine, mêmes options JVM et même corpus répété ×32 ; le nombre indiqué est le temps moyen de `scanNanos` après échauffement dans une JVM. Il sert uniquement à vérifier que l'optimisation cible bien le goulot identifié.

| Cas ×32 | Ancien scan | Nouveau scan | Accélération approximative |
| :--- | ---: | ---: | ---: |
| `Elizabeth` · KMP | 126.5 ms | 39.6 ms | ×3.2 |
| `Elizabeth` · DFA | 181.1 ms | 75.8 ms | ×2.4 |
| `Elizabeth` · DFAM | 171.2 ms | 74.1 ms | ×2.3 |
| regex complexe · DFA | 165.4 ms | 72.5 ms | ×2.3 |
| regex complexe · DFAM | 184.2 ms | 76.8 ms | ×2.4 |

Ces nombres ne doivent pas remplacer `docs/assets/benchmark.json`. Après intégration du patch dans un commit propre, `./scripts/report-campaign.sh` doit être relancé : il recalculera les CSV, les statistiques, les figures et les hashes avec le code final.

## Vérifications effectuées sur ce changement

- compilation Java 21 avec `javac --release 21 -Xlint:all` et `ant clean jar` ;
- **24/24 tests Python** du protocole expérimental ;
- comparaison exacte `--print` contre `grep -E -n` sur les **13 cas book-1** du profil (KMP, DFA et DFAM) ;
- comparaison des comptes sur le corpus ×32 pour le littéral et la regex complexe ;
- contrôle spécifique du fast path UTF-8 : caractères BMP, emoji coupé à une frontière de bloc, CR/LF/CRLF et plusieurs formes d'UTF-8 invalide.

La suite complète JUnit doit néanmoins être relancée avec Maven dans l'environnement du dépôt après remplacement des fichiers ; Maven n'est pas disponible dans l'environnement utilisé pour cette optimisation.

## Limites restantes face à GNU grep

Cette optimisation réduit fortement le coût du moteur Java, mais elle ne supprime pas trois avantages structurels de GNU grep : coût de démarrage natif très faible, moteurs spécialisés pour les littéraux, et préfiltrage/stratégies hybrides avant le DFA général. Le projet reste volontairement centré sur KMP et les automates du cours. Ajouter Boyer–Moore/Aho–Corasick ou un filtre de littéraux obligatoires serait une **nouvelle stratégie algorithmique** qui devrait être documentée et mesurée séparément, pas dissimulée dans le chemin DFA.

[← Perspectives](06-perspectives.md) · [Accueil](../README.md)
