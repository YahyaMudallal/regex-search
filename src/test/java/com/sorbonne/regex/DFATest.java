package com.sorbonne.regex;

import static org.junit.jupiter.api.Assertions.*;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;
import com.sorbonne.support.AutomatonBuilder;
import com.sorbonne.support.SearchGenerators;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Cas élémentaires et régressions de déterminisation, sans utiliser NativeSearch comme oracle. */
class DFATest {
    /** Crée une suite de tests indépendante. */
    DFATest() {
    }

    /**
     * Vérifie les DFA en chaîne, y compris les chaînes vides et les bornes de l'alphabet 8 bits.
     * @param word mot littéral reconnu par le graphe construit
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "a", "ab", "aab", ".*", "\u0080", "\u00ff"})
    void preservesLiteralLanguages(String word) {
        Automaton dfa = DFA.convert(AutomatonBuilder.literal(word));
        assertTrue(SearchGenerators.accepts(dfa, word));
        assertFalse(SearchGenerators.accepts(dfa, word + "x"));
        assertFalse(SearchGenerators.accepts(dfa, "x" + word));
        assertDeterministic(dfa);
    }

    /** Vérifie une fermeture initiale finale et des cycles ε sans boucle infinie. */
    @Test
    void followsEpsilonCyclesAndTrailingEpsilon() {
        Automaton nfa = new AutomatonBuilder().state("s", Status.ENTER)
                .state("m", Status.INTERMEDIATE).state("f", Status.FINAL)
                .epsilon("s", "m").epsilon("m", "s").epsilon("m", "f")
                .character("m", "m", 'a').build();
        Automaton dfa = DFA.convert(nfa);
        assertEquals(Status.ENTER_FINAL, dfa.getInitialState().getStatus());
        assertTrue(SearchGenerators.accepts(dfa, ""));
        assertTrue(SearchGenerators.accepts(dfa, "aaaa"));
        assertFalse(SearchGenerators.accepts(dfa, "b"));
        assertDeterministic(dfa);
    }

    /** Vérifie que les transitions a et ANY doivent contribuer ensemble au déplacement sur a. */
    @Test
    void mergesWildcardAndLiteralBranchesWithoutOverlappingLabels() {
        // Langage ac | .b : sur a, les deux chemins doivent être conservés.
        Automaton nfa = new AutomatonBuilder().state("s", Status.ENTER)
                .state("literal", Status.INTERMEDIATE).state("wildcard", Status.INTERMEDIATE)
                .state("f", Status.FINAL).character("s", "literal", 'a')
                .any("s", "wildcard").character("literal", "f", 'c')
                .character("wildcard", "f", 'b').build();
        Automaton dfa = DFA.convert(nfa);
        for (String word : List.of("ab", "ac", "xb", "\u00ffb", "\nb", "\0b", "\u0080b")) {
            assertTrue(SearchGenerators.accepts(dfa, word), word);
        }
        for (String word : List.of("", "a", "xc", "abc")) {
            assertFalse(SearchGenerators.accepts(dfa, word), word);
        }
        // Les 256 valeurs de l'alphabet doivent correspondre à exactement un arc initial.
        List<Transition> outgoing = dfa.getOutgoingTransitions(dfa.getInitialState());
        for (int code = 0; code < 256; code++) {
            int count = 0;
            for (Transition transition : outgoing) {
                if (transition.matches((char) code)) {
                    count++;
                }
            }
            assertEquals(1, count, "Chevauchement ou trou pour char=" + code);
        }
        assertTrue(outgoing.size() <= 4, "Le point ne doit pas être développé en milliers d'arcs");
    }

    /** Vérifie les arcs parallèles, plusieurs états finaux et des noms d'états identiques. */
    @Test
    void mergesDuplicateAndParallelEdges() {
        Automaton nfa = new AutomatonBuilder().state("s", Status.ENTER)
                .state("f", Status.FINAL).state("g", Status.FINAL)
                .character("s", "f", 'a').character("s", "g", 'a')
                .character("s", "f", 'a').build();
        nfa.getStates().forEach(state -> state.setLabel("same-name"));
        Automaton dfa = DFA.convert(nfa);
        assertEquals(2, dfa.getStates().size());
        assertEquals(1, dfa.getTransitions().size());
        assertTrue(SearchGenerators.accepts(dfa, "a"));
        assertDeterministic(dfa);
    }

    /** Vérifie que les états inaccessibles et l'état puits implicite ne sont pas ajoutés. */
    @Test
    void keepsOnlyReachableSubsetsAndAllowsEmptyLanguage() {
        Automaton nfa = new AutomatonBuilder().state("s", Status.ENTER)
                .state("unreachable", Status.FINAL).character("unreachable", "unreachable", 'a').build();
        Automaton dfa = DFA.convert(nfa);
        assertEquals(1, dfa.getStates().size());
        assertTrue(dfa.getTransitions().isEmpty());
        assertTrue(dfa.getFinalStates().isEmpty());
        assertFalse(SearchGenerators.accepts(dfa, ""));
        assertFalse(SearchGenerators.accepts(dfa, "a"));
    }

    /** Vérifie les exclusions existantes, une seconde conversion et l'indépendance des graphes. */
    @Test
    void preservesRestrictedWildcardsAndDoesNotMutateInput() {
        Set<Character> excluded = new HashSet<>(Set.of('a', '\u00e9'));
        Automaton nfa = new AutomatonBuilder().state("s", Status.ENTER).state("f", Status.FINAL)
                .anyExcept("s", "f", excluded).build();
        excluded.clear(); // La transition doit avoir conservé une copie de la configuration.
        Automaton first = DFA.convert(nfa);
        Automaton second = DFA.convert(first);
        for (String word : List.of("", "a", "\u00e9", "b", "\0", "\u00ff", "ab")) {
            assertEquals(SearchGenerators.accepts(nfa, word), SearchGenerators.accepts(second, word), word);
        }
        assertEquals(Status.ENTER, nfa.getInitialState().getStatus());
        assertEquals(2, nfa.getStates().size());
        assertEquals(1, nfa.getTransitions().size());
        assertTrue(first.getStates().stream().noneMatch(nfa.getStates()::contains));
        assertDeterministic(second);
    }

    /** Vérifie les erreurs de configuration avant toute conversion. */
    @Test
    @SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
    void rejectsInvalidInitialConfigurations() {
        assertThrows(NullPointerException.class, () -> DFA.convert(null));
        assertThrows(IllegalArgumentException.class, () -> DFA.convert(new Automaton()));
        Automaton multiple = new AutomatonBuilder().state("a", Status.ENTER)
                .state("b", Status.ENTER_FINAL).build();
        assertThrows(IllegalStateException.class, () -> DFA.convert(multiple));
    }

    /**
     * Vérifie l'absence d'ε et de chevauchement sur chaque classe possible de caractères.
     * @param dfa graphe à contrôler
     */
    static void assertDeterministic(Automaton dfa) {
        assertNotNull(dfa.getInitialState());
        Set<Character> representatives = new HashSet<>();
        for (Transition transition : dfa.getTransitions()) {
            assertFalse(transition.isEpsilon());
            if (transition.getSymbol() != null) {
                representatives.add(transition.getSymbol());
            }
            representatives.addAll(transition.getExcludedSymbols());
        }
        for (int code = 0; code < 256; code++) {
            if (representatives.add((char) code)) {
                break; // Un représentant suffit pour tous les caractères non mentionnés.
            }
        }
        for (State state : dfa.getStates()) {
            for (char symbol : representatives) {
                long matches = dfa.getOutgoingTransitions(state).stream().filter(t -> t.matches(symbol)).count();
                assertTrue(matches <= 1, "Arcs concurrents pour " + state.getLabel() + ", char=" + (int) symbol);
            }
        }
    }
}
