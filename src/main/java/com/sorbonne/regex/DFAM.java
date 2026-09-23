package com.sorbonne.regex;

import com.sorbonne.automata.Automaton;

/**
 * Point d'entrée public de la minimisation d'un automate déterministe.
 *
 * <p>La minimisation repose sur le raffinement de partitions de Hopcroft,
 * implémenté dans {@link DFAMHopcroft}. Le langage du DFA est conservé, les
 * états inaccessibles sont éliminés et les transitions absentes sont traitées
 * comme allant vers un puits implicite pendant le raffinement.</p>
 */
public final class DFAM {
    /** Empêche l'instanciation de cette classe utilitaire. */
    private DFAM() {
    }

    /**
     * Construit un automate minimal équivalent au DFA fourni.
     *
     * @param dfa automate déterministe non nul à minimiser
     * @return nouvel automate minimal équivalent ; l'entrée n'est pas modifiée
     * @throws NullPointerException si l'automate est nul
     * @throws IllegalArgumentException si l'automate n'est pas un DFA valide
     */
    public static Automaton minimize(Automaton dfa) {
        return DFAMHopcroft.minimize(dfa);
    }
}
