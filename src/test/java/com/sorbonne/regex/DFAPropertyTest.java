package com.sorbonne.regex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sorbonne.automata.Automaton;
import com.sorbonne.support.SearchGenerators;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Vérifie des langages générés avec deux oracles indépendants de la déterminisation. */
class DFAPropertyTest {
    /** Crée les tests, sans générateur partagé entre leurs exécutions. */
    DFAPropertyTest() {
    }

    /**
     * Compare des graphes arbitraires au simulateur NFA à point fixe.
     * @return 300 cas reproductibles, avec 20 mots chacun
     */
    @TestFactory
    Stream<DynamicTest> preservesGeneratedNfaLanguages() {
        return IntStream.range(0, 300).mapToObj(index -> {
            long seed = 2209000L + index;
            return DynamicTest.dynamicTest("NFA quelconque — graine=" + seed, () -> {
                Random random = new Random(seed);
                Automaton nfa = SearchGenerators.nfa(random);
                Automaton dfa = DFA.convert(nfa);
                DFATest.assertDeterministic(dfa);
                for (int trial = 0; trial < 20; trial++) {
                    String word = SearchGenerators.text(random, 8, "abxé\n\0\uFFFF");
                    assertEquals(SearchGenerators.accepts(nfa, word), SearchGenerators.accepts(dfa, word),
                            "graine=" + seed + ", essai=" + trial + ", mot=" + word);
                }
            });
        });
    }

    /**
     * Compare NFA puis DFA au moteur Java sur le sous-ensemble regex commun.
     * Les textes BMP et DOTALL alignent la sémantique du point avec nos char.
     * @return 250 expressions bornées, avec 20 textes chacune
     */
    @TestFactory
    Stream<DynamicTest> agreesWithJavaRegexForGeneratedExpressions() {
        return IntStream.range(0, 250).mapToObj(index -> {
            long seed = 2209500L + index;
            return DynamicTest.dynamicTest("Regex générée — graine=" + seed, () -> {
                Random random = new Random(seed);
                SearchGenerators.RegexCase example = SearchGenerators.regex(random, 3);
                Automaton nfa = NFA.buildNFA(example.tree());
                Automaton dfa = DFA.convert(nfa);
                Pattern reference = Pattern.compile(example.expression(), Pattern.DOTALL);
                DFATest.assertDeterministic(dfa);
                for (int trial = 0; trial < 20; trial++) {
                    String word = SearchGenerators.text(random, 10, "abxé\n\0");
                    boolean expected = reference.matcher(word).matches();
                    String message = "graine=" + seed + ", essai=" + trial + ", regex=" + example.expression();
                    assertEquals(expected, SearchGenerators.accepts(nfa, word), message + " (NFA)");
                    assertEquals(expected, SearchGenerators.accepts(dfa, word), message + " (DFA)");
                }
            });
        });
    }
}
