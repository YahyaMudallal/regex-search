package com.sorbonne.search;

import static org.junit.jupiter.api.Assertions.*;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.Status;
import com.sorbonne.regex.DFA;
import com.sorbonne.regex.NFA;
import com.sorbonne.regex.RegexParser;
import com.sorbonne.support.AutomatonBuilder;
import com.sorbonne.support.SearchGenerators;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests de recherche de sous-chaînes, validation des DFA et réutilisation d'une préparation. */
class NativeSearchTest {
    /** Contrat partagé avec KMP, mais spécialisé pour un motif automate. */
    private final SearchAlgorithm<Automaton> search = new NativeSearch();

    /** Crée le moteur sans préparer de motif commun aux scénarios. */
    NativeSearchTest() {
    }

    /**
     * Vérifie les occurrences usuelles, chevauchements et langages acceptant le mot vide.
     * @param expression expression régulière du projet
     * @param text texte où chercher
     * @param expected résultat explicite attendu
     * @throws Exception si un exemple valide ne peut pas être analysé
     */
    @ParameterizedTest(name = "regex={0}, texte={1}, attendu={2}")
    @MethodSource("examples")
    void searchesExamples(String expression, String text, boolean expected) throws Exception {
        Automaton dfa = DFA.convert(NFA.buildNFA(RegexParser.parse(expression)));
        assertEquals(expected, search.search(text, dfa));
        assertEquals(expected, NativeSearch.prepare(dfa).search(text));
    }

    /**
     * Fournit les exemples avec leur résultat attendu explicite.
     * @return cas simples et régressions des occurrences précédemment perdues
     */
    private static Stream<Arguments> examples() {
        return Stream.of(
                Arguments.of("monsieur", "bonjour monsieur bienvenue", true),
                Arguments.of("monsieur", "bonjour madame", false),
                Arguments.of("ab", "abx", true),
                Arguments.of("ab", "xxab", true),
                Arguments.of("ab", "aab", true),
                Arguments.of("aab", "aaab", true),
                Arguments.of("ababac", "ababababac", true),
                Arguments.of("ab", "acb", false),
                Arguments.of("ab", "a", false),
                Arguments.of("ab", "", false),
                Arguments.of("a*", "", true),
                Arguments.of("(ab)*", "a", true),
                Arguments.of("(ab)*", "xyz", true),
                Arguments.of("ac|.b", "ac", true),
                Arguments.of("ac|.b", "ab", true),
                Arguments.of("ac|.b", "xb", true),
                Arguments.of("ac|.b", "xc", false),
                Arguments.of("a.b", "xa\nbx", true),
                Arguments.of("a\\.b", "a.b", true),
                Arguments.of("a\\.b", "axb", false),
                Arguments.of("a|bc*", "xxxbcxxx", true),
                Arguments.of("a|bc*", "xxx", false),
                Arguments.of("Bonjour", "bonjour", false));
    }

    /** Vérifie que les deux implémentations partagent l'opération, sans confondre le type du motif. */
    @Test
    void sharesTypedContractWithKmp() {
        SearchAlgorithm<String> literalSearch = new KMPSearch();
        Automaton dfa = AutomatonBuilder.literal("monsieur");
        String text = "bonjour monsieur bienvenue";
        assertTrue(literalSearch.search(text, "monsieur"));
        assertTrue(search.search(text, dfa));
        assertTrue(search.search("", AutomatonBuilder.literal("")));
    }

    /** Vérifie les bornes de l'alphabet 8 bits et quelques symboles de ponctuation. */
    @Test
    void handlesAllByteValuedLiteralCharacters() {
        for (String pattern : List.of("\0", "\u00ff", "\u0080", ".*|()")) {
            Automaton dfa = AutomatonBuilder.literal(pattern);
            assertTrue(search.search("prefix" + pattern + "suffix", dfa), pattern);
            assertFalse(search.search("abc", dfa), pattern);
        }
        Automaton outside = AutomatonBuilder.literal("\u0100");
        assertThrows(IllegalArgumentException.class, () -> NativeSearch.prepare(outside));
    }

    /** Vérifie l'absence de mot accepté et les classes complémentaires définies manuellement. */
    @Test
    void supportsEmptyLanguageAndRestrictedWildcards() {
        Automaton empty = new AutomatonBuilder().state("s", Status.ENTER).build();
        assertFalse(search.search("", empty));
        assertFalse(search.search("abc", empty));
        Automaton restricted = new AutomatonBuilder().state("s", Status.ENTER)
                .state("f", Status.FINAL).anyExcept("s", "f", Set.of('a', '\n')).build();
        assertFalse(search.search("aaa\na", restricted));
        assertTrue(NativeSearch.prepare(restricted).search(new byte[] {'a', 'a', 'a', (byte) 0xff}));
        assertTrue(search.search("aaa\0", restricted));
    }

    /** Vérifie qu'une préparation capture le langage initial et reste réutilisable sans mutation partagée. */
    @Test
    void snapshotsInputAndSupportsIndependentConcurrentCalls() {
        Automaton original = AutomatonBuilder.literal("ab");
        NativeSearch.Prepared prepared = NativeSearch.prepare(original);
        assertEquals(Status.ENTER, original.getInitialState().getStatus());
        assertEquals(1, original.getFinalStates().size());
        assertEquals(2, original.getTransitions().size());
        original.getStates().forEach(state -> state.setStatus(Status.INTERMEDIATE));
        // L'entrée a perdu ses statuts ; la copie préparée doit rester valide.
        assertTrue(prepared.search("aab"));
        assertFalse(prepared.search("aaa"));
        IntStream.range(0, 100).parallel().forEach(index -> {
            assertEquals(index % 2 == 0, prepared.search(index % 2 == 0 ? "xxab" : "xxaa"));
        });
    }

    /** Vérifie explicitement le rejet des entrées invalides, sans ignorer ni écraser d'arcs. */
    @Test
    @SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
    void rejectsNullAndMalformedDfas() {
        Automaton literal = AutomatonBuilder.literal("a");
        assertThrows(NullPointerException.class, () -> search.search(null, literal));
        assertThrows(NullPointerException.class, () -> search.search("", null));
        assertThrows(NullPointerException.class, () -> NativeSearch.prepare(null));
        assertThrows(NullPointerException.class, () -> NativeSearch.prepare(literal).search((String) null));
        assertThrows(IllegalArgumentException.class, () -> NativeSearch.prepare(new Automaton()));
        Automaton multipleInitials = new AutomatonBuilder().state("s", Status.ENTER)
                .state("f", Status.ENTER_FINAL).build();
        assertThrows(IllegalStateException.class, () -> NativeSearch.prepare(multipleInitials));
        AutomatonBuilder epsilon = baseBuilder().epsilon("s", "f");
        AutomatonBuilder duplicate = baseBuilder().character("s", "f", 'a').character("s", "f", 'a');
        AutomatonBuilder overlap = baseBuilder().character("s", "f", 'a').any("s", "f");
        AutomatonBuilder twoWildcards = baseBuilder().any("s", "f").any("s", "f");
        for (AutomatonBuilder invalid : List.of(epsilon, duplicate, overlap, twoWildcards)) {
            assertThrows(IllegalArgumentException.class, () -> NativeSearch.prepare(invalid.build()));
        }
    }

    /**
     * Déclare les états partagés par la construction des scénarios invalides.
     * @return builder commun aux scénarios d'arcs incorrects, avec deux états distincts
     */
    private static AutomatonBuilder baseBuilder() {
        return new AutomatonBuilder().state("s", Status.ENTER).state("f", Status.FINAL);
    }

    /**
     * Compare exhaustivement les petits textes à find(), sans confondre reconnaissance complète et occurrence.
     * @param expression motif du sous-ensemble commun aux deux moteurs
     * @throws Exception si le parseur échoue sur un exemple valide
     */
    @ParameterizedTest
    @ValueSource(strings = {"ab", "aab", "a*", "(ab)*", "ac|.b", "(a|b)*c", "a.b", ".*ab", "a|bc*"})
    void checksEverySmallText(String expression) throws Exception {
        NativeSearch.Prepared prepared = NativeSearch.prepare(DFA.convert(NFA.buildNFA(RegexParser.parse(expression))));
        Pattern reference = Pattern.compile(expression, Pattern.DOTALL);
        for (String text : SearchGenerators.words("abc", 5)) {
            assertEquals(reference.matcher(text).find(), prepared.search(text), expression + " dans " + text);
        }
    }

    /** Vérifie un texte long et répétitif sans limite de temps fragile liée à la machine. */
    @Test
    void scansLongTextAfterOnePreparation() {
        NativeSearch.Prepared prepared = NativeSearch.prepare(AutomatonBuilder.literal("a".repeat(32) + "b"));
        String repeated = "a".repeat(300_000);
        assertFalse(prepared.search(repeated));
        assertTrue(prepared.search(repeated + "b"));
        assertFalse(prepared.search(repeated + "c"));
    }
    /** Le chemin par blocs doit être strictement équivalent au parcours symbole par symbole. */
    @Test
    void byteBulkCursorMatchesScalarCursor() throws Exception {
        NativeSearch.Prepared prepared = NativeSearch.prepareNfa(
                NFA.buildNFA(RegexParser.parse("(Elizabeth|Darcy).*(said|replied)")));
        byte[] text = "xx Elizabeth eventually said yy".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        SearchCursor bulk = prepared.newCursor();
        SearchCursor scalar = prepared.newCursor();
        assertTrue(bulk.accept(text, 0, text.length));
        for (byte value : text) {
            scalar.accept(value & 0xff);
        }
        assertEquals(scalar.matches(), bulk.matches());
    }

}
