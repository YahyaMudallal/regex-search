package com.sorbonne.regex;

import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.search.NativeSearch;
import com.sorbonne.support.AutomatonBuilder;
import com.sorbonne.support.SearchGenerators;

/** Vérifie la minimisation de Hopcroft et la préservation du langage. */
class DFAMHopcroftTest {
    /** Crée les tests sans état partagé. */
    DFAMHopcroftTest() {
    }

    /** Une entrée minimale est copiée plutôt que retournée par référence. */
    @Test
    void returnsIndependentMinimalAutomaton() throws Exception {
        Automaton dfa = DFA.convert(NFA.buildNFA(RegexParser.parse("ab")));
        Automaton minimized = DFAMHopcroft.minimize(dfa);
        assertNotSame(dfa, minimized);
        assertEquals(dfa.getStates().size(), minimized.getStates().size());
        assertSameLanguage(dfa, minimized, "abx", 4);
    }

    /**
     * Deux états possédant exactement le même langage résiduel doivent fusionner.
     */
    @Test
    void mergesEquivalentStates() {
        Automaton dfa = new AutomatonBuilder()
                .state("q0", Status.ENTER)
                .state("q1", Status.INTERMEDIATE)
                .state("q2", Status.INTERMEDIATE)
                .state("qf", Status.FINAL)
                .character("q0", "q1", 'a')
                .character("q0", "q2", 'b')
                .character("q1", "qf", 'a')
                .character("q1", "qf", 'b')
                .character("q2", "qf", 'a')
                .character("q2", "qf", 'b')
                .build();

        Automaton minimized = DFAMHopcroft.minimize(dfa);
        assertEquals(3, minimized.getStates().size());
        assertSameLanguage(dfa, minimized, "abx", 4);
    }

    /**
     * Les états inaccessibles ne doivent pas apparaître dans l'automate minimal.
     */
    @Test
    void removesUnreachableStates() {
        Automaton dfa = new AutomatonBuilder()
                .state("start", Status.ENTER)
                .state("accept", Status.FINAL)
                .state("unreachable", Status.FINAL)
                .character("start", "accept", 'a')
                .character("unreachable", "unreachable", 'a')
                .build();

        Automaton minimized = DFAMHopcroft.minimize(dfa);
        assertEquals(2, minimized.getStates().size());
        assertSameLanguage(dfa, minimized, "ab", 3);
    }

    /**
     * Les classes ANY/exclusions peuvent disparaître lorsqu'elles deviennent
     * équivalentes.
     */
    @Test
    void preservesWildcardClassesAndCompactsTransitions() {
        Automaton dfa = new AutomatonBuilder()
                .state("start", Status.ENTER)
                .state("left", Status.FINAL)
                .state("right", Status.FINAL)
                .character("start", "left", 'a')
                .anyExcept("start", "right", Set.of('a'))
                .build();

        Automaton minimized = DFAMHopcroft.minimize(dfa);
        assertEquals(2, minimized.getStates().size());
        State start = minimized.getInitialState();
        assertEquals(1, minimized.getOutgoingTransitions(start).size(),
                "les deux classes deviennent un unique arc ANY");
        assertSameLanguage(dfa, minimized, "abxé\uFFFF", 2);
    }

    /**
     * Les transitions absentes sont traitées comme un puits implicite sans
     * l'imposer en sortie.
     */
    @Test
    void keepsPartialDfaSemantics() {
        Automaton dfa = AutomatonBuilder.literal("ab");
        Automaton minimized = DFAMHopcroft.minimize(dfa);
        assertEquals(3, minimized.getStates().size());
        assertSameLanguage(dfa, minimized, "abx", 4);
    }

    /** Un automate sans état initial n'a pas de langage d'entrée bien défini. */
    @Test
    void rejectsMissingInitialState() {
        Automaton dfa = new AutomatonBuilder().state("q", Status.FINAL).build();
        assertThrows(IllegalArgumentException.class, () -> DFAMHopcroft.minimize(dfa));
    }

    /**
     * Hopcroft s'applique uniquement à un automate déjà déterministe et sans
     * epsilon.
     */
    @Test
    void rejectsEpsilonAndOverlappingTransitions() {
        Automaton epsilon = new AutomatonBuilder()
                .state("q0", Status.ENTER)
                .state("q1", Status.FINAL)
                .epsilon("q0", "q1")
                .build();
        assertThrows(IllegalArgumentException.class, () -> DFAMHopcroft.minimize(epsilon));

        Automaton overlap = new AutomatonBuilder()
                .state("q0", Status.ENTER)
                .state("q1", Status.FINAL)
                .state("q2", Status.FINAL)
                .character("q0", "q1", 'a')
                .any("q0", "q2")
                .build();
        assertThrows(IllegalArgumentException.class, () -> DFAMHopcroft.minimize(overlap));
    }

    /** Signale explicitement une entrée nulle dès l'appel. */
    @Test
    @SuppressWarnings({ "ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored" })
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> DFAMHopcroft.minimize(null));
    }

    /**
     * Property test reproductible : langage du DFA classique avant/après
     * minimisation.
     * 
     * @return 250 regex indépendantes, 30 mots chacune
     */
    @TestFactory
    Stream<DynamicTest> preservesGeneratedDfaLanguages() {
        return IntStream.range(0, 250).mapToObj(index -> {
            long seed = 2309000L + index;
            return DynamicTest.dynamicTest("Hopcroft langage — graine=" + seed, () -> {
                Random random = new Random(seed);
                SearchGenerators.RegexCase example = SearchGenerators.regex(random, 4);
                Automaton dfa = DFA.convert(NFA.buildNFA(example.tree()));
                Automaton minimized = DFAMHopcroft.minimize(dfa);
                assertTrue(minimized.getStates().size() <= dfa.getStates().size());
                DFATest.assertDeterministic(minimized);
                for (int trial = 0; trial < 30; trial++) {
                    String word = SearchGenerators.text(random, 10, "abxé\n\0\uFFFF");
                    assertEquals(SearchGenerators.accepts(dfa, word), SearchGenerators.accepts(minimized, word),
                            "graine=" + seed + ", regex=" + example.expression() + ", mot=" + word);
                }
                assertEquals(minimized.getStates().size(), DFAMHopcroft.minimize(minimized).getStates().size(),
                        "la minimisation doit être idempotente sur le nombre d'états");
            });
        });
    }

    /**
     * Le DFA spécialisé est consommé avec arrêt dès le premier état final ; ce
     * contrat
     * doit lui aussi être invariant après minimisation.
     * 
     * @return 200 regex, 30 textes chacune
     */
    @TestFactory
    Stream<DynamicTest> preservesSearchDfaBehavior() {
        return IntStream.range(0, 200).mapToObj(index -> {
            long seed = 2310000L + index;
            return DynamicTest.dynamicTest("Hopcroft recherche — graine=" + seed, () -> {
                Random random = new Random(seed);
                SearchGenerators.RegexCase example = SearchGenerators.regex(random, 4);
                Automaton searchDfa = DFA.forSearch(NFA.buildNFA(example.tree()));
                Automaton minimized = DFAMHopcroft.minimize(searchDfa);
                NativeSearch.Prepared before = NativeSearch.fromSearchDfa(searchDfa);
                NativeSearch.Prepared after = NativeSearch.fromSearchDfa(minimized);
                for (int trial = 0; trial < 30; trial++) {
                    String text = SearchGenerators.text(random, 14, "abxé\n\0\uFFFF");
                    assertEquals(before.search(text), after.search(text),
                            "graine=" + seed + ", regex=" + example.expression() + ", texte=" + text);
                }
            });
        });
    }

    /** Compare exhaustivement deux petits DFA sur tous les mots d'un alphabet. */
    private static void assertSameLanguage(Automaton expected, Automaton actual, String alphabet, int maxLength) {
        for (String word : SearchGenerators.words(alphabet, maxLength)) {
            assertEquals(SearchGenerators.accepts(expected, word), SearchGenerators.accepts(actual, word),
                    "mot=" + word);
        }
    }
}
