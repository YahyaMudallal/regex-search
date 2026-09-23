package com.sorbonne.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
     * <p>Le chemin chaud privilégie une table plate dense lorsque son empreinte
     * reste bornée. Une transition devient alors deux accès de tableau et une
     * multiplication entière. Pour les DFA exceptionnellement larges, une
     * représentation paginée conserve une consommation mémoire raisonnable.
     * La table de classes UTF-16 est directe (128 KiB) : aucun hachage ni double
     * indirection n'est effectué pendant le scan.</p>
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
        Map<State, Row> rows = new IdentityHashMap<>();
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
        /** État sentinelle : aucune continuation possible. */
        private static final int DEAD = -1;
        /** État sentinelle : une occurrence a déjà été trouvée. */
        private static final int MATCH = -2;
        /** Au-delà de 8 Mio de cellules (environ 32 Mio d’octets), bascule vers la table paginée. */
        private static final long MAX_DENSE_CELLS = 8L * 1024 * 1024;
        /** Même budget maximal pour la table ASCII spécialisée. */
        private static final long MAX_ASCII_CELLS = 8L * 1024 * 1024;

        private final int start;
        private final int classCount;
        /** Classe de chaque unité UTF-16, accès direct sans branche. */
        private final char[] classes = new char[Character.MAX_VALUE + 1];
        /** Table état-major : delta[state * classCount + class]. */
        private final int[] denseTransitions;
        /** Table directe pour les octets ASCII du chemin fichier. */
        private final int[] asciiTransitions;
        /** Repli mémoire pour les automates dont la table dense serait excessive. */
        private final int[][][] sparseTransitions;
        private final int[] defaults;

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
                throw new IllegalArgumentException("L'état initial n'appartient pas au DFA");
            }
            start = finals[startId] ? MATCH : startId;

            Set<Character> alphabet = new LinkedHashSet<>();
            for (Row row : rows.values()) {
                alphabet.addAll(row.characters.keySet());
                if (row.other != null) {
                    alphabet.addAll(row.other.getExcludedSymbols());
                }
            }
            List<Character> symbols = new ArrayList<>(alphabet);
            boolean hasOtherClass = symbols.size() < Character.MAX_VALUE + 1;
            classCount = symbols.size() + (hasOtherClass ? 1 : 0);
            int nextClass = hasOtherClass ? 1 : 0;
            for (char symbol : symbols) {
                classes[symbol] = (char) nextClass++;
            }

            long cells = (long) finals.length * classCount;
            if (cells <= MAX_DENSE_CELLS && cells <= Integer.MAX_VALUE) {
                denseTransitions = new int[(int) cells];
                Arrays.fill(denseTransitions, DEAD);
                sparseTransitions = null;
                defaults = null;
                buildDense(rows, ids, finals);
                asciiTransitions = (long) finals.length * 128 <= MAX_ASCII_CELLS
                        ? buildAsciiTransitions(finals.length)
                        : null;
            } else {
                denseTransitions = null;
                asciiTransitions = null;
                sparseTransitions = new int[finals.length][][];
                defaults = new int[finals.length];
                Arrays.fill(defaults, DEAD);
                buildSparse(rows, ids, finals);
            }
        }

        private int encodedDestination(Map<State, Integer> ids, boolean[] finals, State destination) {
            Integer id = ids.get(destination);
            if (id == null) {
                throw new IllegalArgumentException("Une transition référence un état absent du DFA");
            }
            return finals[id] ? MATCH : id;
        }

        private void buildDense(Map<State, Row> rows, Map<State, Integer> ids, boolean[] finals) {
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
                    Arrays.fill(denseTransitions, base, base + classCount, destination);
                    for (char excluded : row.other.getExcludedSymbols()) {
                        denseTransitions[base + classOf(excluded)] = DEAD;
                    }
                }
                for (Map.Entry<Character, State> arc : row.characters.entrySet()) {
                    denseTransitions[base + classOf(arc.getKey())] =
                            encodedDestination(ids, finals, arc.getValue());
                }
            }
        }

        private int[] buildAsciiTransitions(int stateCount) {
            int[] result = new int[stateCount << 7];
            for (int state = 0; state < stateCount; state++) {
                int sourceBase = state * classCount;
                int targetBase = state << 7;
                for (int symbol = 0; symbol < 128; symbol++) {
                    result[targetBase + symbol] = denseTransitions[sourceBase + classes[symbol]];
                }
            }
            return result;
        }

        private void buildSparse(Map<State, Row> rows, Map<State, Integer> ids, boolean[] finals) {
            for (Map.Entry<State, Integer> entry : ids.entrySet()) {
                int state = entry.getValue();
                if (finals[state]) {
                    continue;
                }
                Row row = rows.get(entry.getKey());
                if (row == null) {
                    continue;
                }
                if (row.other != null) {
                    defaults[state] = encodedDestination(ids, finals, row.other.getDestination());
                    for (char excluded : row.other.getExcludedSymbols()) {
                        setSparse(state, classOf(excluded), DEAD);
                    }
                }
                for (Map.Entry<Character, State> arc : row.characters.entrySet()) {
                    setSparse(state, classOf(arc.getKey()), encodedDestination(ids, finals, arc.getValue()));
                }
            }
        }

        private int classOf(char symbol) {
            return classes[symbol];
        }

        /** Alloue uniquement les pages contenant une exception à la transition par défaut. */
        private void setSparse(int state, int symbolClass, int destination) {
            int[][] pages = sparseTransitions[state];
            int pageIndex = symbolClass >>> 8;
            if (pages == null) {
                if (destination == defaults[state]) {
                    return;
                }
                pages = new int[(classCount + 255) >>> 8][];
                sparseTransitions[state] = pages;
            }
            int[] page = pages[pageIndex];
            if (page == null) {
                if (destination == defaults[state]) {
                    return;
                }
                int pageLength = Math.min(256, classCount - (pageIndex << 8));
                page = new int[pageLength];
                Arrays.fill(page, defaults[state]);
                pages[pageIndex] = page;
            }
            page[symbolClass & 255] = destination;
        }

        private int next(int state, char symbol) {
            if (state < 0) {
                return state;
            }
            int symbolClass = classes[symbol];
            if (denseTransitions != null) {
                return denseTransitions[state * classCount + symbolClass];
            }
            int[][] pages = sparseTransitions[state];
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
            if (current == MATCH) {
                return true;
            }
            if (denseTransitions != null) {
                int[] table = denseTransitions;
                char[] charClasses = classes;
                int width = classCount;
                for (int i = 0, length = text.length(); i < length; i++) {
                    current = table[current * width + charClasses[text.charAt(i)]];
                    if (current == MATCH) {
                        return true;
                    }
                    if (current == DEAD) {
                        return false;
                    }
                }
                return false;
            }
            for (int i = 0, length = text.length(); i < length; i++) {
                current = next(current, text.charAt(i));
                if (current == MATCH) {
                    return true;
                }
                if (current == DEAD) {
                    return false;
                }
            }
            return false;
        }

        @Override
        public SearchCursor newCursor() {
            return new Cursor(this);
        }

        /** Curseur concret : facilite l'inlining du chemin chaud par HotSpot. */
        private static final class Cursor implements SearchCursor {
            private final Prepared owner;
            private int current;

            private Cursor(Prepared owner) {
                this.owner = owner;
                current = owner.start;
            }

            @Override
            public boolean accept(char symbol) {
                if (current >= 0) {
                    current = owner.next(current, symbol);
                }
                return current == MATCH;
            }

            @Override
            public boolean acceptAscii(byte[] buffer, int offset, int length) {
                if (current == MATCH || current == DEAD || length == 0) {
                    return current == MATCH;
                }
                int end = offset + length;
                if (owner.asciiTransitions != null) {
                    int state = current;
                    int[] table = owner.asciiTransitions;
                    for (int i = offset; i < end; i++) {
                        state = table[(state << 7) + (buffer[i] & 0x7f)];
                        if (state < 0) {
                            current = state;
                            return state == MATCH;
                        }
                    }
                    current = state;
                    return false;
                }
                if (owner.denseTransitions != null) {
                    int state = current;
                    int[] table = owner.denseTransitions;
                    char[] charClasses = owner.classes;
                    int width = owner.classCount;
                    for (int i = offset; i < end; i++) {
                        state = table[state * width + charClasses[buffer[i] & 0x7f]];
                        if (state < 0) {
                            current = state;
                            return state == MATCH;
                        }
                    }
                    current = state;
                    return false;
                }
                for (int i = offset; i < end; i++) {
                    current = owner.next(current, (char) (buffer[i] & 0x7f));
                    if (current < 0) {
                        return current == MATCH;
                    }
                }
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
