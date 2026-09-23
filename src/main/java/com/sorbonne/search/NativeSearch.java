package com.sorbonne.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Transition;
import com.sorbonne.regex.DFA;

/**
 * Recherche de sous-chaînes par automate, en unités UTF-16.
 * La préparation directe depuis un NFA évite une déterminisation intermédiaire.
 * Le parcours utilise des états entiers et des tables de transitions : O(n)
 * temps au pire et O(1) mémoire supplémentaire, hors moteur préparé.
 * La construction du DFA peut toujours produire exponentiellement d'états.
 */
public final class NativeSearch implements SearchAlgorithm<Automaton> {
    @Override
    public boolean search(String text, Automaton pattern) {
        Objects.requireNonNull(text, "Le texte ne doit pas être nul");
        return prepare(pattern).search(text);
    }

    /**
     * Prépare un DFA de mots entiers pour la recherche de sous-chaînes.
     * Conserve le contrat historique : ε, doublons et chevauchements sont refusés.
     * Pour un NFA déjà disponible, utiliser prepareNfa afin d'éviter deux conversions.
     */
    public static Prepared prepare(Automaton pattern) {
        validate(Objects.requireNonNull(pattern, "Le DFA ne doit pas être nul"));
        return prepareNfa(pattern);
    }

    /** Détermine directement un NFA pour la recherche ; l'entrée n'est pas modifiée. */
    public static Prepared prepareNfa(Automaton pattern) {
        return fromSearchDfa(DFA.forSearch(pattern));
    }

    /**
     * Indexe SANS redéterminiser un automate déjà construit par DFA.forSearch.
     * Le graphe doit gérer tous les départs du motif ; un DFA de mots entiers
     * ordinaire doit passer par prepare. Les futurs changements du graphe n'ont
     * aucun effet sur le moteur préparé.
     *
     * <p>Les transitions utilisent des pages allouées à la demande et une
     * destination par défaut : pas de tableau dense RK systématique. Le pire
     * reste O(RK + T + X) temps/mémoire d'indexation, avec R états, K classes,
     * T arcs et X exclusions. Le parcours fait des accès directs O(1).</p>
     */
    public static Prepared fromSearchDfa(Automaton searchable) {
        return new Prepared(Objects.requireNonNull(searchable, "Le DFA ne doit pas être nul"));
    }

    private static final class Row {
        private final Map<Character, State> characters = new HashMap<>();
        private Transition other;
    }

    /** Validation linéaire moyenne ; aucune table dense n'est allouée ici. */
    private static Map<State, Row> validate(Automaton dfa) {
        if (dfa.getInitialState() == null) {
            throw new IllegalArgumentException("Le DFA doit posséder un état initial");
        }
        Map<State, Row> rows = new HashMap<>();
        for (Transition transition : dfa.getTransitions()) {
            if (transition.isEpsilon()) {
                throw new IllegalArgumentException("Un DFA ne peut pas contenir de transition ε");
            }
            Row row = rows.computeIfAbsent(transition.getSource(), key -> new Row());
            if (transition.getType() == Transition.Type.ANY) {
                if (row.other != null) {
                    throw new IllegalArgumentException("Plusieurs arcs ANY pour un même état");
                }
                row.other = transition;
            } else if (row.characters.putIfAbsent(transition.getSymbol(), transition.getDestination()) != null) {
                throw new IllegalArgumentException("Plusieurs arcs pour une même lettre et un même état");
            }
        }
        for (Row row : rows.values()) {
            if (row.other != null) {
                for (char symbol : row.characters.keySet()) {
                    if (row.other.matches(symbol)) {
                        throw new IllegalArgumentException("Chevauchement entre un arc littéral et un arc ANY");
                    }
                }
            }
        }
        return rows;
    }

    /** Tables privées immuables ; aucun objet State n'est conservé après indexation. */
    public static final class Prepared implements PreparedSearch {
        private final int start;
        private final boolean[] finals;
        private final int[][][] transitions;
        private final int[] defaults;
        private final int classCount;
        // Deux niveaux évitent 65 536 cases pour les petits alphabets.
        // La classe 0 représente tous les caractères absents de l'alphabet explicite.
        private final int[][] classes = new int[256][];

        private Prepared(Automaton dfa) {
            Map<State, Row> rows = validate(dfa);
            Map<State, Integer> ids = new HashMap<>();
            finals = new boolean[dfa.getStates().size()];
            transitions = new int[finals.length][][];
            defaults = new int[finals.length];
            Arrays.fill(defaults, -1);
            for (State state : dfa.getStates()) {
                int id = ids.size();
                ids.put(state, id);
                finals[id] = state.getStatus().isFinal();
            }
            start = ids.get(dfa.getInitialState());
            Set<Character> alphabet = new LinkedHashSet<>();
            for (Row row : rows.values()) {
                alphabet.addAll(row.characters.keySet());
                if (row.other != null) {
                    alphabet.addAll(row.other.getExcludedSymbols());
                }
            }
            List<Character> symbols = new ArrayList<>(alphabet);
            classCount = symbols.size() + 1;
            for (int i = 0; i < symbols.size(); i++) {
                char symbol = symbols.get(i);
                if (classes[symbol >>> 8] == null) {
                    classes[symbol >>> 8] = new int[256];
                }
                classes[symbol >>> 8][symbol & 255] = i + 1;
            }
            for (Map.Entry<State, Integer> entry : ids.entrySet()) {
                int id = entry.getValue();
                if (finals[id]) {
                    continue;
                }
                Row row = rows.get(entry.getKey());
                if (row == null) {
                    continue;
                }
                if (row.other != null) {
                    defaults[id] = ids.get(row.other.getDestination());
                    for (char excluded : row.other.getExcludedSymbols()) {
                        set(id, classOf(excluded), -1);
                    }
                }
                for (Map.Entry<Character, State> arc : row.characters.entrySet()) {
                    set(id, classOf(arc.getKey()), ids.get(arc.getValue()));
                }
            }
        }

        private int classOf(char symbol) {
            int[] page = classes[symbol >>> 8];
            return page == null ? 0 : page[symbol & 255];
        }

        /** Alloue uniquement les pages contenant une exception à la transition par défaut. */
        private void set(int state, int symbolClass, int destination) {
            int[][] pages = transitions[state];
            int pageIndex = symbolClass >>> 8;
            if (pages == null) {
                if (destination == defaults[state]) {
                    return;
                }
                pages = new int[(classCount + 255) >>> 8][];
                transitions[state] = pages;
            }
            int[] page = pages[pageIndex];
            if (page == null) {
                if (destination == defaults[state]) {
                    return;
                }
                page = new int[Math.min(256, classCount)];
                Arrays.fill(page, defaults[state]);
                pages[pageIndex] = page;
            }
            page[symbolClass & 255] = destination;
        }

        private int next(int state, char symbol) {
            int symbolClass = classOf(symbol);
            int[][] pages = transitions[state];
            if (pages == null) {
                return defaults[state];
            }
            int[] page = pages[symbolClass >>> 8];
            return page == null ? defaults[state] : page[symbolClass & 255];
        }

        @Override
        public boolean search(String text) {
            Objects.requireNonNull(text, "Le texte ne doit pas être nul");
            int current = start;
            if (finals[current]) {
                return true;
            }
            for (int i = 0; i < text.length(); i++) {
                current = next(current, text.charAt(i));
                if (current < 0) {
                    return false;
                }
                if (finals[current]) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public SearchCursor newCursor() {
            return new SearchCursor() {
                private int current = start;

                @Override
                public boolean accept(char symbol) {
                    if (current >= 0 && !finals[current]) {
                        current = next(current, symbol);
                    }
                    return matches();
                }

                @Override
                public boolean matches() {
                    return current >= 0 && finals[current];
                }

                @Override
                public void reset() {
                    current = start;
                }
            };
        }
    }
}
