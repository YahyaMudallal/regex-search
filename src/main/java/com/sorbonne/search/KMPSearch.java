package com.sorbonne.search;

import java.util.Objects;

/**
 * Recherche un motif littéral avec l'algorithme Knuth-Morris-Pratt (KMP).
 *
 * <p>KMP prépare une table décrivant les répétitions du motif. Après une différence
 * avec le texte, cette table permet de réutiliser une partie des caractères déjà
 * reconnus, sans faire reculer la position dans le texte.</p>
 *
 * <p>Pour un texte de longueur {@code n} et un motif de longueur {@code m},
 * la préparation prend {@code O(m)}, la recherche {@code O(n)}, et la mémoire
 * supplémentaire {@code O(m)}. La méthode s'arrête à la première occurrence.</p>
 *
 * <p>Cette classe utilise un motif String littéral avec {@link SearchAlgorithm} :
 * elle n'interprète pas les expressions régulières. Aucun état n'est conservé
 * entre les appels ; une instance peut donc servir à plusieurs recherches.</p>
 */
public class KMPSearch implements SearchAlgorithm<String> {

    /** Crée un moteur KMP sans configuration ni données de recherche partagées. */
    public KMPSearch() {
    }

    /**
     * Cherche la première occurrence du motif après avoir préparé sa table LPS.
     *
     * @param text texte à parcourir, non nul
     * @param pattern motif littéral à chercher, non nul
     * @return {@code true} si le motif est présent ; toujours {@code true} pour un motif vide
     * @throws NullPointerException si le texte ou le motif est nul
     */
    @Override
    public boolean search(String text, String pattern) {
        Objects.requireNonNull(text, "Le texte ne doit pas être nul");
        Objects.requireNonNull(pattern, "Le motif ne doit pas être nul");
        if (pattern.isEmpty()) {
            return true;
        }

        // Pour chaque position du motif, longueur du préfixe réutilisable après un échec.
        int[] lps = buildLPS(pattern);
        // Position du prochain caractère à comparer dans le texte ; elle ne recule jamais.
        int i = 0;
        // Position dans le motif, également égale au nombre de caractères déjà reconnus.
        int j = 0;

        while (i < text.length()) {
            if (text.charAt(i) == pattern.charAt(j)) {
                i++;
                j++;
            }

            if (j == pattern.length()) {
                return true;
            } else if (i < text.length() && text.charAt(i) != pattern.charAt(j)) {
                if (j != 0) {
                    // Réutiliser le plus long préfixe du motif qui termine la partie reconnue.
                    j = lps[j - 1];
                } else {
                    // Aucun préfixe à réutiliser : essayer le caractère suivant du texte.
                    i++;
                }
            }
        }
        return false;
    }

    /**
     * Construit la table LPS, pour « Longest Proper Prefix which is also Suffix ».
     *
     * <p>À la position {@code i}, la valeur est la longueur du plus long préfixe
     * propre de {@code pattern[0..i]} qui en est aussi un suffixe. « Propre »
     * signifie que le préfixe ne peut pas être toute la portion considérée.</p>
     *
     * <p>Exemple : le motif {@code abab} donne {@code [0, 0, 1, 2]}.
     * Pour {@code abab}, le préfixe {@code ab} est aussi un suffixe.</p>
     *
     * @param pattern motif non nul, validé par la méthode appelante
     * @return table de même longueur que le motif, vide si le motif est vide
     */
    private int[] buildLPS(String pattern) {
        // Table calculée progressivement de gauche à droite.
        int[] lps = new int[pattern.length()];
        // Longueur du préfixe candidat, également utilisée comme indice de comparaison.
        int length = 0;
        // Position à calculer ; la première valeur vaut toujours zéro.
        int i = 1;

        while (i < pattern.length()) {

            if (pattern.charAt(i) == pattern.charAt(length)) {
                length++;
                lps[i] = length;
                i++;
            } else {
                if (length != 0) {
                    // Essayer un préfixe candidat plus court avant d'avancer dans le motif.
                    length = lps[length - 1];
                } else {
                    lps[i] = 0;
                    i++;
                }
            }
        }
        return lps;
    }
}
