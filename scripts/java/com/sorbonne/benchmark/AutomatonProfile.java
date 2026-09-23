package com.sorbonne.benchmark;

import com.sorbonne.automata.Automaton;
import com.sorbonne.regex.DFA;
import com.sorbonne.regex.DFAM;
import com.sorbonne.regex.NFA;
import com.sorbonne.regex.RegexParser;
import com.sorbonne.regex.SyntaxTree;

/**
 * Profil structurel hors chronométrage : NFA de Thompson, DFA spécialisé pour
 * la recherche puis DFA minimal. Les tailles sont collectées séparément des
 * chronomètres afin de relier les coûts mesurés aux graphes effectivement créés.
 */
public final class AutomatonProfile {
    private AutomatonProfile() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage : AutomatonProfile <regex>");
        }
        SyntaxTree tree = RegexParser.parse(args[0]);
        Automaton nfa = NFA.buildNFA(tree);
        Automaton searchDfa = DFA.forSearch(nfa);
        Automaton minimized = DFAM.minimize(searchDfa);
        System.out.println("regex_length,nfa_states,nfa_transitions,search_dfa_states,search_dfa_transitions,dfam_states,dfam_transitions");
        System.out.printf("%d,%d,%d,%d,%d,%d,%d%n",
                args[0].length(), nfa.getStates().size(), nfa.getTransitions().size(),
                searchDfa.getStates().size(), searchDfa.getTransitions().size(),
                minimized.getStates().size(), minimized.getTransitions().size());
    }
}
