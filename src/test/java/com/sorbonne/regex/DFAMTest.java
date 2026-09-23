package com.sorbonne.regex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sorbonne.automata.Automaton;
import com.sorbonne.search.NativeSearch;
import org.junit.jupiter.api.Test;

/** Vérifie le point d'entrée public de la minimisation. */
class DFAMTest {
    DFAMTest() {
    }

    /** Le point d'entrée public exécute réellement Hopcroft et ne renvoie pas le DFA source. */
    @Test
    void delegatesToRealMinimization() throws Exception {
        Automaton dfa = DFA.forSearch(NFA.buildNFA(RegexParser.parse(
                "(Elizabeth|Darcy|Bennet|Bingley).*(said|replied|answered|cried)")));
        Automaton minimized = DFAM.minimize(dfa);
        assertNotSame(dfa, minimized);
        assertEquals(DFAMHopcroft.minimize(dfa).getStates().size(), minimized.getStates().size());

        NativeSearch.Prepared before = NativeSearch.fromSearchDfa(dfa);
        NativeSearch.Prepared after = NativeSearch.fromSearchDfa(minimized);
        for (String text : new String[] {"Elizabeth said", "Darcy replied", "nothing here", "Bennet cried"}) {
            assertEquals(before.search(text), after.search(text), text);
        }
    }

    /** Signale explicitement une entrée nulle dès l'appel. */
    @Test
    @SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> DFAM.minimize(null));
    }
}
