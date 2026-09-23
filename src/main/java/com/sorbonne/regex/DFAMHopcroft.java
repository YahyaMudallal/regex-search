package com.sorbonne.regex;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;

/**
 * Minimisation d'un automate déterministe par raffinement de partitions de
 * Hopcroft.
 *
 * <p>
 * L'automate du projet est partiel et possède des transitions symboliques
 * {@code ANY}
 * avec exclusions. La minimisation commence donc par construire un alphabet
 * fini de
 * classes de caractères : chaque caractère apparaissant littéralement ou dans
 * une
 * exclusion forme une classe singleton, et les autres valeurs de l'alphabet 8 bits
 * partagent au plus une classe « autre ». Un état puits implicite complète ensuite
 * la fonction de transition sans étendre l'alphabet au-delà de 256 symboles.
 * </p>
 *
 * <p>
 * Sur {@code n} états accessibles du DFA complété et {@code k} classes de
 * caractères, le raffinement de Hopcroft est en {@code O(k n log n)} en temps
 * et
 * {@code O(k n)} en mémoire. Les prédécesseurs sont stockés en tableaux CSR et
 * les
 * blocs sont des listes doublement chaînées indexées par entiers : une scission
 * ne
 * parcourt que les prédécesseurs effectivement touchés, sans recopier le bloc
 * entier.
 * La construction de l'index ajoute {@code O(E + X + A k + n k)}, où {@code E}
 * est
 * le nombre d'arcs, {@code X} le nombre total d'exclusions et {@code A} le
 * nombre
 * d'arcs {@code ANY}. Le résultat ne contient aucun état inaccessible et reste
 * un
 * DFA partiel compact.
 * </p>
 */
public final class DFAMHopcroft {
    /** Taille de l'alphabet interne : une valeur par octet. */
    private static final int SYMBOL_COUNT = 256;

    /** Empêche l'instanciation de cette classe utilitaire. */
    private DFAMHopcroft() {
    }

    /**
     * Renvoie un nouvel automate déterministe minimal reconnaissant le même
     * langage.
     *
     * <p>
     * Les états inaccessibles sont supprimés. Les transitions absentes sont
     * interprétées comme allant vers un puits implicite pendant la minimisation ;
     * le
     * puits n'est matérialisé dans le résultat que s'il est équivalent à un
     * véritable
     * état accessible du DFA d'entrée. L'automate fourni n'est jamais modifié.
     * </p>
     *
     * @param dfa automate déterministe non nul, sans transition epsilon
     * @return nouvel automate minimal, accessible et déterministe
     * @throws NullPointerException     si l'automate est nul
     * @throws IllegalArgumentException si aucun état initial n'existe, si une
     *                                  transition
     *                                  epsilon est présente ou si deux arcs
     *                                  sortants se
     *                                  recouvrent sur un même caractère
     * @throws IllegalStateException    si plusieurs états sont marqués initiaux
     */
    public static Automaton minimize(Automaton dfa) {
        IndexedDfa indexed = new IndexedDfa(Objects.requireNonNull(dfa, "Le DFA ne doit pas être nul"));
        Partition partition = new Partition(indexed);
        partition.refine();
        return partition.materialize();
    }

    /**
     * Index dense de la fonction de transition complétée par un puits implicite.
     */
    private static final class IndexedDfa {
        private final State[] originalStates;
        private final int realStateCount;
        private final int stateCount;
        private final int sink;
        private final int initial;
        private final boolean[] finals;
        /**
         * Fonction de transition orientée alphabet : delta[classe][état] = destination.
         */
        private final int[][] delta;
        /** Caractères distingués globalement, dans l'ordre croissant. */
        private final char[] significant;
        /**
         * Vrai lorsqu'il existe encore au moins un caractère dans la classe « autre ».
         */
        private final boolean hasOtherClass;
        /**
         * Indice de la classe « autre », ou -1 si les 256 symboles sont distingués.
         */
        private final int otherClass;
        /** États accessibles dans le DFA complété. */
        private final boolean[] reachable;
        private final int reachableCount;
        /**
         * CSR inverse par classe : offsets[c][q]..offsets[c][q+1] dans predecessors[c].
         */
        private final int[][] offsets;
        private final int[][] predecessors;

        private static void requireByteSymbol(char symbol) {
            if (symbol >= SYMBOL_COUNT) {
                throw new IllegalArgumentException("Symbole hors alphabet 8 bits : " + (int) symbol);
            }
        }

        private IndexedDfa(Automaton dfa) {
            State start = dfa.getInitialState();
            if (start == null) {
                throw new IllegalArgumentException("Le DFA doit posséder un état initial");
            }

            originalStates = dfa.getStates().toArray(State[]::new);
            realStateCount = originalStates.length;
            sink = realStateCount;
            stateCount = realStateCount + 1;

            Map<State, Integer> ids = new IdentityHashMap<>(Math.max(16, realStateCount * 2));
            finals = new boolean[stateCount];
            for (int i = 0; i < realStateCount; i++) {
                State state = originalStates[i];
                ids.put(state, i);
                finals[i] = state.getStatus().isFinal();
            }
            Integer startId = ids.get(start);
            if (startId == null) {
                throw new IllegalArgumentException("L'état initial n'appartient pas au DFA");
            }
            initial = startId;

            BitSet significantBits = new BitSet(SYMBOL_COUNT);
            for (Transition transition : dfa.getTransitions()) {
                requireKnownEndpoints(ids, transition);
                switch (transition.getType()) {
                    case EPSILON -> throw new IllegalArgumentException(
                            "DFAM exige un automate déterministe sans transition epsilon");
                    case CHARACTER -> {
                        requireByteSymbol(transition.getSymbol());
                        significantBits.set(transition.getSymbol());
                    }
                    case ANY -> {
                        for (char excluded : transition.getExcludedSymbols()) {
                            requireByteSymbol(excluded);
                            significantBits.set(excluded);
                        }
                    }
                }
            }

            int significantCount = significantBits.cardinality();
            hasOtherClass = significantCount < SYMBOL_COUNT;
            int alphabetSize = significantCount + (hasOtherClass ? 1 : 0);
            significant = new char[significantCount];
            int[] classOf = new int[SYMBOL_COUNT];
            Arrays.fill(classOf, -1);
            int position = 0;
            for (int code = significantBits.nextSetBit(0); code >= 0; code = significantBits.nextSetBit(code + 1)) {
                significant[position] = (char) code;
                classOf[code] = position++;
            }
            otherClass = hasOtherClass ? significantCount : -1;

            char[] representatives = new char[alphabetSize];
            System.arraycopy(significant, 0, representatives, 0, significantCount);
            if (hasOtherClass) {
                int representative = significantBits.nextClearBit(0);
                representatives[otherClass] = (char) representative;
            }

            delta = new int[alphabetSize][stateCount];
            for (int[] row : delta) {
                Arrays.fill(row, -1);
            }
            for (Transition transition : dfa.getTransitions()) {
                int source = ids.get(transition.getSource());
                int destination = ids.get(transition.getDestination());
                switch (transition.getType()) {
                    case EPSILON -> throw new AssertionError("transition epsilon déjà rejetée");
                    case CHARACTER -> assign(delta[classOf[transition.getSymbol()]], source, destination);
                    case ANY -> {
                        Set<Character> excluded = transition.getExcludedSymbols();
                        for (int symbol = 0; symbol < alphabetSize; symbol++) {
                            if (!excluded.contains(representatives[symbol])) {
                                assign(delta[symbol], source, destination);
                            }
                        }
                    }
                }
            }
            for (int symbol = 0; symbol < alphabetSize; symbol++) {
                int[] transitions = delta[symbol];
                for (int state = 0; state < realStateCount; state++) {
                    if (transitions[state] < 0) {
                        transitions[state] = sink;
                    }
                }
                transitions[sink] = sink;
            }

            reachable = new boolean[stateCount];
            int[] queue = new int[stateCount];
            int head = 0;
            int tail = 0;
            reachable[initial] = true;
            queue[tail++] = initial;
            while (head < tail) {
                int state = queue[head++];
                for (int symbol = 0; symbol < alphabetSize; symbol++) {
                    int destination = delta[symbol][state];
                    if (!reachable[destination]) {
                        reachable[destination] = true;
                        queue[tail++] = destination;
                    }
                }
            }
            reachableCount = tail;

            offsets = new int[alphabetSize][];
            predecessors = new int[alphabetSize][];
            for (int symbol = 0; symbol < alphabetSize; symbol++) {
                int[] counts = new int[stateCount];
                for (int state = 0; state < stateCount; state++) {
                    if (reachable[state]) {
                        counts[delta[symbol][state]]++;
                    }
                }
                int[] symbolOffsets = new int[stateCount + 1];
                for (int state = 0; state < stateCount; state++) {
                    symbolOffsets[state + 1] = symbolOffsets[state] + counts[state];
                }
                int[] cursor = symbolOffsets.clone();
                int[] reverse = new int[reachableCount];
                for (int state = 0; state < stateCount; state++) {
                    if (reachable[state]) {
                        int destination = delta[symbol][state];
                        reverse[cursor[destination]++] = state;
                    }
                }
                offsets[symbol] = symbolOffsets;
                predecessors[symbol] = reverse;
            }
        }

        private static void requireKnownEndpoints(Map<State, Integer> ids, Transition transition) {
            if (!ids.containsKey(transition.getSource()) || !ids.containsKey(transition.getDestination())) {
                throw new IllegalArgumentException("Une transition référence un état absent du DFA");
            }
        }

        /**
         * Enregistre une case de delta et détecte tout recouvrement de classes
         * sortantes.
         */
        private static void assign(int[] transitions, int source, int destination) {
            if (transitions[source] >= 0) {
                throw new IllegalArgumentException(
                        "Automate non déterministe : plusieurs transitions acceptent le même caractère");
            }
            transitions[source] = destination;
        }
    }

    /**
     * Raffinement de Hopcroft avec blocs chaînés et worklist des plus petits
     * séparateurs.
     */
    private static final class Partition {
        private final IndexedDfa input;
        private final int[] blockOf;
        private final int[] previous;
        private final int[] next;
        private final int[] head;
        private final int[] tail;
        private final int[] size;
        private int blockCount;

        private final ArrayDeque<Integer> work = new ArrayDeque<>();
        private final boolean[] inWork;

        /** Structures temporaires réutilisées pour grouper X = pre(A,c) par bloc. */
        private final int[] touchedHead;
        private final int[] touchedNext;
        private final int[] touchedCount;
        private final int[] touchedBlocks;

        private Partition(IndexedDfa input) {
            this.input = input;
            int states = input.stateCount;
            blockOf = new int[states];
            Arrays.fill(blockOf, -1);
            previous = new int[states];
            next = new int[states];
            Arrays.fill(previous, -1);
            Arrays.fill(next, -1);

            // Au plus un bloc par état accessible ; les tableaux sont donc bornés par
            // stateCount.
            head = new int[states];
            tail = new int[states];
            size = new int[states];
            Arrays.fill(head, -1);
            Arrays.fill(tail, -1);
            inWork = new boolean[states];
            touchedHead = new int[states];
            touchedNext = new int[states];
            touchedCount = new int[states];
            touchedBlocks = new int[states];
            Arrays.fill(touchedHead, -1);
            Arrays.fill(touchedNext, -1);

            int nonFinals = createBlock();
            int finals = createBlock();
            for (int state = 0; state < states; state++) {
                if (input.reachable[state]) {
                    append(input.finals[state] ? finals : nonFinals, state);
                }
            }

            // Supprime les blocs vides de la numérotation initiale pour simplifier la
            // suite.
            if (size[nonFinals] == 0) {
                compactSingleBlock(finals);
            } else if (size[finals] == 0) {
                compactSingleBlock(nonFinals);
            } else {
                addWork(size[nonFinals] <= size[finals] ? nonFinals : finals);
            }
        }

        /**
         * Replace un unique bloc non vide en position 0 lorsque l'autre partition est
         * vide.
         */
        private void compactSingleBlock(int block) {
            if (block != 0) {
                head[0] = head[block];
                tail[0] = tail[block];
                size[0] = size[block];
                for (int state = head[0]; state >= 0; state = next[state]) {
                    blockOf[state] = 0;
                }
                head[block] = tail[block] = -1;
                size[block] = 0;
            }
            blockCount = 1;
            addWork(0);
        }

        private int createBlock() {
            int block = blockCount++;
            head[block] = -1;
            tail[block] = -1;
            size[block] = 0;
            return block;
        }

        private void append(int block, int state) {
            int last = tail[block];
            previous[state] = last;
            next[state] = -1;
            if (last >= 0) {
                next[last] = state;
            } else {
                head[block] = state;
            }
            tail[block] = state;
            size[block]++;
            blockOf[state] = block;
        }

        /**
         * Déplace un état vers un nouveau bloc en O(1), sans recopier les autres
         * membres.
         */
        private void move(int state, int destinationBlock) {
            int sourceBlock = blockOf[state];
            int before = previous[state];
            int after = next[state];
            if (before >= 0) {
                next[before] = after;
            } else {
                head[sourceBlock] = after;
            }
            if (after >= 0) {
                previous[after] = before;
            } else {
                tail[sourceBlock] = before;
            }
            size[sourceBlock]--;
            append(destinationBlock, state);
        }

        private void addWork(int block) {
            if (!inWork[block]) {
                work.addLast(block);
                inWork[block] = true;
            }
        }

        /** Exécute le raffinement jusqu'au point fixe. */
        private void refine() {
            while (!work.isEmpty()) {
                int splitter = work.removeFirst();
                inWork[splitter] = false;

                /*
                 * Le bloc peut lui-même être scindé pendant le traitement d'un symbole.
                 * Hopcroft exige pourtant que le séparateur A choisi dans la worklist
                 * reste le même ensemble pour toutes les lettres de cette itération.
                 * On fige donc une fois ses membres. La somme des tailles de ces snapshots
                 * reste O(n log n), car seuls les plus petits séparateurs sont ajoutés
                 * lorsqu'un bloc non planifié est scindé.
                 */
                int[] splitterStates = members(splitter);
                for (int symbol = 0; symbol < input.delta.length; symbol++) {
                    splitByPredecessors(splitterStates, symbol);
                }
            }
        }

        /**
         * Copie compacte des membres actuels d'un bloc, dans l'ordre de la liste
         * chaînée.
         */
        private int[] members(int block) {
            int[] states = new int[size[block]];
            int index = 0;
            for (int state = head[block]; state >= 0; state = next[state]) {
                states[index++] = state;
            }
            return states;
        }

        /**
         * Raffine tous les blocs intersectant pre(splitter, symbol).
         * Chaque prédécesseur accessible est visité une seule fois pour ce séparateur.
         */
        private void splitByPredecessors(int[] splitterStates, int symbol) {
            int touchedBlockSize = 0;
            int[] offsets = input.offsets[symbol];
            int[] predecessors = input.predecessors[symbol];

            for (int target : splitterStates) {
                for (int index = offsets[target]; index < offsets[target + 1]; index++) {
                    int state = predecessors[index];
                    int block = blockOf[state];
                    // Un prédécesseur apparaît au plus une fois pour un symbole dans un DFA.
                    if (touchedCount[block]++ == 0) {
                        touchedBlocks[touchedBlockSize++] = block;
                    }
                    touchedNext[state] = touchedHead[block];
                    touchedHead[block] = state;
                }
            }

            for (int i = 0; i < touchedBlockSize; i++) {
                int block = touchedBlocks[i];
                int intersectionSize = touchedCount[block];
                int originalSize = size[block];
                if (intersectionSize > 0 && intersectionSize < originalSize) {
                    int split = createBlock();
                    int state = touchedHead[block];
                    while (state >= 0) {
                        int following = touchedNext[state];
                        move(state, split);
                        state = following;
                    }

                    // Règle de Hopcroft : si Y était planifié, planifier les deux moitiés ;
                    // sinon seul le plus petit nouveau séparateur est nécessaire.
                    if (inWork[block]) {
                        addWork(split);
                    } else {
                        addWork(size[split] <= size[block] ? split : block);
                    }
                }
                touchedHead[block] = -1;
                touchedCount[block] = 0;
            }
        }

        /**
         * Reconstruit un DFA partiel compact depuis les classes d'équivalence finales.
         */
        private Automaton materialize() {
            boolean[] hasRealState = new boolean[blockCount];
            int[] representative = new int[blockCount];
            Arrays.fill(representative, -1);
            for (int state = 0; state < input.realStateCount; state++) {
                if (input.reachable[state]) {
                    int block = blockOf[state];
                    hasRealState[block] = true;
                    if (representative[block] < 0) {
                        representative[block] = state;
                    }
                }
            }

            int initialBlock = blockOf[input.initial];
            int[] outputIndex = new int[blockCount];
            Arrays.fill(outputIndex, -1);
            int outputCount = 0;
            outputIndex[initialBlock] = outputCount++;
            for (int block = 0; block < blockCount; block++) {
                if (block != initialBlock && hasRealState[block]) {
                    outputIndex[block] = outputCount++;
                }
            }

            Automaton result = new Automaton();
            State[] outputStates = new State[blockCount];
            for (int block = 0; block < blockCount; block++) {
                if (!hasRealState[block]) {
                    continue;
                }
                int state = representative[block];
                boolean initial = block == initialBlock;
                boolean accepting = input.finals[state];
                State output = new State("M" + outputIndex[block], status(initial, accepting));
                outputStates[block] = output;
                result.addState(output);
            }

            int significantCount = input.significant.length;
            for (int block = 0; block < blockCount; block++) {
                State source = outputStates[block];
                if (source == null) {
                    continue;
                }
                int state = representative[block];
                int baselineBlock = chooseBaselineBlock(state);
                State baseline = baselineBlock >= 0 ? outputStates[baselineBlock] : null;

                if (baseline != null) {
                    java.util.LinkedHashSet<Character> exclusions = new java.util.LinkedHashSet<>();
                    for (int symbol = 0; symbol < significantCount; symbol++) {
                        if (blockOf[input.delta[symbol][state]] != baselineBlock) {
                            exclusions.add(input.significant[symbol]);
                        }
                    }
                    result.add(Transition.anyExcept(source, baseline, exclusions));
                }

                for (int symbol = 0; symbol < significantCount; symbol++) {
                    int destinationBlock = blockOf[input.delta[symbol][state]];
                    if (destinationBlock == baselineBlock) {
                        continue;
                    }
                    State destination = outputStates[destinationBlock];
                    if (destination != null) {
                        result.add(new Transition(source, destination, input.significant[symbol]));
                    }
                }
            }
            return result;
        }

        /**
         * Choisit la cible de l'arc symbolique de base. S'il existe une classe
         * « autre », elle impose cette cible. Sinon la cible majoritaire minimise
         * le nombre de transitions littérales produites.
         */
        private int chooseBaselineBlock(int state) {
            if (input.hasOtherClass) {
                return blockOf[input.delta[input.otherClass][state]];
            }
            int[] frequencies = new int[blockCount];
            int bestBlock = -1;
            int bestCount = -1;
            for (int symbol = 0; symbol < input.delta.length; symbol++) {
                int block = blockOf[input.delta[symbol][state]];
                int count = ++frequencies[block];
                if (count > bestCount) {
                    bestCount = count;
                    bestBlock = block;
                }
            }
            return bestBlock;
        }

        private static Status status(boolean initial, boolean accepting) {
            if (initial && accepting) {
                return Status.ENTER_FINAL;
            }
            if (initial) {
                return Status.ENTER;
            }
            return accepting ? Status.FINAL : Status.INTERMEDIATE;
        }
    }
}
