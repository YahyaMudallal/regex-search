package com.sorbonne.search;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Transition;
import com.sorbonne.regex.DFA;

/**
 * Recherche de sous-chaines par automate sur un alphabet de 256 valeurs.
 *
 * <p>Le chemin fichier ne convertit pas les donnees : chaque octet devient directement
 * un symbole de l'automate. Pour les DFA usuels du projet, la fonction de transition
 * est une table plate {@code delta[state * 256 + symbol]}. Une transition ne demande
 * donc qu'un calcul d'indice et un acces memoire. La recherche reste O(n) et ne garde
 * qu'un identifiant d'etat mutable par curseur.</p>
 */
public final class NativeSearch implements SearchAlgorithm<Automaton> {
    private static final int ALPHABET_SIZE = 256;
    /** 16 M cellules = 64 Mio ; au-dela, une table compacte par classes est utilisee. */
    private static final long MAX_DIRECT_CELLS = 16L * 1024 * 1024;

    @Override
    public boolean search(String text, Automaton pattern) {
        Objects.requireNonNull(text, "Le texte ne doit pas etre nul");
        return prepare(pattern).search(text);
    }

    /** Prepare un DFA de mots entiers pour la recherche de sous-chaines. */
    public static Prepared prepare(Automaton pattern) {
        validate(Objects.requireNonNull(pattern, "Le DFA ne doit pas etre nul"));
        return prepareNfa(pattern);
    }

    /** Determine directement un NFA pour la recherche ; l'entree n'est pas modifiee. */
    public static Prepared prepareNfa(Automaton pattern) {
        return fromSearchDfa(DFA.forSearch(pattern));
    }

    /** Indexe sans redeterminer un automate deja construit par {@link DFA#forSearch(Automaton)}. */
    public static Prepared fromSearchDfa(Automaton searchable) {
        return new Prepared(Objects.requireNonNull(searchable, "Le DFA ne doit pas etre nul"));
    }

    private static final class Row {
        private final Map<Integer, State> characters = new HashMap<>();
        private Transition other;
    }

    /** Validation deterministe et verification de l'alphabet 8 bits. */
    private static Map<State, Row> validate(Automaton dfa) {
        if (dfa.getInitialState() == null) {
            throw new IllegalArgumentException("Le DFA doit posseder un etat initial");
        }
        Map<State, Row> rows = new IdentityHashMap<>();
        for (Transition transition : dfa.getTransitions()) {
            if (transition.isEpsilon()) {
                throw new IllegalArgumentException("Un DFA ne peut pas contenir de transition epsilon");
            }
            Row row = rows.computeIfAbsent(transition.getSource(), key -> new Row());
            if (transition.getType() == Transition.Type.ANY) {
                if (row.other != null) {
                    throw new IllegalArgumentException("Plusieurs arcs ANY pour un meme etat");
                }
                for (char excluded : transition.getExcludedSymbols()) {
                    requireByteSymbol(excluded);
                }
                row.other = transition;
            } else {
                int symbol = transition.getSymbol();
                requireByteSymbol(symbol);
                if (row.characters.putIfAbsent(symbol, transition.getDestination()) != null) {
                    throw new IllegalArgumentException("Plusieurs arcs pour un meme symbole et un meme etat");
                }
            }
        }
        for (Row row : rows.values()) {
            if (row.other != null) {
                for (int symbol : row.characters.keySet()) {
                    if (row.other.matches((char) symbol)) {
                        throw new IllegalArgumentException("Chevauchement entre un arc litteral et un arc ANY");
                    }
                }
            }
        }
        return rows;
    }

    private static void requireByteSymbol(int symbol) {
        if (symbol < 0 || symbol >= ALPHABET_SIZE) {
            throw new IllegalArgumentException("Symbole hors alphabet 8 bits : " + symbol);
        }
    }

    /** Tables privees immuables ; aucun objet State n'est conserve apres indexation. */
    public static final class Prepared implements PreparedSearch {
        private static final int DEAD = -1;
        private static final int MATCH = -2;

        private final int start;
        /** Table rapide et directe, presente pour les automates de taille usuelle. */
        private final int[] directTransitions;
        /** Repli compact pour un automate exceptionnellement grand. */
        private final byte[] classes;
        private final int classCount;
        private final int[] compactTransitions;

        private Prepared(Automaton dfa) {
            Map<State, Row> rows = validate(dfa);
            Map<State, Integer> ids = new IdentityHashMap<>(Math.max(16, dfa.getStates().size() * 2));
            boolean[] finals = new boolean[dfa.getStates().size()];
            for (State state : dfa.getStates()) {
                int id = ids.size();
                ids.put(state, id);
                finals[id] = state.getStatus().isFinal();
            }
            Integer startId = ids.get(dfa.getInitialState());
            if (startId == null) {
                throw new IllegalArgumentException("L'etat initial n'appartient pas au DFA");
            }
            start = finals[startId] ? MATCH : startId;

            long directCells = (long) finals.length * ALPHABET_SIZE;
            if (directCells <= MAX_DIRECT_CELLS && directCells <= Integer.MAX_VALUE) {
                directTransitions = new int[(int) directCells];
                Arrays.fill(directTransitions, DEAD);
                classes = null;
                classCount = 0;
                compactTransitions = null;
                buildDirect(rows, ids, finals);
            } else {
                directTransitions = null;
                classes = buildClasses(rows);
                int maxClass = 0;
                for (byte value : classes) {
                    maxClass = Math.max(maxClass, value & 0xff);
                }
                classCount = maxClass + 1;
                compactTransitions = new int[Math.multiplyExact(finals.length, classCount)];
                Arrays.fill(compactTransitions, DEAD);
                buildCompact(rows, ids, finals);
            }
        }

        private int encodedDestination(Map<State, Integer> ids, boolean[] finals, State destination) {
            Integer id = ids.get(destination);
            if (id == null) {
                throw new IllegalArgumentException("Une transition reference un etat absent du DFA");
            }
            return finals[id] ? MATCH : id;
        }

        private void buildDirect(Map<State, Row> rows, Map<State, Integer> ids, boolean[] finals) {
            for (Map.Entry<State, Integer> entry : ids.entrySet()) {
                int state = entry.getValue();
                if (finals[state]) {
                    continue;
                }
                Row row = rows.get(entry.getKey());
                if (row == null) {
                    continue;
                }
                int base = state << 8;
                if (row.other != null) {
                    int destination = encodedDestination(ids, finals, row.other.getDestination());
                    Arrays.fill(directTransitions, base, base + ALPHABET_SIZE, destination);
                    for (char excluded : row.other.getExcludedSymbols()) {
                        directTransitions[base + excluded] = DEAD;
                    }
                }
                for (Map.Entry<Integer, State> arc : row.characters.entrySet()) {
                    directTransitions[base + arc.getKey()] = encodedDestination(ids, finals, arc.getValue());
                }
            }
        }


        /**
         * Construit des classes uniquement pour le repli memoire. Chaque symbole
         * mentionne explicitement est distingue ; les autres partagent la classe 0.
         */
        private static byte[] buildClasses(Map<State, Row> rows) {
            boolean[] significant = new boolean[ALPHABET_SIZE];
            for (Row row : rows.values()) {
                for (int symbol : row.characters.keySet()) {
                    significant[symbol] = true;
                }
                if (row.other != null) {
                    for (char excluded : row.other.getExcludedSymbols()) {
                        significant[excluded] = true;
                    }
                }
            }
            byte[] result = new byte[ALPHABET_SIZE];
            int next = 1;
            for (int symbol = 0; symbol < ALPHABET_SIZE; symbol++) {
                if (significant[symbol]) {
                    if (next >= ALPHABET_SIZE) {
                        // Les 256 symboles sont significatifs : leur valeur est deja une classe parfaite.
                        for (int i = 0; i < ALPHABET_SIZE; i++) {
                            result[i] = (byte) i;
                        }
                        return result;
                    }
                    result[symbol] = (byte) next++;
                }
            }
            return result;
        }

        private void buildCompact(Map<State, Row> rows, Map<State, Integer> ids, boolean[] finals) {
            for (Map.Entry<State, Integer> entry : ids.entrySet()) {
                int state = entry.getValue();
                if (finals[state]) {
                    continue;
                }
                Row row = rows.get(entry.getKey());
                if (row == null) {
                    continue;
                }
                int base = state * classCount;
                if (row.other != null) {
                    int destination = encodedDestination(ids, finals, row.other.getDestination());
                    Arrays.fill(compactTransitions, base, base + classCount, destination);
                    for (char excluded : row.other.getExcludedSymbols()) {
                        compactTransitions[base + (classes[excluded] & 0xff)] = DEAD;
                    }
                }
                for (Map.Entry<Integer, State> arc : row.characters.entrySet()) {
                    int clazz = classes[arc.getKey()] & 0xff;
                    compactTransitions[base + clazz] = encodedDestination(ids, finals, arc.getValue());
                }
            }
        }

        private int next(int state, int symbol) {
            if (state < 0) {
                return state;
            }
            if (directTransitions != null) {
                return directTransitions[(state << 8) | symbol];
            }
            return compactTransitions[state * classCount + (classes[symbol] & 0xff)];
        }

        @Override
        public SearchCursor newCursor() {
            return new Cursor(this);
        }

        private static final class Cursor implements SearchCursor {
            private final Prepared owner;
            private int current;

            private Cursor(Prepared owner) {
                this.owner = owner;
                current = owner.start;
            }

            @Override
            public boolean accept(int symbol) {
                if ((symbol & ~0xff) != 0) {
                    throw new IllegalArgumentException("Symbole hors alphabet 8 bits : " + symbol);
                }
                if (current >= 0) {
                    current = owner.next(current, symbol);
                }
                return current == MATCH;
            }

            @Override
            public boolean accept(byte[] buffer, int offset, int length) {
                Objects.requireNonNull(buffer, "Le tampon ne doit pas etre nul");
                if (offset < 0 || length < 0 || offset > buffer.length - length) {
                    throw new IndexOutOfBoundsException("Segment d'octets invalide");
                }
                if (current == MATCH || current == DEAD || length == 0) {
                    return current == MATCH;
                }
                int end = offset + length;
                int state = current;
                if (owner.directTransitions != null) {
                    int[] table = owner.directTransitions;
                    for (int i = offset; i < end; i++) {
                        state = table[(state << 8) | (buffer[i] & 0xff)];
                        if (state < 0) {
                            current = state;
                            return state == MATCH;
                        }
                    }
                } else {
                    int[] table = owner.compactTransitions;
                    byte[] classes = owner.classes;
                    int width = owner.classCount;
                    for (int i = offset; i < end; i++) {
                        state = table[state * width + (classes[buffer[i] & 0xff] & 0xff)];
                        if (state < 0) {
                            current = state;
                            return state == MATCH;
                        }
                    }
                }
                current = state;
                return false;
            }

            @Override
            public boolean matches() {
                return current == MATCH;
            }

            @Override
            public void reset() {
                current = owner.start;
            }
        }
    }
}
