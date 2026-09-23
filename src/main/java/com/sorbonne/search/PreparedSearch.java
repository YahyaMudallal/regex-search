package com.sorbonne.search;

import java.util.Objects;

/** Motif prepare immuable, partageable ; chaque curseur possede son propre etat. */
public interface PreparedSearch {
    /**
     * Recherche dans une chaine dont chaque caractere doit appartenir a l'alphabet 8 bits.
     * Cette methode sert aux tests et demonstrations ; le chemin fichier travaille directement
     * sur les octets via {@link SearchCursor}.
     */
    default boolean search(String text) {
        Objects.requireNonNull(text, "Le texte ne doit pas etre nul");
        SearchCursor cursor = newCursor();
        if (cursor.matches()) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            char symbol = text.charAt(i);
            if (symbol > 0xff) {
                throw new IllegalArgumentException("Le texte contient un symbole hors alphabet 8 bits");
            }
            if (cursor.accept(symbol)) {
                return true;
            }
        }
        return false;
    }

    /** Recherche directement dans un bloc d'octets. */
    default boolean search(byte[] text) {
        Objects.requireNonNull(text, "Le texte ne doit pas etre nul");
        SearchCursor cursor = newCursor();
        return cursor.matches() || cursor.accept(text, 0, text.length);
    }

    SearchCursor newCursor();
}
