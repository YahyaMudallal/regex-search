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
 * entre les appels ; une instance peut donc servir à plusieurs recherches.
 * Pour partager la préparation entre plusieurs textes, utiliser {@link #prepare(String)}.</p>
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
        return prepare(pattern).search(text);
    }

    /**
     * Prépare une fois la table LPS pour rechercher le même motif dans plusieurs lignes.
     * La construction coûte O(m) en temps et en mémoire pour m unités UTF-16.
     * @param pattern motif littéral non nul, éventuellement vide
     * @return moteur immuable réutilisable, y compris entre plusieurs threads
     * @throws NullPointerException si le motif est nul
     */
    public static Prepared prepare(String pattern) {
        return new Prepared(Objects.requireNonNull(pattern, "Le motif ne doit pas être nul"));
    }

    /** Motif et table LPS partagés ; les positions de recherche restent locales à chaque appel. */
    public static final class Prepared implements PreparedSearch {
        /** Motif littéral dont les préfixes ont été calculés. */
        private final String pattern;
        /** Copie ASCII du motif lorsqu'elle existe, pour le chemin fichier. */
        private final byte[] asciiPattern;
        /** Table privée des préfixes réutilisables, jamais modifiée après construction. */
        private final int[] lps;

        /**
         * Construit le moteur à partir d'un motif déjà validé.
         * @param pattern motif non nul
         */
        private Prepared(String pattern) {
            this.pattern = pattern;
            this.asciiPattern = toAscii(pattern);
            this.lps = buildLPS(pattern);
        }

        /**
         * Cherche une occurrence sans reconstruire la table ni conserver d'état entre les lignes.
         * Le coût est O(n) pour n unités UTF-16, avec O(1) de mémoire supplémentaire :
         * l'indice du texte ne recule jamais et les replis sont amortis sur les avancées.
         * @param text texte non nul
         * @return vrai dès la première occurrence, toujours vrai pour un motif vide
         * @throws NullPointerException si le texte est nul
         */
        public boolean search(String text) {
            Objects.requireNonNull(text, "Le texte ne doit pas être nul");
            if (pattern.isEmpty()) {
                return true;
            }
            if (text.length() < pattern.length()) {
                return false;
            }
            int j = 0;
            for (int i = 0; i < text.length(); i++) {
                j = advance(j, text.charAt(i));
                if (j == pattern.length()) {
                    return true;
                }
            }
            return false;
        }

        private static byte[] toAscii(String pattern) {
            byte[] bytes = new byte[pattern.length()];
            for (int i = 0; i < pattern.length(); i++) {
                char symbol = pattern.charAt(i);
                if (symbol > 0x7f) {
                    return null;
                }
                bytes[i] = (byte) symbol;
            }
            return bytes;
        }

        private int advance(int position, char symbol) {
            while (position > 0 && symbol != pattern.charAt(position)) {
                position = lps[position - 1];
            }
            return symbol == pattern.charAt(position) ? position + 1 : position;
        }

        /** Continue entre blocs sans conserver le texte ; reset sépare les lignes. */
        @Override
        public SearchCursor newCursor() {
            return new SearchCursor() {
                private int position;

                @Override
                public boolean accept(char symbol) {
                    if (!matches()) {
                        position = advance(position, symbol);
                    }
                    return matches();
                }

                @Override
                public boolean acceptAscii(byte[] buffer, int offset, int length) {
                    if (matches() || length == 0) {
                        return matches();
                    }
                    int current = position;
                    int end = offset + length;
                    int patternLength = pattern.length();
                    if (asciiPattern != null) {
                        byte[] expected = asciiPattern;
                        for (int i = offset; i < end; i++) {
                            byte symbol = buffer[i];
                            while (current > 0 && symbol != expected[current]) {
                                current = lps[current - 1];
                            }
                            if (symbol == expected[current]) {
                                current++;
                                if (current == patternLength) {
                                    position = current;
                                    return true;
                                }
                            }
                        }
                    } else {
                        for (int i = offset; i < end; i++) {
                            char symbol = (char) (buffer[i] & 0x7f);
                            while (current > 0 && symbol != pattern.charAt(current)) {
                                current = lps[current - 1];
                            }
                            if (symbol == pattern.charAt(current)) {
                                current++;
                                if (current == patternLength) {
                                    position = current;
                                    return true;
                                }
                            }
                        }
                    }
                    position = current;
                    return false;
                }

                @Override
                public boolean matches() {
                    return position == pattern.length();
                }

                @Override
                public void reset() {
                    position = 0;
                }
            };
        }
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
    private static int[] buildLPS(String pattern) {
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
