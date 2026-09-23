package com.sorbonne.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Proprietes reproductibles de KMP sur le sous-ensemble ASCII. */
class KMPSearchPropertyTest {
    private final SearchAlgorithm<String> search = new KMPSearch();
    private static final long BASE_SEED = 20260921L;
    private static final String[] ALPHABETS = {
        "a", "ab", "abc", "abc XYZ012.*|()[]\\\n\t\0\u007f"
    };

    @TestFactory
    @DisplayName("Propriete : meme resultat que String.contains")
    Stream<DynamicTest> agreesWithContains() {
        return generatedCases("accord avec contains", 1_000, 0, random -> {
            String text = randomString(random, 0, 160);
            String pattern = random.nextBoolean() ? substring(random, text) : randomString(random, 0, 180);
            assertEquals(text.contains(pattern), search.search(text, pattern), () -> describe(text, pattern));
        });
    }

    @TestFactory
    @DisplayName("Propriete : un motif insere est present")
    Stream<DynamicTest> findsInsertedPatterns() {
        return generatedCases("insertion", 250, 1_000, random -> {
            String pattern = randomString(random, 1, 40);
            String text = randomString(random, 0, 100) + pattern + randomString(random, 0, 100);
            assertTrue(search.search(text, pattern), () -> describe(text, pattern));
        });
    }

    @TestFactory
    @DisplayName("Propriete : toute sous-chaine extraite est presente")
    Stream<DynamicTest> findsExtractedSubstrings() {
        return generatedCases("extraction", 250, 1_250, random -> {
            String text = randomString(random, 0, 200);
            String pattern = substring(random, text);
            assertTrue(search.search(text, pattern), () -> describe(text, pattern));
        });
    }

    @TestFactory
    @DisplayName("Propriete : un symbole absent empeche toute occurrence")
    Stream<DynamicTest> rejectsPatternsContainingAnAbsentSymbol() {
        return generatedCases("symbole absent", 250, 1_500, random -> {
            String text = randomStringFromAlphabet(random, 0, 200, "abc");
            String pattern = randomStringFromAlphabet(random, 0, 20, "abc")
                    + "#" + randomStringFromAlphabet(random, 0, 20, "abc");
            assertFalse(search.search(text, pattern), () -> describe(text, pattern));
        });
    }

    @TestFactory
    @DisplayName("Propriete : ajouter du contexte preserve une occurrence")
    Stream<DynamicTest> preservesMatchesWhenAddingContext() {
        return generatedCases("ajout de contexte", 250, 1_750, random -> {
            String original = randomString(random, 1, 100);
            int start = random.nextInt(original.length());
            int end = start + 1 + random.nextInt(original.length() - start);
            String pattern = original.substring(start, end);
            String extended = randomString(random, 0, 80) + original + randomString(random, 0, 80);
            assertTrue(search.search(original, pattern), () -> describe(original, pattern));
            assertTrue(search.search(extended, pattern), () -> describe(extended, pattern));
        });
    }

    @Test
    @DisplayName("Exhaustif : 945 couples de petites chaines sur {a, b}")
    void agreesWithContainsForEverySmallBinaryWord() {
        List<String> texts = binaryWordsUpTo(5);
        List<String> patterns = binaryWordsUpTo(3);
        for (String text : texts) {
            for (String pattern : patterns) {
                assertEquals(text.contains(pattern), search.search(text, pattern), () -> describe(text, pattern));
            }
        }
    }

    private Stream<DynamicTest> generatedCases(String name, int count, long seedOffset,
            Consumer<Random> property) {
        return IntStream.range(0, count).mapToObj(index -> {
            long seed = BASE_SEED + seedOffset + index;
            return DynamicTest.dynamicTest(name + " - graine=" + seed,
                    () -> property.accept(new Random(seed)));
        });
    }

    private static String randomString(Random random, int minLength, int maxLength) {
        return randomStringFromAlphabet(random, minLength, maxLength,
                ALPHABETS[random.nextInt(ALPHABETS.length)]);
    }

    private static String randomStringFromAlphabet(Random random, int minLength, int maxLength,
            String alphabet) {
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return result.toString();
    }

    private static String substring(Random random, String text) {
        int start = random.nextInt(text.length() + 1);
        int end = start + random.nextInt(text.length() - start + 1);
        return text.substring(start, end);
    }

    private static List<String> binaryWordsUpTo(int maxLength) {
        List<String> all = new ArrayList<>(List.of(""));
        List<String> level = List.of("");
        for (int length = 1; length <= maxLength; length++) {
            List<String> next = new ArrayList<>();
            for (String prefix : level) {
                next.add(prefix + "a");
                next.add(prefix + "b");
            }
            all.addAll(next);
            level = next;
        }
        return all;
    }

    private static String describe(String text, String pattern) {
        return "texte=" + escaped(text) + ", motif=" + escaped(pattern);
    }

    private static String escaped(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            result.append(String.format("\\x%02x", (int) value.charAt(index)));
        }
        return result.append('"').toString();
    }
}
