package com.sorbonne.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sorbonne.automata.Automaton;
import com.sorbonne.regex.DFA;
import com.sorbonne.regex.NFA;
import com.sorbonne.support.AutomatonBuilder;
import com.sorbonne.support.SearchGenerators;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Génère des motifs et des textes avec graines fixes et bornes explicites.
 * Les noms JUnit permettent de reproduire chaque cas ; pas de réduction automatique
 * du contre-exemple. Les oracles sont contains, Pattern.find et une simulation NFA exhaustive.
 */
class NativeSearchPropertyTest {
    /** Crée la suite sans état aléatoire partagé. */
    NativeSearchPropertyTest() {
    }

    /**
     * Vérifie les mots littéraux et les propriétés d'insertion/extraction sur les char UTF-16.
     * @return 400 cas, dont motifs vides, répétitifs, caractères spéciaux et substituts
     */
    @TestFactory
    Stream<DynamicTest> agreesWithLiteralSearchAndInsertionProperties() {
        return IntStream.range(0, 400).mapToObj(index -> {
            long seed = 2210000L + index;
            return DynamicTest.dynamicTest("Motif littéral — graine=" + seed, () -> {
                Random random = new Random(seed);
                String alphabet = index % 2 == 0 ? "ab" : "ab.*|é\n\0😀\uFFFF";
                String text = SearchGenerators.text(random, 40, alphabet);
                String pattern = SearchGenerators.text(random, 12, alphabet);
                NativeSearch.Prepared prepared = NativeSearch.prepare(AutomatonBuilder.literal(pattern));
                assertEquals(text.contains(pattern), prepared.search(text));
                assertEquals(new KMPSearch().search(text, pattern), prepared.search(text));
                assertTrue(prepared.search(text + pattern + text), "Insertion : graine=" + seed);
                // Un motif extrait est toujours présent, même si l'extraction est vide.
                int start = random.nextInt(text.length() + 1);
                String extracted = text.substring(start);
                assertTrue(NativeSearch.prepare(AutomatonBuilder.literal(extracted)).search(text));
                // # est absent de tous les alphabets de génération.
                assertFalse(NativeSearch.prepare(AutomatonBuilder.literal(pattern + "#")).search(text));
            });
        });
    }

    /**
     * Vérifie la recherche de regex contre le moteur Java sur un domaine sémantiquement commun.
     * @return 300 arbres de profondeur au plus 3, avec 16 textes BMP par préparation
     */
    @TestFactory
    Stream<DynamicTest> agreesWithJavaRegexFind() {
        return IntStream.range(0, 300).mapToObj(index -> {
            long seed = 2211000L + index;
            return DynamicTest.dynamicTest("Recherche regex — graine=" + seed, () -> {
                Random random = new Random(seed);
                SearchGenerators.RegexCase example = SearchGenerators.regex(random, 3);
                NativeSearch.Prepared prepared = NativeSearch.prepare(DFA.convert(NFA.buildNFA(example.tree())));
                Pattern oracle = Pattern.compile(example.expression(), Pattern.DOTALL);
                for (int trial = 0; trial < 16; trial++) {
                    String text = SearchGenerators.text(random, 16, "abxé\n\0");
                    assertEquals(oracle.matcher(text).find(), prepared.search(text),
                            "graine=" + seed + ", essai=" + trial + ", regex=" + example.expression() + ", texte=" + text);
                }
            });
        });
    }

    /**
     * Vérifie des graphes qui ne dépendent pas de la construction par expressions régulières.
     * @return 150 NFA quelconques puis déterminisés, avec 12 petits textes chacun
     */
    @TestFactory
    Stream<DynamicTest> agreesWithExhaustiveNfaSubstringOracle() {
        return IntStream.range(0, 150).mapToObj(index -> {
            long seed = 2212000L + index;
            return DynamicTest.dynamicTest("Recherche dans un graphe — graine=" + seed, () -> {
                Random random = new Random(seed);
                Automaton nfa = SearchGenerators.nfa(random);
                NativeSearch.Prepared prepared = NativeSearch.prepare(DFA.convert(nfa));
                for (int trial = 0; trial < 12; trial++) {
                    String text = SearchGenerators.text(random, 8, "abxé\n\0\uFFFF");
                    assertEquals(SearchGenerators.contains(nfa, text), prepared.search(text),
                            "graine=" + seed + ", essai=" + trial + ", texte=" + text);
                }
            });
        });
    }
}
