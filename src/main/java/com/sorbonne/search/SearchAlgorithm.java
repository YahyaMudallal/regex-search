package com.sorbonne.search;

/**
 * Définit la recherche d'une occurrence dans un texte, avec un motif typé.
 *
 * <p>Le résultat vaut vrai si une sous-chaîne du texte appartient au langage du
 * motif. Pour {@link KMPSearch}, le motif est une String littérale ; pour
 * {@link NativeSearch}, c'est un automate déterministe représentant un langage.
 * Le paramètre générique empêche de confondre ces deux représentations.</p>
 *
 * <p>Le moteur de production travaille sur un alphabet de 256 valeurs. Les méthodes
 * prenant une {@link String} sont des commodités pour les tests et démonstrations ;
 * le scan des fichiers consomme directement les octets. Un langage qui accepte le
 * mot vide correspond à tout texte, même vide.</p>
 *
 * @param <P> représentation du motif : String littérale ou Automaton, par exemple
 */
public interface SearchAlgorithm<P> {
    /**
     * Indique si une sous-chaîne du texte est reconnue par le motif.
     *
     * @param text texte à parcourir, éventuellement vide mais jamais nul
     * @param pattern représentation non nulle du motif selon l'implémentation
     * @return true si une occurrence existe, y compris une occurrence vide
     * @throws NullPointerException si l'un des arguments est nul
     */
    boolean search(String text, P pattern);
}
