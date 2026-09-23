# Référence gelée du README

Exécution UTC : 2026-09-23T05:54:01.639489+00:00

Protocole : `readme-v1` ; empreinte `92bbe816ec17aae1856ef599f6f8906cec27dcac90f3ca4e8079c53ab34d7eb7`.

Les données concernent les sources optimisées identifiées dans [benchmark.json](benchmark.json).
DFAM reste inchangé et ne minimise pas les automates.

## Commandes complètes

Médiane et intervalle interquartile de 30 processus par moteur. Démarrage JVM inclus ; aucun point retiré.

| Cas et regex exécutée | Corpus | Lignes | Java, médiane [Q1 ; Q3] ms | GNU grep, médiane [Q1 ; Q3] ms |
| :--- | :--- | ---: | ---: | ---: |
| Littéral · KMP<br>`Elizabeth` | book-1 | 644 | 47.01 [46.42 ; 49.08] | 5.07 [4.77 ; 5.28] |
| Littéral · automate<br>`Elizabeth` | book-1 | 644 | 58.61 [58.21 ; 60.42] | 5.11 [4.77 ; 5.23] |
| Alternative<br>`Elizabeth\|Darcy` | book-1 | 1050 | 59.82 [59.44 ; 61.32] | 5.59 [5.46 ; 5.64] |
| Point + étoile<br>`Eli.*beth` | book-1 | 644 | 61.10 [59.60 ; 64.05] | 5.49 [5.12 ; 5.67] |
| Littéral absent<br>`ZZZ_NOT_PRESENT_2026` | book-1 | 0 | 50.32 [46.33 ; 52.24] | 4.83 [4.61 ; 5.14] |
| Mot vide accepté<br>`a*` | book-1 | 14915 | 42.11 [40.40 ; 47.25] | 5.10 [4.85 ; 5.59] |
| Groupes + alternatives + étoiles<br>`((Elizabeth\|Darcy).*(said\|replied))\|(Mr\..*Bennet)` | book-1 | 127 | 68.19 [67.54 ; 70.10] | 5.49 [5.24 ; 5.60] |
| KMP ×8<br>`Elizabeth` | book-8 | 5152 | 73.52 [71.61 ; 77.33] | 8.65 [8.40 ; 9.05] |
| Automate ×8<br>`Elizabeth` | book-8 | 5152 | 95.93 [94.50 ; 103.67] | 8.59 [8.48 ; 9.17] |
| KMP ×32<br>`Elizabeth` | book-32 | 20608 | 149.82 [148.53 ; 154.58] | 20.13 [19.85 ; 20.82] |
| Automate ×32<br>`Elizabeth` | book-32 | 20608 | 210.56 [207.90 ; 212.55] | 20.13 [19.88 ; 20.46] |
| Préfixes répétés · KMP<br>`ababababac` | synthetic | 2048 | 49.35 [49.11 ; 50.54] | 4.83 [4.59 ; 4.98] |
| Préfixes répétés · automate<br>`ababababac` | synthetic | 2048 | 64.19 [63.62 ; 68.97] | 4.98 [4.86 ; 5.33] |
| Alternatives imbriquées<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | synthetic | 2048 | 69.09 [68.27 ; 72.77] | 6.64 [6.53 ; 6.92] |

## JVM échauffées

5 JVM distinctes par cas ; 10 prépassages puis 10 mesures par JVM. Chaque mesure reconstruit le motif et lit le fichier.
Les statistiques sont calculées sur les **5 moyennes de forks**, pas sur 50 répétitions prétendument indépendantes.
Le parcours inclut le décodage et les IO ; il ne mesure pas seulement les transitions en mémoire.

| Cas et regex exécutée | Préparation, médiane des forks ms | Parcours, médiane des forks ms | Total, médiane des forks ms |
| :--- | ---: | ---: | ---: |
| Littéral · KMP<br>`Elizabeth` | 0.019 | 2.934 | 2.952 |
| Littéral · automate<br>`Elizabeth` | 0.317 | 3.268 | 3.584 |
| Alternative<br>`Elizabeth\|Darcy` | 0.389 | 3.560 | 3.942 |
| Point + étoile<br>`Eli.*beth` | 0.379 | 4.721 | 5.101 |
| Littéral absent<br>`ZZZ_NOT_PRESENT_2026` | 0.028 | 2.864 | 2.892 |
| Mot vide accepté<br>`a*` | 0.007 | 1.276 | 1.283 |
| Groupes + alternatives + étoiles<br>`((Elizabeth\|Darcy).*(said\|replied))\|(Mr\..*Bennet)` | 1.459 | 5.369 | 6.828 |
| KMP ×8<br>`Elizabeth` | 0.021 | 21.773 | 21.793 |
| Automate ×8<br>`Elizabeth` | 0.356 | 24.777 | 25.133 |
| KMP ×32<br>`Elizabeth` | 0.036 | 88.463 | 88.496 |
| Automate ×32<br>`Elizabeth` | 0.426 | 99.725 | 100.164 |
| Préfixes répétés · KMP<br>`ababababac` | 0.019 | 4.249 | 4.271 |
| Préfixes répétés · automate<br>`ababababac` | 0.345 | 8.747 | 9.093 |
| Alternatives imbriquées<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 0.560 | 8.827 | 9.366 |
| Motif 65 caractères<br>64 lettres a suivies de b | 0.994 | 8.937 | 9.901 |
| Motif 257 caractères<br>256 lettres a suivies de b | 4.554 | 8.670 | 13.208 |
| Motif 1 025 caractères<br>1024 lettres a suivies de b | 43.013 | 8.612 | 51.625 |

## Dispersion et limites

Signalement fixé avant mesure : IQR/médiane > 15%. Ces observations sont conservées.
Séries signalées : nullable/java, literal-auto/search_preparation_ns, alternative/parsing_ns, wildcard/parsing_ns, nullable/parsing_ns, complex/nfa_ns, scale-auto-8/parsing_ns, scale-auto-8/search_preparation_ns, scale-auto-8/preparation_ns, scale-automaton-8/parsing_ns, scale-automaton-8/nfa_ns, scale-automaton-8/preparation_ns, scale-automaton-32/parsing_ns, overlap-kmp/parsing_ns, overlap-kmp/search_preparation_ns, overlap-kmp/preparation_ns, overlap-automaton/parsing_ns, branching/nfa_ns.

Les intervalles représentés sont des dispersions, pas des intervalles de confiance. Aucun classement universel n'est déduit.
Cache de fichiers sollicité, machine non isolée, charge de fond et température non contrôlées. L'échauffement fixe ne prouve pas une convergence du JIT.
Les cas complexes sont bornés ; cette campagne ne supprime ni ne couvre tous les cas d'explosion exponentielle.

Données brutes locales (non versionnées) : `target/report/results/cli.csv`, `jvm.csv` et leurs résumés.
Les prépassages, les ordres d'exécution, les résultats exacts et les empreintes sont conservés.

## Regex exactes

Ces chaînes sont celles transmises aux moteurs ; aucune syntaxe de quantification n'est ajoutée.

<details><summary>literal-auto — 9 caractères — book-1</summary>

```text
Elizabeth
```

</details>

<details><summary>literal-automaton — 9 caractères — book-1</summary>

```text
Elizabeth
```

</details>

<details><summary>alternative — 15 caractères — book-1</summary>

```text
Elizabeth|Darcy
```

</details>

<details><summary>wildcard — 9 caractères — book-1</summary>

```text
Eli.*beth
```

</details>

<details><summary>absent — 20 caractères — book-1</summary>

```text
ZZZ_NOT_PRESENT_2026
```

</details>

<details><summary>nullable — 2 caractères — book-1</summary>

```text
a*
```

</details>

<details><summary>complex — 50 caractères — book-1</summary>

```text
((Elizabeth|Darcy).*(said|replied))|(Mr\..*Bennet)
```

</details>

<details><summary>scale-auto-8 — 9 caractères — book-8</summary>

```text
Elizabeth
```

</details>

<details><summary>scale-automaton-8 — 9 caractères — book-8</summary>

```text
Elizabeth
```

</details>

<details><summary>scale-auto-32 — 9 caractères — book-32</summary>

```text
Elizabeth
```

</details>

<details><summary>scale-automaton-32 — 9 caractères — book-32</summary>

```text
Elizabeth
```

</details>

<details><summary>overlap-kmp — 10 caractères — synthetic</summary>

```text
ababababac
```

</details>

<details><summary>overlap-automaton — 10 caractères — synthetic</summary>

```text
ababababac
```

</details>

<details><summary>branching — 33 caractères — synthetic</summary>

```text
(a|b)*a(a|b)(a|b)(a|b)(a|b)(a|b)b
```

</details>

<details><summary>compile-64 — 65 caractères — synthetic</summary>

```text
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab
```

</details>

<details><summary>compile-256 — 257 caractères — synthetic</summary>

```text
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab
```

</details>

<details><summary>compile-1024 — 1025 caractères — synthetic</summary>

```text
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab
```

</details>
