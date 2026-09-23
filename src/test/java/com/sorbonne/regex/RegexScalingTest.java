package com.sorbonne.regex;

import com.sorbonne.automata.Automaton;
import com.sorbonne.search.NativeSearch;
import com.sorbonne.support.SearchGenerators;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Régressions des copies quadratiques et des dépassements de pile, sans seuil de temps. */
class RegexScalingTest {
    @Test
    void buildsLongConcatenationWithoutRecursionOrFragmentCopies() throws Exception {
        int length = 20_000;
        SyntaxTree tree = RegexParser.parse("a".repeat(length));
        assertFalse(tree.acceptsEmpty());
        Automaton nfa = NFA.buildNFA(tree);
        assertEquals(2 * length, nfa.getStates().size());
        assertEquals(2 * length - 1, nfa.getTransitions().size());
        assertEquals(1, nfa.getFinalStates().size());
        assertNotNull(nfa.getInitialState());
    }

    @Test
    void acceptsDeepParenthesesAndUnaryOperators() throws Exception {
        int depth = 20_000;
        SyntaxTree grouped = RegexParser.parse("(".repeat(depth) + "a" + ")".repeat(depth));
        assertEquals("a", grouped.getLetter());
        SyntaxTree stars = RegexParser.parse("a" + "*".repeat(depth));
        assertTrue(stars.acceptsEmpty());
        Automaton nfa = NFA.buildNFA(stars);
        assertEquals(2 + 2 * depth, nfa.getStates().size());
        Automaton searchable = DFA.forSearch(nfa);
        assertEquals(1, searchable.getStates().size());
        assertTrue(searchable.getTransitions().isEmpty());
        assertTrue(NativeSearch.fromSearchDfa(searchable).search(""));
    }

    @Test
    void searchStopsAtAcceptanceButWholeWordConversionPreservesSuffixes() throws Exception {
        Automaton nfa = NFA.buildNFA(RegexParser.parse("a|a" + "b".repeat(200)));
        Automaton searchable = DFA.forSearch(nfa);
        assertEquals(2, searchable.getStates().size());
        assertTrue(searchable.getFinalStates().stream()
                .allMatch(state -> searchable.getOutgoingTransitions(state).isEmpty()));
        assertTrue(NativeSearch.fromSearchDfa(searchable).search("xxax"));
        Automaton wholeWord = DFA.convert(nfa);
        assertTrue(SearchGenerators.accepts(wholeWord, "a" + "b".repeat(200)));
        assertFalse(SearchGenerators.accepts(wholeWord, "ab"));
    }

    @Test
    void handlesTheWholeByteAlphabetWithOneWildcard() throws Exception {
        Automaton nfa = NFA.buildNFA(RegexParser.parse("a.b"));
        Automaton dfa = DFA.convert(nfa);
        Automaton searchable = DFA.forSearch(nfa);

        // Le point reste une transition symbolique : l'automate ne duplique pas 256 arcs.
        assertEquals(3, dfa.getTransitions().size());
        assertEquals(10, searchable.getTransitions().size());

        NativeSearch.Prepared prepared = NativeSearch.fromSearchDfa(searchable);
        for (int symbol = 0; symbol < 256; symbol++) {
            byte[] word = {(byte) 'a', (byte) symbol, (byte) 'b'};
            assertTrue(prepared.search(word), "Le point doit accepter l'octet " + symbol);
        }
        assertFalse(prepared.search(new byte[] {'a', 'b'}));
    }

    @Test
    void validatesMissingOperandsAndPreservesRepeatedStars() throws Exception {
        for (String invalid : new String[] {"a||b", "a(|b)", "a(b|)", "(*)", "a|*b", "((a)"}) {
            assertThrows(Exception.class, () -> RegexParser.parse(invalid), invalid);
        }
        assertTrue(RegexParser.parse("a**").acceptsEmpty());
        assertFalse(RegexParser.parse("a*b").acceptsEmpty());
        assertTrue(RegexParser.parse("a*b*").acceptsEmpty());
        assertTrue(RegexParser.parse("ab|c*").acceptsEmpty());
    }
}
