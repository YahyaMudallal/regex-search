# Référence expérimentale reproductible

Exécution UTC : 2026-09-23T15:42:07.829803+00:00

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
| Littéral · KMP<br>`Elizabeth` | book-1 | 644 | 54.08 [53.25 ; 55.50] | 7.40 [5.50 ; 7.80] |
| Littéral · DFA<br>`Elizabeth` | book-1 | 644 | 73.85 [73.39 ; 75.34] | 6.23 [5.99 ; 6.44] |
| Littéral · DFAM<br>`Elizabeth` | book-1 | 644 | 75.68 [74.70 ; 78.09] | 6.29 [6.16 ; 6.49] |
| Alternative large · DFA<br>`(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` | book-1 | 1987 | 78.48 [78.06 ; 81.64] | 6.42 [5.85 ; 6.89] |
| Alternative large · DFAM<br>`(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` | book-1 | 1987 | 80.87 [80.40 ; 81.55] | 6.44 [5.82 ; 6.58] |
| Branches + wildcard · DFA<br>`(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` | book-1 | 78 | 82.24 [81.81 ; 83.23] | 7.00 [6.85 ; 7.12] |
| Branches + wildcard · DFAM<br>`(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` | book-1 | 78 | 85.11 [84.01 ; 89.22] | 7.11 [7.00 ; 7.68] |
| Regex complexe absente · DFA<br>`(ZXQ\|QXZ).*(NEVER\|PRESENT)` | book-1 | 0 | 76.45 [75.98 ; 77.33] | 5.76 [5.13 ; 5.90] |
| Regex complexe absente · DFAM<br>`(ZXQ\|QXZ).*(NEVER\|PRESENT)` | book-1 | 0 | 78.85 [77.96 ; 81.69] | 5.96 [5.44 ; 6.30] |
| Mot vide accepté · DFA<br>`(a\|b)*` | book-1 | 14915 | 52.25 [51.34 ; 54.16] | 7.50 [6.08 ; 8.47] |
| Mot vide accepté · DFAM<br>`(a\|b)*` | book-1 | 14915 | 51.71 [45.74 ; 52.45] | 7.43 [6.54 ; 7.68] |
| Groupes + alternatives + wildcards · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-1 | 643 | 86.70 [85.60 ; 87.44] | 6.93 [6.71 ; 7.38] |
| Groupes + alternatives + wildcards · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-1 | 643 | 90.17 [88.72 ; 91.12] | 6.98 [6.73 ; 7.12] |
| Littéral ×8 · KMP<br>`Elizabeth` | book-8 | 5152 | 78.75 [75.56 ; 80.75] | 10.87 [9.27 ; 11.96] |
| Littéral ×8 · DFA<br>`Elizabeth` | book-8 | 5152 | 92.26 [91.51 ; 93.36] | 9.49 [9.17 ; 9.63] |
| Littéral ×8 · DFAM<br>`Elizabeth` | book-8 | 5152 | 93.68 [92.84 ; 100.78] | 9.61 [9.39 ; 10.01] |
| Complexe ×8 · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-8 | 5144 | 118.29 [113.77 ; 118.81] | 11.44 [11.03 ; 11.69] |
| Complexe ×8 · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-8 | 5144 | 108.49 [104.13 ; 119.59] | 11.38 [11.07 ; 11.53] |
| Littéral ×32 · KMP<br>`Elizabeth` | book-32 | 20608 | 99.76 [98.81 ; 101.81] | 22.24 [21.93 ; 22.81] |
| Littéral ×32 · DFA<br>`Elizabeth` | book-32 | 20608 | 151.69 [150.08 ; 154.07] | 21.04 [20.70 ; 21.21] |
| Littéral ×32 · DFAM<br>`Elizabeth` | book-32 | 20608 | 152.56 [151.84 ; 159.61] | 21.48 [21.03 ; 21.96] |
| Complexe ×32 · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-32 | 20576 | 173.83 [172.86 ; 177.07] | 25.63 [25.51 ; 26.55] |
| Complexe ×32 · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | book-32 | 20576 | 166.26 [162.94 ; 169.19] | 25.60 [25.37 ; 25.98] |
| Préfixes répétés · KMP<br>`ababababac` | synthetic | 2048 | 61.39 [58.10 ; 62.61] | 7.28 [5.67 ; 7.64] |
| Préfixes répétés · DFA<br>`ababababac` | synthetic | 2048 | 77.79 [76.93 ; 81.78] | 6.16 [5.91 ; 6.43] |
| Préfixes répétés · DFAM<br>`ababababac` | synthetic | 2048 | 79.09 [78.61 ; 79.89] | 6.12 [5.95 ; 6.28] |
| Croissance profondeur 5 · DFA<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | synthetic | 2048 | 80.88 [71.18 ; 82.24] | 7.86 [7.61 ; 8.10] |
| Croissance profondeur 5 · DFAM<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | synthetic | 2048 | 85.22 [84.83 ; 85.91] | 8.01 [7.86 ; 8.16] |
| Croissance profondeur 7 · DFA<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | synthetic | 2048 | 76.45 [75.64 ; 80.92] | 7.75 [7.58 ; 8.10] |
| Croissance profondeur 7 · DFAM<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | synthetic | 2048 | 93.00 [92.50 ; 93.87] | 8.05 [7.80 ; 8.21] |

## Comparaison directe des moteurs

Même regex et même corpus sur chaque ligne. N/A : KMP ne traite pas les opérateurs regex.
GNU grep -E est l'équivalent d'egrep ; sa colonne utilise les 30 mesures du cas DFA associé, sans mélanger les séries.

| Regex / corpus | KMP ms | DFA ms | DFAM ms | GNU grep -E ms |
| :--- | ---: | ---: | ---: | ---: |
| `Elizabeth` / book-1 | 54.083 | 73.851 | 75.683 | 6.231 |
| `(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` / book-1 | N/A | 78.481 | 80.875 | 6.423 |
| `(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` / book-1 | N/A | 82.237 | 85.106 | 6.996 |
| `(ZXQ\|QXZ).*(NEVER\|PRESENT)` / book-1 | N/A | 76.446 | 78.849 | 5.758 |
| `(a\|b)*` / book-1 | N/A | 52.253 | 51.709 | 7.496 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-1 | N/A | 86.700 | 90.170 | 6.934 |
| `Elizabeth` / book-8 | 78.749 | 92.257 | 93.683 | 9.494 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-8 | N/A | 118.289 | 108.492 | 11.438 |
| `Elizabeth` / book-32 | 99.763 | 151.692 | 152.555 | 21.043 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-32 | N/A | 173.835 | 166.259 | 25.631 |
| `ababababac` / synthetic | 61.392 | 77.788 | 79.089 | 6.160 |
| `(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` / synthetic | N/A | 80.884 | 85.220 | 7.856 |
| `(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` / synthetic | N/A | 76.445 | 93.000 | 7.749 |

### Effet propre de la minimisation dans les JVM

Chaque valeur est la médiane des cinq moyennes de forks. Les médianes de phases ne sont pas additives.

| Regex / corpus | Hopcroft ms | Préparation DFA / DFAM ms | Parcours DFA / DFAM ms | Total DFA / DFAM ms |
| :--- | ---: | ---: | ---: | ---: |
| `Elizabeth` / book-1 | 0.153 | 0.364 / 0.453 | 2.499 / 2.529 | 2.863 / 2.975 |
| `(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` / book-1 | 0.494 | 0.869 / 1.327 | 2.451 / 2.491 | 3.320 / 3.793 |
| `(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` / book-1 | 0.410 | 1.548 / 1.649 | 2.602 / 2.613 | 4.168 / 4.262 |
| `(ZXQ\|QXZ).*(NEVER\|PRESENT)` / book-1 | 0.237 | 0.579 / 0.738 | 2.587 / 2.589 | 3.164 / 3.333 |
| `(a\|b)*` / book-1 | 0.000 | 0.012 / 0.012 | 0.670 / 0.599 | 0.682 / 0.611 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-1 | 0.793 | 2.838 / 3.072 | 2.570 / 2.581 | 5.408 / 5.652 |
| `Elizabeth` / book-8 | 0.155 | 0.364 / 0.452 | 18.933 / 19.025 | 19.312 / 19.475 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-8 | 0.545 | 2.210 / 2.512 | 19.245 / 19.915 | 21.429 / 22.414 |
| `Elizabeth` / book-32 | 0.173 | 0.437 / 0.548 | 76.027 / 76.570 | 76.453 / 77.119 |
| `((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` / book-32 | 0.416 | 2.256 / 2.323 | 77.122 / 77.364 | 79.378 / 79.696 |
| `ababababac` / synthetic | 0.138 | 0.380 / 0.472 | 3.983 / 4.023 | 4.364 / 4.473 |
| `(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` / synthetic | 0.376 | 0.733 / 1.151 | 4.080 / 4.085 | 4.820 / 5.236 |
| `(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` / synthetic | 0.703 | 1.759 / 2.510 | 4.075 / 4.080 | 5.839 / 6.583 |
| `(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` / synthetic | 1.286 | 4.752 / 5.947 | 4.087 / 4.235 | 8.858 / 10.189 |

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
| Littéral · KMP<br>`Elizabeth` | 0.021 | 1.189 | 1.209 |
| Littéral · DFA<br>`Elizabeth` | 0.364 | 2.499 | 2.863 |
| Littéral · DFAM<br>`Elizabeth` | 0.453 | 2.529 | 2.975 |
| Alternative large · DFA<br>`(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` | 0.869 | 2.451 | 3.320 |
| Alternative large · DFAM<br>`(Elizabeth\|Darcy\|Bennet\|Bingley\|Collins\|Wickham)` | 1.327 | 2.491 | 3.793 |
| Branches + wildcard · DFA<br>`(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` | 1.548 | 2.602 | 4.168 |
| Branches + wildcard · DFAM<br>`(Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried)` | 1.649 | 2.613 | 4.262 |
| Regex complexe absente · DFA<br>`(ZXQ\|QXZ).*(NEVER\|PRESENT)` | 0.579 | 2.587 | 3.164 |
| Regex complexe absente · DFAM<br>`(ZXQ\|QXZ).*(NEVER\|PRESENT)` | 0.738 | 2.589 | 3.333 |
| Mot vide accepté · DFA<br>`(a\|b)*` | 0.012 | 0.670 | 0.682 |
| Mot vide accepté · DFAM<br>`(a\|b)*` | 0.012 | 0.599 | 0.611 |
| Groupes + alternatives + wildcards · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 2.838 | 2.570 | 5.408 |
| Groupes + alternatives + wildcards · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 3.072 | 2.581 | 5.652 |
| Littéral ×8 · KMP<br>`Elizabeth` | 0.018 | 8.042 | 8.059 |
| Littéral ×8 · DFA<br>`Elizabeth` | 0.364 | 18.933 | 19.312 |
| Littéral ×8 · DFAM<br>`Elizabeth` | 0.452 | 19.025 | 19.475 |
| Complexe ×8 · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 2.210 | 19.245 | 21.429 |
| Complexe ×8 · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 2.512 | 19.915 | 22.414 |
| Littéral ×32 · KMP<br>`Elizabeth` | 0.029 | 33.045 | 33.074 |
| Littéral ×32 · DFA<br>`Elizabeth` | 0.437 | 76.027 | 76.453 |
| Littéral ×32 · DFAM<br>`Elizabeth` | 0.548 | 76.570 | 77.119 |
| Complexe ×32 · DFA<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 2.256 | 77.122 | 79.378 |
| Complexe ×32 · DFAM<br>`((Elizabeth\|Darcy\|Bennet\|Bingley).*(said\|replied\|answered\|cried))\|((Mr\|Mrs)\..*(Bennet\|Darcy\|Bingley))` | 2.323 | 77.364 | 79.696 |
| Préfixes répétés · KMP<br>`ababababac` | 0.022 | 2.839 | 2.861 |
| Préfixes répétés · DFA<br>`ababababac` | 0.380 | 3.983 | 4.364 |
| Préfixes répétés · DFAM<br>`ababababac` | 0.472 | 4.023 | 4.473 |
| Croissance profondeur 5 · DFA<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 0.733 | 4.080 | 4.820 |
| Croissance profondeur 5 · DFAM<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 1.151 | 4.085 | 5.236 |
| Croissance profondeur 7 · DFA<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 1.759 | 4.075 | 5.839 |
| Croissance profondeur 7 · DFAM<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 2.510 | 4.080 | 6.583 |
| Croissance profondeur 9 · DFA<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 4.752 | 4.087 | 8.858 |
| Croissance profondeur 9 · DFAM<br>`(a\|b)*a(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)(a\|b)b` | 5.947 | 4.235 | 10.189 |

## Dispersion et limites

Signalement fixé avant mesure : IQR/médiane > 15%. Ces observations sont conservées.
Séries signalées : literal-kmp/grep, alternative-dfa/grep, nullable-dfa/grep, nullable-dfam/grep, scale-literal-8-kmp/grep, overlap-kmp/grep, literal-kmp/parsing_ns, alternative-dfam/parsing_ns, nullable-dfam/search_preparation_ns, scale-literal-8-dfam/nfa_ns, scale-complex-8-dfa/nfa_ns, scale-complex-8-dfam/search_preparation_ns, scale-literal-32-kmp/search_preparation_ns, scale-literal-32-dfam/parsing_ns, scale-literal-32-dfam/nfa_ns, scale-complex-32-dfa/parsing_ns, scale-complex-32-dfa/nfa_ns, overlap-kmp/search_preparation_ns, growth-5-dfam/nfa_ns, growth-5-dfam/dfa_ns.

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
