package com.sorbonne.regex;

import com.sorbonne.automata.Automaton;
import java.util.Objects;

/**
 * Point d'intégration de la future minimisation d'un automate déterministe.
 * La méthode actuelle est une identité : elle ne réduit ni les états ni les arcs.
 */
public final class DFAM {
    /** Empêche l'instanciation de cette classe utilitaire. */
    private DFAM() {
    }

    /**
     * Renvoie provisoirement le DFA fourni, sans le copier ni le modifier.
     *
     * <p>Le binôme pourra remplacer ce corps par la minimisation en conservant
     * la signature et le langage reconnu. Aucun contrôle du déterminisme n'est
     * réalisé ici. Le coût actuel est O(1) en temps et en mémoire supplémentaire ;
     * il ne préjuge pas du coût de la future implémentation.</p>
     *
     * @param dfa automate déterministe non nul à minimiser
     * @return actuellement la même instance, qui n'est pas nécessairement minimale
     * @throws NullPointerException si l'automate est nul
     */
    public static Automaton minimize(Automaton dfa) {
        // TODO : implémenter la minimisation en préservant le langage du DFA.
        return Objects.requireNonNull(dfa, "Le DFA ne doit pas être nul");
    }
}
