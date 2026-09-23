[← Perspectives](06-perspectives.md) · [Accueil](../README.md)

# 07 — Optimisations du chemin chaud

Le profilage a montré que la minimisation réduit parfois fortement la taille du graphe sans réduire le nombre de symboles à lire. Le travail de performance porte donc d’abord sur la **boucle exécutée pour chaque octet du fichier**. Le sujet imposant des motifs ASCII, le moteur de production utilise désormais un alphabet fixe de **256 valeurs** et lit les fichiers directement sous forme de `byte[]`.

## 1. Décisions appliquées

| Zone | Représentation précédente | Représentation actuelle | Effet recherché |
| :--- | :--- | :--- | :--- |
| Alphabet de scan | domaine plus large que nécessaire | `0..255` | modèle conforme au besoin et borné |
| Transition DFA | classification puis plusieurs indirections | `delta[(state << 8) | symbol]` | une adresse de table dans le cas courant |
| Lecture fichier | transformation avant le matching | blocs `byte[65536]` | supprimer les conversions du chemin chaud |
| KMP | comparaison caractère par caractère | motif `byte[]` + traitement de bloc | réduire les appels de curseur |
| `--print` | reconstruction textuelle de la ligne | conservation des octets de la ligne courante | restituer exactement le contenu lu |
| Très grand DFA | table directe potentiellement coûteuse | repli par classes d’équivalence | borner la mémoire |

Le moteur garde la même sémantique de recherche par ligne : LF, CR et CRLF sont des séparateurs ; toute autre valeur est un symbole ordinaire. Le point `.` consomme exactement un octet.

## 2. Table directe `état × 256`

Pour les automates usuels, `NativeSearch` prépare un tableau plat d’entiers. Avec `q` états, sa taille est :

\[
4 \times 256 \times q\;\text{octets}.
\]

Un DFA de 159 états demande ainsi environ 159 Kio ; un DFA de 1 026 états reste proche de 1 Mio. À cette échelle, privilégier une table contiguë est plus intéressant que multiplier les structures de pointeurs. La transition chaude devient :

```java
state = delta[(state << 8) | (buffer[i] & 0xff)];
```

Les états finaux sont encodés par un marqueur interne : dès qu’une occurrence est trouvée, le curseur cesse de faire travailler l’automate jusqu’à la fin de la ligne.

Au-delà de 16 millions de cellules, soit 64 Mio pour la table, `NativeSearch` bascule sur une table compacte par classes. Ce cas protège la mémoire sans pénaliser les DFA ordinaires du protocole.

## 3. Lecture binaire et traitement par blocs

`FileLoader` utilise un tampon de 64 Kio et transmet au moteur des plages contiguës d’octets. Le curseur DFA et le curseur KMP possèdent tous deux une méthode de traitement de bloc ; l’appel virtuel n’est donc pas répété pour chaque symbole.

Le mode `--count` ne conserve aucune ligne complète. Le mode `--print` alloue seulement la ligne en cours, car il doit pouvoir la restituer lorsqu’une correspondance est trouvée. Une occurrence peut traverser une frontière de tampon : l’état du DFA ou l’indice KMP est conservé entre deux blocs et n’est réinitialisé qu’à la frontière de ligne.

## 4. KMP reste le fast path littéral

Un motif littéral ASCII est converti une seule fois en `byte[]`, puis sa table LPS est construite en `O(m)`. Le scan du fichier est `O(n)` et n’effectue aucune conversion de symbole. Le chemin KMP reste utile comme témoin algorithmique et comme stratégie automatique pour une concaténation pure.

## 5. Pourquoi DFAM n’est pas automatiquement plus rapide

Hopcroft minimise le **nombre d’états**, pas le nombre d’octets à lire. Après préparation, DFA et DFAM exécutent tous deux une transition en temps constant pour chaque symbole tant qu’aucune occurrence n’a été trouvée. Réduire 159 états à 101 peut économiser de la mémoire sans changer sensiblement le temps du scan ; si un DFA passe de 1 026 à 1 025 états, le gain attendu pendant la lecture est presque nul alors que la minimisation a un coût de préparation mesurable.

La campagne distingue donc explicitement :

\[
T_{\mathrm{DFA}},\quad T_{\mathrm{Hopcroft}},\quad T_{\mathrm{index}},\quad T_{\mathrm{scan}}.
\]

C’est une conclusion expérimentale importante : **minimal en états ne signifie pas minimal en temps d’exécution**.

## 6. Protocole de comparaison

Le comparateur normalise uniquement les séparateurs CRLF/CR vers LF, puis fournit la **même copie d’octets** à Java et à GNU grep. Le moteur de référence est lancé avec `grep -a -E` et `LC_ALL=C`. Le motif doit appartenir au sous-ensemble ASCII du projet ; aucune hypothèse textuelle supplémentaire n’est imposée au fichier.

La validation précède toute mesure : les sorties `--print` et `grep -a -E -n` sont comparées octet par octet. Les tests couvrent notamment les 256 valeurs possibles, les frontières de tampon, NUL, CR/LF/CRLF et les occurrences coupées entre deux lectures.

## 7. Limite restante face à GNU grep

La réduction du coût par symbole rapproche le moteur d’une boucle DFA classique, mais GNU grep conserve des avantages d’implémentation et des stratégies spécialisées : coût de lancement natif faible, recherche littérale très optimisée et préfiltrage avant certains chemins généraux. Le projet reste volontairement centré sur les algorithmes étudiés — KMP, Thompson, déterminisation et Hopcroft — afin que les mesures restent interprétables.

[← Perspectives](06-perspectives.md) · [Accueil](../README.md)
