package com.sorbonne;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Contient le test minimal fourni à l'initialisation du projet.
 *
 * <p>Ce test vérifie seulement qu'une assertion JUnit peut être exécutée.
 * Il ne valide aucune fonctionnalité du moteur de recherche.</p>
 */
class AppTest {
    /** Crée l'instance utilisée par JUnit pour exécuter le test de démarrage. */
    AppTest() {
    }

    /**
     * Exécute une assertion toujours vraie pour vérifier le démarrage de JUnit.
     *
     * <p>Aucune donnée n'est préparée. Le résultat attendu est un test réussi.</p>
     */
    @Test
    void testApp() {
        assertTrue(true);
    }
}
