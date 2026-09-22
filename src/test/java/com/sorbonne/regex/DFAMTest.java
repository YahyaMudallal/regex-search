package com.sorbonne.regex;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sorbonne.automata.Automaton;
import org.junit.jupiter.api.Test;

/** Vérifie uniquement le contrat provisoire ; aucun algorithme de minimisation n'est encore testé. */
class DFAMTest {
    /** Crée les tests du point d'intégration DFAM. */
    DFAMTest() {
    }

    /**
     * Le placeholder rend exactement le DFA reçu ; ce test évoluera avec la minimisation.
     * @throws Exception si la construction du DFA de test échoue
     */
    @Test
    void returnsInputUntilMinimizationIsImplemented() throws Exception {
        Automaton dfa = DFA.convert(NFA.buildNFA(RegexParser.parse("a|bc*")));
        assertSame(dfa, DFAM.minimize(dfa));
    }

    /** Signale explicitement une entrée nulle dès l'appel. */
    @Test
    @SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> DFAM.minimize(null));
    }
}
