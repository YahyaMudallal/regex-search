package com.sorbonne.search;

import java.util.Objects;

/**
 * Recherche litterale avec Knuth-Morris-Pratt sur l'alphabet 8 bits du projet.
 * La preparation prend O(m), le scan O(n), et la memoire supplementaire O(m).
 */
public class KMPSearch implements SearchAlgorithm<String> {
    public KMPSearch() {
    }

    @Override
    public boolean search(String text, String pattern) {
        Objects.requireNonNull(text, "Le texte ne doit pas etre nul");
        return prepare(pattern).search(text);
    }

    /** Prepare une fois le motif ASCII et sa table LPS. */
    public static Prepared prepare(String pattern) {
        return new Prepared(Objects.requireNonNull(pattern, "Le motif ne doit pas etre nul"));
    }

    /** Motif et table LPS immuables, reutilisables entre plusieurs scans. */
    public static final class Prepared implements PreparedSearch {
        private final byte[] pattern;
        private final int[] lps;

        private Prepared(String value) {
            pattern = toAsciiBytes(value);
            lps = buildLPS(pattern);
        }

        @Override
        public SearchCursor newCursor() {
            return new SearchCursor() {
                private int position;

                @Override
                public boolean accept(int symbol) {
                    if ((symbol & ~0xff) != 0) {
                        throw new IllegalArgumentException("Symbole hors alphabet 8 bits : " + symbol);
                    }
                    if (!matches()) {
                        position = advance(position, symbol);
                    }
                    return matches();
                }

                @Override
                public boolean accept(byte[] buffer, int offset, int length) {
                    Objects.requireNonNull(buffer, "Le tampon ne doit pas etre nul");
                    if (offset < 0 || length < 0 || offset > buffer.length - length) {
                        throw new IndexOutOfBoundsException("Segment d'octets invalide");
                    }
                    if (matches() || length == 0) {
                        return matches();
                    }
                    int current = position;
                    int end = offset + length;
                    int patternLength = pattern.length;
                    for (int i = offset; i < end; i++) {
                        int symbol = buffer[i] & 0xff;
                        while (current > 0 && symbol != (pattern[current] & 0xff)) {
                            current = lps[current - 1];
                        }
                        if (symbol == (pattern[current] & 0xff)) {
                            current++;
                            if (current == patternLength) {
                                position = current;
                                return true;
                            }
                        }
                    }
                    position = current;
                    return false;
                }

                @Override
                public boolean matches() {
                    return position == pattern.length;
                }

                @Override
                public void reset() {
                    position = 0;
                }
            };
        }

        private int advance(int position, int symbol) {
            if (pattern.length == 0) {
                return 0;
            }
            while (position > 0 && symbol != (pattern[position] & 0xff)) {
                position = lps[position - 1];
            }
            return symbol == (pattern[position] & 0xff) ? position + 1 : position;
        }
    }

    private static byte[] toAsciiBytes(String pattern) {
        byte[] bytes = new byte[pattern.length()];
        for (int i = 0; i < pattern.length(); i++) {
            char symbol = pattern.charAt(i);
            if (symbol > 0x7f) {
                throw new IllegalArgumentException("KMP exige un motif ASCII");
            }
            bytes[i] = (byte) symbol;
        }
        return bytes;
    }

    private static int[] buildLPS(byte[] pattern) {
        int[] lps = new int[pattern.length];
        int length = 0;
        int i = 1;
        while (i < pattern.length) {
            if (pattern[i] == pattern[length]) {
                lps[i++] = ++length;
            } else if (length != 0) {
                length = lps[length - 1];
            } else {
                lps[i++] = 0;
            }
        }
        return lps;
    }
}
