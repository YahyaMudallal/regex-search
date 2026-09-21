package com.sorbonne.search;

/**
 * Définit la recherche d'un motif littéral dans un texte.
 *
 * <p>Un motif est trouvé s'il apparaît comme une suite de caractères consécutifs,
 * à n'importe quelle position du texte. La comparaison est sensible à la casse :
 * {@code Bonjour} et {@code bonjour} sont différents. Les symboles comme
 * {@code .}, {@code *} ou {@code |} sont des caractères ordinaires, pas des opérateurs.</p>
 *
 * <p>Les comparaisons portent sur les unités UTF-16 de {@link String}, sans
 * normalisation des accents. Le motif vide est toujours présent. Les arguments
 * nuls sont refusés. Ce contrat permet de comparer les implémentations à
 * {@link String#contains(CharSequence)}.</p>
 */
public interface SearchAlgorithm {
    /**
     * Indique si le texte contient au moins une occurrence du motif littéral.
     *
     * @param text texte à parcourir, éventuellement vide mais jamais nul
     * @param pattern motif à chercher, éventuellement vide mais jamais nul
     * @return {@code true} si le motif est présent ou vide, sinon {@code false}
     * @throws NullPointerException si l'un des arguments est nul
     */
    boolean search(String text, String pattern);
}
