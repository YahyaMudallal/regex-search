# Référence expérimentale reproductible

Exécution UTC : 2026-09-23T14:31:51.097138+00:00

Protocole : `report-v4-dfa-dfam-kmp-grep` ; empreinte `f5006b14605ce7f0a9196a7a17db5f62f7644360e99278bce8d13961f33d790b`.

Les données concernent les sources optimisées identifiées dans [benchmark.json](benchmark.json).
DFAM applique la minimisation de Hopcroft au DFA de recherche avant indexation.

KMP est comparé uniquement sur les littéraux. DFA et DFAM partagent le même DFA de recherche initial et la même indexation ; seule la minimisation change.
Les motifs acceptant le mot vide utilisent le raccourci commun sans construire d'automate, même en DFA/DFAM.
Le profil structurel construit les graphes à titre diagnostique, même pour KMP ou un motif nullable ; ce n'est pas le travail effectué par ces chemins chronométrés.

## Commandes complètes

Médiane et intervalle interquartile de 30 processus par moteur. Démarrage JVM inclus ; aucun point retiré.

| Cas et regex exécutée | Corpus | Lignes | Java, médiane [Q1 ; Q3] ms | GNU grep, médiane [Q1 ; Q3] ms |
| :--- | :--- | ---: | ---: | ---: |
| Littéral · KMP<br>`Elizabeth` | book-1 | 644 | 56.11 [54.12 ; 58.78] | 5.91 [5.65 ; 6.05] |
| Littéral · DFA<br>`Elizabeth` | book-1 | 644 | 87.76 [86.39 ; 89.69] | 5.72 [5.52 ; 5.87] |
| Littéral · DFAM<br>`Elizabeth` | book-1 | 644 | 88.81 [84.45 ; 90.36] | 6.01 [5.65 ; 6.29] |
| Alternative large · DFA<br>`(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` | book-1 | 1987 | 91.17 [88.95 ; 93.31] | 5.99 [5.61 ; 6.19] |
| Alternative large · DFAM<br>`(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` | book-1 | 1987 | 93.12 [90.73 ; 95.55] | 5.91 [5.47 ; 6.28] |
| Branches + wildcard · DFA<br>`(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` | book-1 | 78 | 94.02 [87.80 ; 96.35] | 6.39 [6.25 ; 6.69] |
| Branches + wildcard · DFAM<br>`(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` | book-1 | 78 | 97.86 [93.90 ; 101.62] | 6.64 [6.38 ; 6.90] |
| Regex complexe absente · DFA<br>`(ZXQ\|QXZ).*(NEVER\|PRESENT)` | book-1 | 0 | 74.52 [73.78 ; 75.84] | 5.21 [4.99 ; 5.57] |
| Regex complexe absente · DFAM<br>`(ZXQ\|QXZ).*(NEVER\|PRESENT)` | book-1 | 0 | 78.63 [75.57 ; 82.63] | 5.34 [4.99 ; 5.57] |
| Mot vide accepté · DFA<br>`(a\|b)*` | book-1 | 14915 | 61.38 [60.00 ; 62.66] | 5.81 [5.42 ; 6.14] |
| Mot vide accepté · DFAM<br>`(a\|b)*` | book-1 | 14915 | 60.66 [59.27 ; 62.29] | 5.94 [5.58 ; 6.12] |
| Groupes + alternatives + wildcards · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-1 | 643 | 101.12 [97.76 ; 104.28] | 6.33 [6.00 ; 6.67] |
| Groupes + alternatives + wildcards · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-1 | 643 | 104.13 [95.74 ; 106.70] | 6.48 [6.28 ; 6.68] |
| Littéral ×8 · KMP<br>`Elizabeth` | book-8 | 5152 | 80.32 [79.41 ; 81.71] | 9.24 [9.07 ; 9.59] |
| Littéral ×8 · DFA<br>`Elizabeth` | book-8 | 5152 | 117.55 [116.54 ; 118.37] | 9.21 [8.93 ; 9.39] |
| Littéral ×8 · DFAM<br>`Elizabeth` | book-8 | 5152 | 112.54 [110.57 ; 118.03] | 9.46 [9.24 ; 9.73] |
| Complexe ×8 · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-8 | 5144 | 141.14 [138.72 ; 143.06] | 10.59 [10.41 ; 10.99] |
| Complexe ×8 · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-8 | 5144 | 142.42 [140.45 ; 144.68] | 10.69 [10.49 ; 10.98] |
| Littéral ×32 · KMP<br>`Elizabeth` | book-32 | 20608 | 160.96 [159.27 ; 162.73] | 21.37 [21.03 ; 21.53] |
| Littéral ×32 · DFA<br>`Elizabeth` | book-32 | 20608 | 235.77 [233.94 ; 238.07] | 21.37 [21.06 ; 21.62] |
| Littéral ×32 · DFAM<br>`Elizabeth` | book-32 | 20608 | 227.65 [226.50 ; 230.47] | 21.42 [20.90 ; 21.64] |
| Complexe ×32 · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-32 | 20576 | 298.36 [296.61 ; 302.97] | 25.82 [25.51 ; 26.71] |
| Complexe ×32 · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-32 | 20576 | 299.93 [298.58 ; 305.22] | 25.83 [25.42 ; 26.27] |
| Préfixes répétés · KMP<br>`ababababac` | synthetic | 2048 | 55.79 [55.18 ; 56.92] | 5.43 [5.08 ; 5.75] |
| Préfixes répétés · DFA<br>`ababababac` | synthetic | 2048 | 84.86 [83.69 ; 86.77] | 5.63 [5.23 ; 5.87] |
| Préfixes répétés · DFAM<br>`ababababac` | synthetic | 2048 | 81.56 [79.07 ; 84.53] | 5.79 [5.62 ; 5.91] |
| Croissance profondeur 5 · DFA<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | synthetic | 2048 | 82.67 [81.72 ; 85.96] | 7.34 [7.10 ; 7.52] |
| Croissance profondeur 5 · DFAM<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | synthetic | 2048 | 85.84 [84.69 ; 88.23] | 7.17 [7.05 ; 7.49] |
| Croissance profondeur 7 · DFA<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | synthetic | 2048 | 89.21 [87.55 ; 91.15] | 7.33 [7.03 ; 7.50] |
| Croissance profondeur 7 · DFAM<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | synthetic | 2048 | 93.69 [92.22 ; 96.60] | 7.36 [7.06 ; 7.69] |

## Comparaison directe des moteurs

Même regex et même corpus sur chaque ligne. N/A : KMP ne traite pas les opérateurs regex.
GNU grep -E est l'équivalent d'egrep ; sa colonne utilise les 30 mesures du cas DFA associé, sans mélanger les séries.

| Regex / corpus | KMP ms | DFA ms | DFAM ms | GNU grep -E ms |
| :--- | ---: | ---: | ---: | ---: |
| `Elizabeth` / book-1 | 56.111 | 87.755 | 88.811 | 5.723 |
| `(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` / book-1 | N/A | 91.173 | 93.118 | 5.987 |
| `(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` / book-1 | N/A | 94.025 | 97.857 | 6.386 |
| `(ZXQ\|QXZ).*(NEVER\|PRESENT)` / book-1 | N/A | 74.515 | 78.630 | 5.209 |
| `(a\|b)*` / book-1 | N/A | 61.382 | 60.663 | 5.806 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-1 | N/A | 101.118 | 104.129 | 6.335 |
| `Elizabeth` / book-8 | 80.315 | 117.554 | 112.538 | 9.207 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-8 | N/A | 141.140 | 142.419 | 10.588 |
| `Elizabeth` / book-32 | 160.962 | 235.774 | 227.652 | 21.375 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-32 | N/A | 298.359 | 299.928 | 25.823 |
| `ababababac` / synthetic | 55.795 | 84.857 | 81.564 | 5.632 |
| `(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` / synthetic | N/A | 82.674 | 85.839 | 7.339 |
| `(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` / synthetic | N/A | 89.206 | 93.685 | 7.329 |

### Effet propre de la minimisation dans les JVM

Chaque valeur est la médiane des cinq moyennes de forks. Les médianes de phases ne sont pas additives.

| Regex / corpus | Hopcroft ms | Préparation DFA / DFAM ms | Parcours DFA / DFAM ms | Total DFA / DFAM ms |
| :--- | ---: | ---: | ---: | ---: |
| `Elizabeth` / book-1 | 0.164 | 0.360 / 0.466 | 3.410 / 3.430 | 3.760 / 3.912 |
| `(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` / book-1 | 0.473 | 0.738 / 1.121 | 4.385 / 4.844 | 5.109 / 5.945 |
| `(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` / book-1 | 0.413 | 1.341 / 1.492 | 5.476 / 5.368 | 6.806 / 6.893 |
| `(ZXQ\|QXZ).*(NEVER\|PRESENT)` / book-1 | 0.244 | 0.532 / 0.685 | 2.984 / 2.971 | 3.516 / 3.646 |
| `(a\|b)*` / book-1 | 0.000 | 0.012 / 0.013 | 1.371 / 1.367 | 1.384 / 1.379 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-1 | 0.650 | 2.458 / 2.822 | 5.476 / 5.547 | 7.986 / 8.385 |
| `Elizabeth` / book-8 | 0.161 | 0.392 / 0.504 | 25.673 / 25.783 | 26.065 / 26.275 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-8 | 0.455 | 2.281 / 2.389 | 41.590 / 42.334 | 43.870 / 44.723 |
| `Elizabeth` / book-32 | 0.178 | 0.478 / 0.608 | 102.948 / 103.892 | 103.425 / 104.505 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-32 | 0.437 | 2.266 / 2.383 | 166.309 / 168.663 | 168.536 / 171.160 |
| `ababababac` / synthetic | 0.158 | 0.382 / 0.478 | 8.875 / 8.952 | 9.249 / 9.427 |
| `(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` / synthetic | 0.441 | 0.576 / 1.027 | 8.946 / 9.075 | 9.541 / 10.108 |
| `(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` / synthetic | 0.584 | 1.517 / 2.094 | 9.005 / 9.090 | 10.516 / 11.232 |
| `(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` / synthetic | 1.281 | 4.173 / 5.384 | 9.121 / 9.220 | 13.269 / 14.623 |

## Taille structurelle des automates

Ces comptes sont collectés une seule fois hors chronométrage. Ils permettent de relier le coût de préparation à la taille réellement construite.

| Cas | Longueur regex | États NFA | Transitions NFA | États DFA recherche | Transitions DFA recherche | États DFAM | Transitions DFAM |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Littéral · KMP | 9 | 18 | 17 | 10 | 26 | 10 | 26 |
| Littéral · DFA | 9 | 18 | 17 | 10 | 26 | 10 | 26 |
| Littéral · DFAM | 9 | 18 | 17 | 10 | 26 | 10 | 26 |
| Alternative large · DFA | 48 | 92 | 96 | 36 | 245 | 35 | 238 |
| Alternative large · DFAM | 48 | 92 | 96 | 36 | 245 | 35 | 238 |
| Branches + wildcard · DFA | 63 | 118 | 125 | 75 | 564 | 41 | 216 |
| Branches + wildcard · DFAM | 63 | 118 | 125 | 75 | 564 | 41 | 216 |
| Regex complexe absente · DFA | 26 | 44 | 47 | 25 | 128 | 17 | 60 |
| Regex complexe absente · DFAM | 26 | 44 | 47 | 25 | 128 | 17 | 60 |
| Mot vide accepté · DFA | 6 | 8 | 10 | 1 | 0 | 1 | 0 |
| Mot vide accepté · DFAM | 6 | 8 | 10 | 1 | 0 | 1 | 0 |
| Groupes + alternatives + wildcards · DFA | 102 | 178 | 191 | 159 | 1348 | 101 | 660 |
| Groupes + alternatives + wildcards · DFAM | 102 | 178 | 191 | 159 | 1348 | 101 | 660 |
| Littéral ×8 · KMP | 9 | 18 | 17 | 10 | 26 | 10 | 26 |
| Littéral ×8 · DFA | 9 | 18 | 17 | 10 | 26 | 10 | 26 |
| Littéral ×8 · DFAM | 9 | 18 | 17 | 10 | 26 | 10 | 26 |
| Complexe ×8 · DFA | 102 | 178 | 191 | 159 | 1348 | 101 | 660 |
| Complexe ×8 · DFAM | 102 | 178 | 191 | 159 | 1348 | 101 | 660 |
| Littéral ×32 · KMP | 9 | 18 | 17 | 10 | 26 | 10 | 26 |
| Littéral ×32 · DFA | 9 | 18 | 17 | 10 | 26 | 10 | 26 |
| Littéral ×32 · DFAM | 9 | 18 | 17 | 10 | 26 | 10 | 26 |
| Complexe ×32 · DFA | 102 | 178 | 191 | 159 | 1348 | 101 | 660 |
| Complexe ×32 · DFAM | 102 | 178 | 191 | 159 | 1348 | 101 | 660 |
| Préfixes répétés · KMP | 10 | 20 | 19 | 11 | 26 | 11 | 26 |
| Préfixes répétés · DFA | 10 | 20 | 19 | 11 | 26 | 11 | 26 |
| Préfixes répétés · DFAM | 10 | 20 | 19 | 11 | 26 | 11 | 26 |
| Croissance profondeur 5 · DFA | 33 | 42 | 49 | 66 | 195 | 65 | 191 |
| Croissance profondeur 5 · DFAM | 33 | 42 | 49 | 66 | 195 | 65 | 191 |
| Croissance profondeur 7 · DFA | 43 | 54 | 63 | 258 | 771 | 257 | 767 |
| Croissance profondeur 7 · DFAM | 43 | 54 | 63 | 258 | 771 | 257 | 767 |
| Croissance profondeur 9 · DFA | 53 | 66 | 77 | 1026 | 3075 | 1025 | 3071 |
| Croissance profondeur 9 · DFAM | 53 | 66 | 77 | 1026 | 3075 | 1025 | 3071 |

## JVM échauffées

5 JVM distinctes par cas ; 10 prépassages puis 10 mesures par JVM. Chaque mesure reconstruit le motif et lit le fichier.
Les statistiques sont calculées sur les **5 moyennes de forks**, pas sur 50 répétitions prétendument indépendantes.
Le parcours inclut le décodage et les IO ; il ne mesure pas seulement les transitions en mémoire.

| Cas et regex exécutée | Préparation, médiane des forks ms | Parcours, médiane des forks ms | Total, médiane des forks ms |
| :--- | ---: | ---: | ---: |
| Littéral · KMP<br>`Elizabeth` | 0.021 | 2.983 | 3.004 |
| Littéral · DFA<br>`Elizabeth` | 0.360 | 3.410 | 3.760 |
| Littéral · DFAM<br>`Elizabeth` | 0.466 | 3.430 | 3.912 |
| Alternative large · DFA<br>`(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` | 0.738 | 4.385 | 5.109 |
| Alternative large · DFAM<br>`(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` | 1.121 | 4.844 | 5.945 |
| Branches + wildcard · DFA<br>`(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` | 1.341 | 5.476 | 6.806 |
| Branches + wildcard · DFAM<br>`(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` | 1.492 | 5.368 | 6.893 |
| Regex complexe absente · DFA<br>`(ZXQ\|QXZ).*(NEVER\|PRESENT)` | 0.532 | 2.984 | 3.516 |
| Regex complexe absente · DFAM<br>`(ZXQ\|QXZ).*(NEVER\|PRESENT)` | 0.685 | 2.971 | 3.646 |
| Mot vide accepté · DFA<br>`(a\|b)*` | 0.012 | 1.371 | 1.384 |
| Mot vide accepté · DFAM<br>`(a\|b)*` | 0.013 | 1.367 | 1.379 |
| Groupes + alternatives + wildcards · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 2.458 | 5.476 | 7.986 |
| Groupes + alternatives + wildcards · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 2.822 | 5.547 | 8.385 |
| Littéral ×8 · KMP<br>`Elizabeth` | 0.031 | 22.391 | 22.425 |
| Littéral ×8 · DFA<br>`Elizabeth` | 0.392 | 25.673 | 26.065 |
| Littéral ×8 · DFAM<br>`Elizabeth` | 0.504 | 25.783 | 26.275 |
| Complexe ×8 · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 2.281 | 41.590 | 43.870 |
| Complexe ×8 · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 2.389 | 42.334 | 44.723 |
| Littéral ×32 · KMP<br>`Elizabeth` | 0.046 | 89.532 | 89.578 |
| Littéral ×32 · DFA<br>`Elizabeth` | 0.478 | 102.948 | 103.425 |
| Littéral ×32 · DFAM<br>`Elizabeth` | 0.608 | 103.892 | 104.505 |
| Complexe ×32 · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 2.266 | 166.309 | 168.536 |
| Complexe ×32 · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 2.383 | 168.663 | 171.160 |
| Préfixes répétés · KMP<br>`ababababac` | 0.023 | 4.383 | 4.406 |
| Préfixes répétés · DFA<br>`ababababac` | 0.382 | 8.875 | 9.249 |
| Préfixes répétés · DFAM<br>`ababababac` | 0.478 | 8.952 | 9.427 |
| Croissance profondeur 5 · DFA<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 0.576 | 8.946 | 9.541 |
| Croissance profondeur 5 · DFAM<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 1.027 | 9.075 | 10.108 |
| Croissance profondeur 7 · DFA<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 1.517 | 9.005 | 10.516 |
| Croissance profondeur 7 · DFAM<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 2.094 | 9.090 | 11.232 |
| Croissance profondeur 9 · DFA<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 4.173 | 9.121 | 13.269 |
| Croissance profondeur 9 · DFAM<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 5.384 | 9.220 | 14.623 |

## Dispersion et limites

Signalement fixé avant mesure : IQR/médiane > 15%. Ces observations sont conservées.
Séries signalées : literal-kmp/search_preparation_ns, alternative-dfam/parsing_ns, alternative-dfam/minimization_ns, absent-dfa/nfa_ns, nullable-dfa/search_preparation_ns, scale-literal-8-kmp/parsing_ns, scale-literal-8-kmp/search_preparation_ns, scale-literal-8-kmp/preparation_ns, scale-literal-8-dfa/nfa_ns, scale-literal-8-dfam/parsing_ns, scale-literal-8-dfam/nfa_ns, scale-complex-8-dfa/parsing_ns, scale-literal-32-dfam/parsing_ns, growth-9-dfam/search_preparation_ns.

Les intervalles représentés sont des dispersions, pas des intervalles de confiance. Aucun classement universel n'est déduit.
Cache de fichiers sollicité, machine non isolée, charge de fond et température non contrôlées. L'échauffement fixe ne prouve pas une convergence du JIT.
Les cas complexes sont bornés ; cette campagne ne supprime ni ne couvre tous les cas d'explosion exponentielle.

Données brutes locales (non versionnées) : `target/report/results/automata.csv`, `cli.csv`, `jvm.csv` et leurs résumés.
Sans `--purge`, les prépassages, ordres d'exécution, résultats exacts et empreintes sont conservés localement. Avec `--purge`, les CSV/TXT sont supprimés seulement après validation et publication des assets.

## Regex exactes

Ces chaînes sont celles transmises aux moteurs ; aucune syntaxe de quantification n'est ajoutée.

<details><summary>literal-kmp — 9 caractères — book-1</summary>

```text
Elizabeth
```

</details>

<details><summary>literal-dfa — 9 caractères — book-1</summary>

```text
Elizabeth
```

</details>

<details><summary>literal-dfam — 9 caractères — book-1</summary>

```text
Elizabeth
```

</details>

<details><summary>alternative-dfa — 48 caractères — book-1</summary>

```text
(Elizabeth|Darcy|Bennet|Bingley|Collins|Wickham)
```

</details>

<details><summary>alternative-dfam — 48 caractères — book-1</summary>

```text
(Elizabeth|Darcy|Bennet|Bingley|Collins|Wickham)
```

</details>

<details><summary>wildcard-dfa — 63 caractères — book-1</summary>

```text
(Elizabeth|Darcy|Bennet|Bingley).*(said|replied|answered|cried)
```

</details>

<details><summary>wildcard-dfam — 63 caractères — book-1</summary>

```text
(Elizabeth|Darcy|Bennet|Bingley).*(said|replied|answered|cried)
```

</details>

<details><summary>absent-dfa — 26 caractères — book-1</summary>

```text
(ZXQ|QXZ).*(NEVER|PRESENT)
```

</details>

<details><summary>absent-dfam — 26 caractères — book-1</summary>

```text
(ZXQ|QXZ).*(NEVER|PRESENT)
```

</details>

<details><summary>nullable-dfa — 6 caractères — book-1</summary>

```text
(a|b)*
```

</details>

<details><summary>nullable-dfam — 6 caractères — book-1</summary>

```text
(a|b)*
```

</details>

<details><summary>complex-dfa — 102 caractères — book-1</summary>

```text
((Elizabeth|Darcy|Bennet|Bingley).*(said|replied|answered|cried))|((Mr|Mrs)\..*(Bennet|Darcy|Bingley))
```

</details>

<details><summary>complex-dfam — 102 caractères — book-1</summary>

```text
((Elizabeth|Darcy|Bennet|Bingley).*(said|replied|answered|cried))|((Mr|Mrs)\..*(Bennet|Darcy|Bingley))
```

</details>

<details><summary>scale-literal-8-kmp — 9 caractères — book-8</summary>

```text
Elizabeth
```

</details>

<details><summary>scale-literal-8-dfa — 9 caractères — book-8</summary>

```text
Elizabeth
```

</details>

<details><summary>scale-literal-8-dfam — 9 caractères — book-8</summary>

```text
Elizabeth
```

</details>

<details><summary>scale-complex-8-dfa — 102 caractères — book-8</summary>

```text
((Elizabeth|Darcy|Bennet|Bingley).*(said|replied|answered|cried))|((Mr|Mrs)\..*(Bennet|Darcy|Bingley))
```

</details>

<details><summary>scale-complex-8-dfam — 102 caractères — book-8</summary>

```text
((Elizabeth|Darcy|Bennet|Bingley).*(said|replied|answered|cried))|((Mr|Mrs)\..*(Bennet|Darcy|Bingley))
```

</details>

<details><summary>scale-literal-32-kmp — 9 caractères — book-32</summary>

```text
Elizabeth
```

</details>

<details><summary>scale-literal-32-dfa — 9 caractères — book-32</summary>

```text
Elizabeth
```

</details>

<details><summary>scale-literal-32-dfam — 9 caractères — book-32</summary>

```text
Elizabeth
```

</details>

<details><summary>scale-complex-32-dfa — 102 caractères — book-32</summary>

```text
((Elizabeth|Darcy|Bennet|Bingley).*(said|replied|answered|cried))|((Mr|Mrs)\..*(Bennet|Darcy|Bingley))
```

</details>

<details><summary>scale-complex-32-dfam — 102 caractères — book-32</summary>

```text
((Elizabeth|Darcy|Bennet|Bingley).*(said|replied|answered|cried))|((Mr|Mrs)\..*(Bennet|Darcy|Bingley))
```

</details>

<details><summary>overlap-kmp — 10 caractères — synthetic</summary>

```text
ababababac
```

</details>

<details><summary>overlap-dfa — 10 caractères — synthetic</summary>

```text
ababababac
```

</details>

<details><summary>overlap-dfam — 10 caractères — synthetic</summary>

```text
ababababac
```

</details>

<details><summary>growth-5-dfa — 33 caractères — synthetic</summary>

```text
(a|b)*a(a|b)(a|b)(a|b)(a|b)(a|b)b
```

</details>

<details><summary>growth-5-dfam — 33 caractères — synthetic</summary>

```text
(a|b)*a(a|b)(a|b)(a|b)(a|b)(a|b)b
```

</details>

<details><summary>growth-7-dfa — 43 caractères — synthetic</summary>

```text
(a|b)*a(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)b
```

</details>

<details><summary>growth-7-dfam — 43 caractères — synthetic</summary>

```text
(a|b)*a(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)b
```

</details>

<details><summary>growth-9-dfa — 53 caractères — synthetic</summary>

```text
(a|b)*a(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)b
```

</details>

<details><summary>growth-9-dfam — 53 caractères — synthetic</summary>

```text
(a|b)*a(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)(a|b)b
```

</details>
