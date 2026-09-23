package com.sorbonne.regex;

import static org.junit.jupiter.api.Assertions.*;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.Status;
import com.sorbonne.support.AutomatonBuilder;
import com.sorbonne.support.SearchGenerators;
import org.junit.jupiter.api.Test;

/**
 * Vérifie la minimisation d'un automate déterministe par l'algorithme de Moore.
 *
 * <p>Les scénarios couvrent le contrat de la méthode provisoire, la fusion
 * d'états équivalents, les transitions absentes et la conservation du langage
 * reconnu.</p>
 */
class DFAMMooreTest {
    /** Crée une suite de tests indépendante. */
    DFAMMooreTest() {
    }

    /**
     * Vérifie que Moore construit un automate réellement minimisé.
     *
     * <p>Scénario : le DFA de {@code a|bc*} contient deux états finaux
     * équivalents après la lecture de {@code b}. Résultat attendu : ces états
     * sont fusionnés dans un nouvel automate de trois états.</p>
     */
    @Test
    void minimizesEquivalentStatesInRegexDfa() throws Exception {
        Automaton dfa = DFA.convert(NFA.buildNFA(RegexParser.parse("a|bc*")));
        Automaton minimized = DFAMMoore.minimize(dfa, false);

        assertNotSame(dfa, minimized);
        assertEquals(3, minimized.getStates().size());
        assertEquals(2, minimized.getFinalStates().size());
        for (String word : SearchGenerators.words("abc", 3)) {
            assertEquals(
                    SearchGenerators.accepts(dfa, word),
                    SearchGenerators.accepts(minimized, word),
                    word);
        }
    }

    /**
     * Vérifie le rejet d'un automate nul par le point d'intégration.
     *
     * <p>Résultat attendu : une {@link NullPointerException} est levée dès
     * l'appel.</p>
     */
    @Test
    @SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
    void rejectsNullAtIntegrationPoint() {
        assertThrows(NullPointerException.class, () -> DFAMMoore.minimize(null, false));
    }

    /**
     * Vérifie la fusion de deux états ayant le même comportement futur.
     *
     * <p>Scénario : les états {@code a} et {@code b} atteignent tous deux
     * l'état final sur {@code x}. Résultat attendu : Moore les regroupe dans
     * un seul état et le langage reconnu reste inchangé.</p>
     */
    @Test
    void mergesEquivalentStates() {
        Automaton dfa = new AutomatonBuilder()
                .state("s", Status.ENTER)
                .state("a", Status.INTERMEDIATE)
                .state("b", Status.INTERMEDIATE)
                .state("f", Status.FINAL)
                .character("s", "a", 'a')
                .character("s", "b", 'b')
                .character("a", "f", 'x')
                .character("b", "f", 'x')
                .build();

        Automaton minimized = DFAMMoore.minimize(dfa, false);

        assertEquals(3, minimized.getStates().size());
        assertEquals(1, minimized.getFinalStates().size());
        assertTrue(SearchGenerators.accepts(minimized, "ax"));
        assertTrue(SearchGenerators.accepts(minimized, "bx"));
        assertFalse(SearchGenerators.accepts(minimized, "a"));
        assertFalse(SearchGenerators.accepts(minimized, "abx"));
        assertTrue(minimized.getStates().stream()
                .anyMatch(state -> state.getLabel().contains("a") && state.getLabel().contains("b")));
        DFATest.assertDeterministic(minimized);
    }

    /**
     * Vérifie qu'une transition absente est traitée par un puits virtuel.
     *
     * <p>Scénario : le DFA n'accepte que {@code "a"} et ne possède aucune
     * transition sortante depuis son état final. Résultat attendu : le puits
     * utilisé par Moore n'est pas exposé dans l'automate final.</p>
     */
    @Test
    void omitsSinkOnlyStateForPartialDfa() {
        Automaton dfa = AutomatonBuilder.literal("a");

        Automaton minimized = DFAMMoore.minimize(dfa, false);

        assertEquals(2, minimized.getStates().size());
        assertEquals(1, minimized.getFinalStates().size());
        assertTrue(minimized.getTransitions().stream().noneMatch(t -> t.isEpsilon()));
        assertTrue(SearchGenerators.accepts(minimized, "a"));
        assertFalse(SearchGenerators.accepts(minimized, ""));
        assertFalse(SearchGenerators.accepts(minimized, "aa"));
    }

    /**
     * Vérifie la conservation du langage sur tous les petits mots ASCII.
     *
     * <p>Scénario : un DFA issu de {@code a|bc*} est minimisé puis comparé au
     * DFA original. Résultat attendu : les deux automates donnent la même
     * réponse pour chaque mot de longueur au plus trois.</p>
     */
    @Test
    void preservesLanguageForSmallAsciiWords() throws Exception {
        Automaton dfa = DFA.convert(NFA.buildNFA(RegexParser.parse("a|bc*")));
        Automaton minimized = DFAMMoore.minimize(dfa, false);

        for (String word : SearchGenerators.words("abc", 3)) {
            assertEquals(
                    SearchGenerators.accepts(dfa, word),
                    SearchGenerators.accepts(minimized, word),
                    word);
        }
    }

    /**
     * Vérifie qu'un automate sans état initial est rejeté.
     *
     * <p>Résultat attendu : une {@link NullPointerException} est levée avant
     * toute construction de l'automate minimisé.</p>
     */
    @Test
    void rejectsDfaWithoutInitialState() {
        assertThrows(
                NullPointerException.class,
                () -> DFAMMoore.minimize(new Automaton(), false));
    }
}
