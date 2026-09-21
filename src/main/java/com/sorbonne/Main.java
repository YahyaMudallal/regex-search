package com.sorbonne;

/**
 * Point d'entrée provisoire de l'application de recherche par expression régulière.
 *
 * <p>Pour le moment, le programme affiche uniquement un message de démarrage.
 * La lecture des arguments et la recherche dans un fichier restent à implémenter.</p>
 */
public class Main {
    /**
     * Crée une instance sans configuration.
     * Le lancement par {@link #main(String[])} ne nécessite pas d'instance.
     */
    public Main() {
    }

    /**
     * Lance le programme et affiche son message de démarrage.
     *
     * @param args arguments de la ligne de commande, actuellement inutilisés
     */
    public static void main(String[] args) {
        System.out.println("Hello World!");
    }
}
