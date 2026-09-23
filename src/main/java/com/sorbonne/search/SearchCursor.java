package com.sorbonne.search;

/** État mutable d'une recherche en flux, privé à une ligne ou à un lecteur. */
public interface SearchCursor {
    /** Consomme un char ; une correspondance trouvée reste acquise jusqu'au reset. */
    boolean accept(char symbol);

    /** Inclut la correspondance vide avant la lecture du premier caractère. */
    boolean matches();

    /** Repart au début d'une nouvelle ligne sans reconstruire le moteur. */
    void reset();
}
