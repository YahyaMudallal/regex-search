package com.sorbonne.search;

/** État mutable d'une recherche en flux, privé à une ligne ou à un lecteur. */
public interface SearchCursor {
    /** Consomme un char ; une correspondance trouvée reste acquise jusqu'au reset. */
    boolean accept(char symbol);

    /**
     * Consomme un segment dont tous les octets représentent directement des
     * caractères ASCII. Le chargeur UTF-8 utilise ce chemin pour éviter le
     * décodage UTF-8 → UTF-16 et l'appel virtuel par caractère sur les corpus
     * majoritairement ASCII.
     *
     * @param buffer tampon d'octets
     * @param offset première case à consommer
     * @param length nombre d'octets ASCII à consommer
     * @return vrai si une correspondance est acquise après ce segment
     */
    default boolean acceptAscii(byte[] buffer, int offset, int length) {
        for (int i = offset, end = offset + length; i < end; i++) {
            if (accept((char) (buffer[i] & 0x7f))) {
                return true;
            }
        }
        return matches();
    }

    /** Inclut la correspondance vide avant la lecture du premier caractère. */
    boolean matches();

    /** Repart au début d'une nouvelle ligne sans reconstruire le moteur. */
    void reset();
}
