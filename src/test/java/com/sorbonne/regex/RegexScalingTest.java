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
    void handlesLargeAlphabetWithoutDenseStateByAlphabetExpansion() throws Exception {
        StringBuilder word = new StringBuilder();
        for (int i = 0x100; i < 0x700; i++) {
            word.append((char) i);
        }
        Automaton nfa = NFA.buildNFA(RegexParser.parse(word.toString()));
        Automaton dfa = DFA.convert(nfa);
        assertEquals(word.length(), dfa.getTransitions().size());
        Automaton searchable = DFA.forSearch(nfa);
        // Au plus : première lettre, prochaine lettre, tous les autres.
        assertTrue(searchable.getTransitions().size() <= 3 * word.length());
        NativeSearch.Prepared prepared = NativeSearch.fromSearchDfa(searchable);
        assertTrue(prepared.search("\0\uffff" + word + "suffixe"));
        assertFalse(prepared.search(word.substring(0, word.length() - 1)));
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
