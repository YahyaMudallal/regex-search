package com.sorbonne.regex;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Déterminisation par sous-ensembles accessibles, représentés par des BitSet.
 * Les arcs ε, littéraux et ANY sont indexés séparément. Un déplacement ne
 * reparcourt pas les arcs littéraux portant d'autres caractères ; une fermeture
 * ne parcourt que les arcs ε. Les clés de la table ne sont plus modifiées.
 *
 * <p>Pour N états, E arcs, X exclusions, K classes et R sous-ensembles accessibles,
 * la borne reste O(N + E + X + RK(N + E)) en temps moyen. Les bitsets occupent
 * O(R ceil(N/64)) mots ; le graphe produit O(RK), les index O(N + E + X).
 * Aucune table quadratique de toutes les fermetures n'est pré-calculée.
 * Un DFA équivalent peut nécessiter un nombre exponentiel d'états.</p>
 */
public final class DFA {
    private DFA() {
    }

    /**
     * Construit un DFA partiel reconnaissant les mêmes mots entiers que le NFA.
     * L'entrée n'est pas modifiée ; elle doit avoir exactement un état initial.
     * @param nfa graphe non nul
     * @return nouveau DFA sans ε, avec classes sortantes disjointes
     */
    public static Automaton convert(Automaton nfa) {
        return convert(nfa, false);
    }

    /**
     * Construit directement un DFA destiné à détecter une occurrence.
     * Après chaque caractère, la fermeture initiale est réinjectée : tous les
     * départs du motif sont suivis simultanément, sans déterminisation préalable.
     * Les sous-ensembles acceptants partagent un état terminal sans arcs sortants.
     *
     * <p>Le consommateur doit s'arrêter au PREMIER état final, y compris avant
     * lecture. Ce graphe n'est pas destiné à la reconnaissance de mots entiers.
     * Un motif nullable donne immédiatement un seul état initial/final.</p>
     * @param nfa automate du motif, déterministe ou non
     * @return automate de recherche, sans modifier l'entrée
     */
    public static Automaton forSearch(Automaton nfa) {
        return convert(nfa, true);
    }

    private record Pending(BitSet subset, State state) {
    }

    private static Automaton convert(Automaton nfa, boolean searching) {
        IndexedNfa input = new IndexedNfa(Objects.requireNonNull(nfa, "Le NFA ne doit pas être nul"));
        BitSet start = new BitSet();
        start.set(input.initial);
        input.close(start);
        Automaton result = new Automaton();
        boolean nullable = start.intersects(input.finals);
        State first = new State("D0", nullable ? Status.ENTER_FINAL : Status.ENTER);
        result.addState(first);
        if (searching && nullable) {
            return result;
        }
        Map<BitSet, State> known = new HashMap<>();
        Queue<Pending> pending = new ArrayDeque<>();
        known.put(start, first);
        pending.add(new Pending(start, first));
        State found = null;
        while (!pending.isEmpty()) {
            Pending current = pending.remove();
            Alphabet alphabet = input.alphabet(current.subset());
            List<Character> representatives = new ArrayList<>(alphabet.explicit());
            if (searching || alphabet.any()) {
                for (int code = Character.MIN_VALUE; code <= Character.MAX_VALUE; code++) {
                    if (!alphabet.explicit().contains((char) code)) {
                        representatives.add((char) code);
                        break;
                    }
                }
            }
            Set<Character> exclusions = Set.copyOf(alphabet.explicit());
            for (char symbol : representatives) {
                BitSet next = input.move(current.subset(), symbol);
                input.close(next);
                if (searching) {
                    next.or(start);
                }
                if (next.isEmpty()) {
                    continue;
                }
                boolean accepting = next.intersects(input.finals);
                State destination;
                if (searching && accepting) {
                    if (found == null) {
                        found = new State("found", Status.FINAL);
                        result.addState(found);
                    }
                    destination = found;
                } else {
                    destination = known.get(next);
                    if (destination == null) {
                        destination = new State("D" + known.size(), accepting ? Status.FINAL : Status.INTERMEDIATE);
                        known.put(next, destination);
                        pending.add(new Pending(next, destination));
                        result.addState(destination);
                    }
                }
                result.add(alphabet.explicit().contains(symbol)
                        ? new Transition(current.state(), destination, symbol)
                        : Transition.anyExcept(current.state(), destination, exclusions));
            }
        }
        return result;
    }

    /** Partition locale : les lettres inutiles dans cet état ne créent aucun arc. */
    private record Alphabet(Set<Character> explicit, boolean any) {
    }

    private record Wildcard(int destination, Set<Character> excluded) {
    }

    private static final class Row {
        private final List<Integer> epsilon = new ArrayList<>();
        private final Map<Character, List<Integer>> characters = new HashMap<>();
        private final List<Wildcard> others = new ArrayList<>();
    }

    /** Index temporaire ; les identifiants entiers sont locaux à cette conversion. */
    private static final class IndexedNfa {
        private final Row[] rows;
        private final int[] stack;
        private final int initial;
        private final BitSet finals = new BitSet();

        private IndexedNfa(Automaton nfa) {
            State start = nfa.getInitialState();
            if (start == null) {
                throw new IllegalArgumentException("Le NFA doit posséder un état initial");
            }
            Map<State, Integer> ids = new HashMap<>();
            rows = new Row[nfa.getStates().size()];
            stack = new int[rows.length];
            for (State state : nfa.getStates()) {
                int id = ids.size();
                ids.put(state, id);
                rows[id] = new Row();
                if (state.getStatus().isFinal()) {
                    finals.set(id);
                }
            }
            initial = ids.get(start);
            for (Transition transition : nfa.getTransitions()) {
                Row row = rows[ids.get(transition.getSource())];
                int destination = ids.get(transition.getDestination());
                switch (transition.getType()) {
                    case EPSILON -> row.epsilon.add(destination);
                    case CHARACTER -> {
                        char symbol = transition.getSymbol();
                        row.characters.computeIfAbsent(symbol, key -> new ArrayList<>()).add(destination);
                    }
                    case ANY -> {
                        Set<Character> excluded = transition.getExcludedSymbols();
                        row.others.add(new Wildcard(destination, excluded));
                    }
                }
            }
        }

        private Alphabet alphabet(BitSet current) {
            Set<Character> explicit = new LinkedHashSet<>();
            boolean any = false;
            for (int state = current.nextSetBit(0); state >= 0; state = current.nextSetBit(state + 1)) {
                Row row = rows[state];
                explicit.addAll(row.characters.keySet());
                for (Wildcard arc : row.others) {
                    any = true;
                    explicit.addAll(arc.excluded());
                }
            }
            return new Alphabet(explicit, any);
        }

        private BitSet move(BitSet current, char symbol) {
            BitSet next = new BitSet();
            for (int state = current.nextSetBit(0); state >= 0; state = current.nextSetBit(state + 1)) {
                Row row = rows[state];
                List<Integer> destinations = row.characters.get(symbol);
                if (destinations != null) {
                    for (int destination : destinations) {
                        next.set(destination);
                    }
                }
                for (Wildcard arc : row.others) {
                    if (!arc.excluded().contains(symbol)) {
                        next.set(arc.destination());
                    }
                }
            }
            return next;
        }

        /** Ferme un nouvel ensemble en place ; chaque état est empilé au plus une fois. */
        private void close(BitSet states) {
            int size = 0;
            for (int state = states.nextSetBit(0); state >= 0; state = states.nextSetBit(state + 1)) {
                stack[size++] = state;
            }
            while (size > 0) {
                for (int destination : rows[stack[--size]].epsilon) {
                    if (!states.get(destination)) {
                        states.set(destination);
                        stack[size++] = destination;
                    }
                }
            }
        }
    }
}
