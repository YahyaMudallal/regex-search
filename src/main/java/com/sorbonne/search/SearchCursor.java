package com.sorbonne.search;

/** Etat mutable d'une recherche en flux, prive a une ligne ou a un lecteur. */
public interface SearchCursor {
    /** Consomme une valeur de l'alphabet 8 bits, comprise entre 0 et 255. */
    boolean accept(int symbol);

    /**
     * Consomme un segment d'octets sans conversion intermediaire.
     *
     * @param buffer tampon source
     * @param offset premiere case a consommer
     * @param length nombre d'octets a consommer
     * @return vrai si une correspondance est acquise apres ce segment
     */
    default boolean accept(byte[] buffer, int offset, int length) {
        if (buffer == null) {
            throw new NullPointerException("Le tampon ne doit pas etre nul");
        }
        if (offset < 0 || length < 0 || offset > buffer.length - length) {
            throw new IndexOutOfBoundsException("Segment d'octets invalide");
        }
        for (int i = offset, end = offset + length; i < end; i++) {
            if (accept(buffer[i] & 0xff)) {
                return true;
            }
        }
        return matches();
    }

    /** Inclut la correspondance vide avant la lecture du premier symbole. */
    boolean matches();

    /** Repart au debut d'une nouvelle ligne sans reconstruire le moteur. */
    void reset();
}
