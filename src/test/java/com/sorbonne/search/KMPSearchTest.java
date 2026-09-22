package com.sorbonne.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import java.util.Random;
import com.sorbonne.support.SearchGenerators;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Vérifie KMP avec des exemples simples, intermédiaires et difficiles.
 *
 * <p>Les résultats attendus sont écrits explicitement. Les tests complémentaires
 * de {@link KMPSearchPropertyTest} utilisent des chaînes générées automatiquement.</p>
 */
class KMPSearchTest {
    /** Moteur utilisé par les scénarios ; il ne conserve pas les recherches précédentes. */
    private final SearchAlgorithm<String> search = new KMPSearch();

    /** Crée une instance de test avec son moteur KMP. */
    KMPSearchTest() {
    }

    // ====================================================================
    // NIVEAU 1 — EXEMPLES SIMPLES ET CONTRAT
    // ====================================================================

    /** Vérifie l'exemple fourni : le mot « monsieur » apparaît au milieu de la phrase. */
    @Test
    @DisplayName("Simple : trouve monsieur dans la phrase d'exemple")
    void findsWordInExampleSentence() {
        // Phrase dans laquelle rechercher un mot complet.
        String texte = "bonjour monsieur bienvenue";
        // Motif littéral attendu dans cette phrase.
        String motif = "monsieur";
        assertTrue(search.search(texte, motif));
    }

    /**
     * Vérifie les positions usuelles, les absences et les chaînes vides.
     *
     * @param scenario description lisible du cas dans le rapport JUnit
     * @param text texte dans lequel chercher
     * @param pattern motif littéral à chercher
     * @param expected résultat attendu, défini indépendamment de KMP
     */
    @ParameterizedTest(name = "Simple : {0}")
    @MethodSource("simpleCases")
    void handlesSimpleCases(String scenario, String text, String pattern, boolean expected) {
        assertEquals(expected, search.search(text, pattern), scenario);
    }

    /**
     * Fournit les cas élémentaires, dont les trois combinaisons de chaînes vides.
     *
     * @return scénario, texte, motif et résultat attendu pour chaque exemple
     */
    private static Stream<Arguments> simpleCases() {
        return Stream.of(
                Arguments.of("début du texte", "bonjour monsieur", "bonjour", true),
                Arguments.of("fin du texte", "bonjour monsieur", "monsieur", true),
                Arguments.of("texte entier", "bonjour", "bonjour", true),
                Arguments.of("mot absent", "bonjour monsieur", "madame", false),
                Arguments.of("motif plus long", "bon", "bonjour", false),
                Arguments.of("un caractère présent", "abc", "b", true),
                Arguments.of("un caractère absent", "abc", "z", false),
                Arguments.of("comparaison sensible à la casse", "Bonjour", "bonjour", false),
                Arguments.of("motif vide", "bonjour", "", true),
                Arguments.of("texte vide", "", "a", false),
                Arguments.of("texte et motif vides", "", "", true));
    }

    /** Vérifie que null est refusé, même si l'autre argument est vide ou également nul. */
    @Test
    @DisplayName("Simple : refuse les arguments nuls")
    @SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> search.search(null, "a"));
        assertThrows(NullPointerException.class, () -> search.search("abc", null));
        assertThrows(NullPointerException.class, () -> search.search(null, ""));
        assertThrows(NullPointerException.class, () -> search.search("", null));
        assertThrows(NullPointerException.class, () -> search.search(null, null));
    }

    // ====================================================================
    // NIVEAU 2 — RÉPÉTITIONS, REPLIS LPS ET CARACTÈRES PARTICULIERS
    // ====================================================================

    /**
     * Vérifie les reprises après correspondance partielle et la comparaison littérale.
     *
     * @param scenario description du comportement attendu
     * @param text texte à parcourir
     * @param pattern motif à chercher
     * @param expected résultat attendu pour cet exemple
     */
    @ParameterizedTest(name = "Intermédiaire : {0}")
    @MethodSource("intermediateCases")
    void handlesIntermediateCases(String scenario, String text, String pattern, boolean expected) {
        assertEquals(expected, search.search(text, pattern), scenario);
    }

    /**
     * Fournit des motifs répétés et des caractères non assimilables à de simples mots ASCII.
     *
     * @return exemples avec leur résultat attendu
     */
    private static Stream<Arguments> intermediateCases() {
        return Stream.of(
                Arguments.of("plusieurs replis LPS", "abcxabcdabxabcdabcdabcy", "abcdabcy", true),
                Arguments.of("préfixes qui se chevauchent", "ababababac", "ababac", true),
                Arguments.of("répétition suivie du caractère attendu", "aaaaaab", "aaaab", true),
                Arguments.of("repli vers zéro puis reprise", "aaacaaab", "aaab", true),
                Arguments.of("échec après un long préfixe", "abababababa", "ababac", false),
                Arguments.of("occurrence incomplète en fin de texte", "xyzababa", "ababac", false),
                Arguments.of("symboles regex littéraux", "avant .*|() après", ".*|()", true),
                Arguments.of("le point n'est pas universel", "abc", "a.c", false),
                Arguments.of("l'étoile n'est pas un opérateur", "aaaa", "a*", false),
                Arguments.of("espaces, tabulation et saut de ligne", "a\t b\nc", "\t b\n", true),
                Arguments.of("caractère nul dans une chaîne", "ab\0cd", "\0c", true),
                Arguments.of("accents et écriture non latine", "été 東京 hiver", "東京", true),
                Arguments.of("emoji représenté par deux unités UTF-16", "a😀b", "😀", true),
                Arguments.of("comparaison des unités UTF-16", "a😀b", "\uD83D", true),
                Arguments.of("pas de normalisation des accents", "café", "cafe\u0301", false));
    }

    /** Vérifie que les appels successifs, y compris après une erreur, restent indépendants. */
    @Test
    @DisplayName("Intermédiaire : réutilise la même instance sans conserver d'état")
    @SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
    void reusesTheSameInstance() {
        assertTrue(search.search("ababac", "abac"));
        assertFalse(search.search("aaaa", "b"));
        assertTrue(search.search("", ""));
        assertThrows(NullPointerException.class, () -> search.search(null, "a"));
        assertTrue(search.search("bonjour monsieur", "monsieur"));
    }

    /**
     * Réutilise les tables préparées sur des textes générés et compare à String.contains.
     * Les préfixes répétés sollicitent les replis ; les appels ne partagent aucun indice.
     */
    @Test
    void reusesPreparedPatternsOnGeneratedTexts() {
        Random random = new Random(20_260_922L);
        for (String pattern : new String[] {"", "a", "ababac", "aaaaab", "é.", "😀"}) {
            KMPSearch.Prepared prepared = KMPSearch.prepare(pattern);
            for (int i = 0; i < 100; i++) {
                String text = SearchGenerators.text(random, 120, "aaaabbcé.");
                assertEquals(text.contains(pattern), prepared.search(text));
                assertTrue(prepared.search(text + pattern));
                assertEquals(pattern.isEmpty(), prepared.search(""));
            }
        }
    }

    /** Vérifie les arguments nuls et la réutilisation d'un moteur préparé après une erreur. */
    @Test
    @SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
    void preparedSearchRejectsNull() {
        assertThrows(NullPointerException.class, () -> KMPSearch.prepare(null));
        KMPSearch.Prepared prepared = KMPSearch.prepare("ab");
        assertThrows(NullPointerException.class, () -> prepared.search(null));
        assertTrue(prepared.search("xxab"));
        assertFalse(prepared.search("a"));
        assertFalse(prepared.search("b"));
    }

    // ====================================================================
    // NIVEAU 3 — GRAND TEXTE AVEC DE NOMBREUX PRÉFIXES COMMUNS
    // ====================================================================

    /**
     * Vérifie un grand nombre de replis avec un motif long, présent puis absent.
     *
     * <p>Le dernier caractère distingue les deux motifs. Le test vérifie le résultat,
     * sans seuil de durée dépendant de la machine ni prétention de mesure de performance.</p>
     */
    @Test
    @DisplayName("Poussé : recherche un motif long dans 200 001 caractères répétitifs")
    void searchesLongRepeatedPrefixes() {
        // Une longue répétition suivie d'un caractère distinct.
        String text = "a".repeat(200_000) + "b";
        // Préfixe commun aux motifs présent et absent.
        String prefix = "a".repeat(10_000);
        assertTrue(search.search(text, prefix + "b"));
        assertFalse(search.search(text, prefix + "c"));
        assertFalse(search.search(text, text + "b"));
    }
}
