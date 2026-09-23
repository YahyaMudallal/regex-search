package com.sorbonne.benchmark;

import com.sorbonne.automata.Automaton;
import com.sorbonne.regex.DFA;
import com.sorbonne.regex.NFA;
import com.sorbonne.regex.RegexParser;
import com.sorbonne.regex.SyntaxTree;

/**
 * Profil structurel hors chronométrage : taille du NFA de Thompson puis du DFA
 * spécialisé pour la recherche. DFAM n'est volontairement pas appelé ici afin
 * que la future minimisation du binôme reste indépendante du protocole actuel.
 */
public final class AutomatonProfile {
    private AutomatonProfile() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("regex");
        }
        SyntaxTree tree = RegexParser.parse(args[0]);
        Automaton nfa = NFA.buildNFA(tree);
        Automaton searchDfa = DFA.forSearch(nfa);
        System.out.println("regex_length,nfa_states,nfa_transitions,search_dfa_states,search_dfa_transitions");
        System.out.printf("%d,%d,%d,%d,%d%n",
                args[0].length(), nfa.getStates().size(), nfa.getTransitions().size(),
                searchDfa.getStates().size(), searchDfa.getTransitions().size());
    }
}
