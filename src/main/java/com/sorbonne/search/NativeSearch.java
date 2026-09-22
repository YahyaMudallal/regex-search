package com.sorbonne.search;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;
import com.sorbonne.regex.DFA;

/**
 * Recherche une sous-chaîne reconnue par un automate déterministe.
 *
 * <p>
 * Cette classe exécute les automates du projet :
 * elle ne délègue ni à String.contains ni au moteur regex Java. Le motif fourni
 * à {@link #search(String, Automaton)} est un DFA, tandis que KMP reçoit une
 * String littérale grâce à {@link SearchAlgorithm}.
 * </p>
 *
 * <p>
 * Un simple retour à l'état initial après un échec perd les occurrences
 * qui se chevauchent : par exemple « ab » dans « aab ». La préparation ajoute
 * donc un nouvel état initial bouclant sur tout caractère, relié par ε à
 * l'ancien départ. La déterminisation de ce graphe représente simultanément
 * tous les départs possibles. Accepter un préfixe de ce graphe revient à
 * trouver
 * une sous-chaîne reconnue par le DFA d'origine.
 * </p>
 *
 * <p>
 * <strong>Complexité :</strong> pour un motif de Q états, T arcs et X
 * exclusions,
 * la validation et la copie coûtent O(Q + T + X) en moyenne. La préparation
 * applique ensuite {@link DFA#convert} au graphe augmenté : elle peut être
 * exponentielle en Q. Si le DFA de recherche obtenu possède R états et U arcs,
 * son index occupe O(R + U + X'), où X' compte ses exclusions stockées.
 * Une recherche préparée parcourt n char en O(n) en moyenne et O(1) mémoire
 * supplémentaire. La limite linéaire en n est optimale au pire pour lire un
 * texte,
 * mais elle ne rend pas la préparation optimale ni gratuite.
 * </p>
 *
 * <p>
 * Pour plusieurs lignes, appeler {@link #prepare(Automaton)} une seule fois
 * puis {@link Prepared#search(String)} sur chacune. La méthode à deux arguments
 * prépare à chaque appel : son coût total inclut la déterminisation.
 * Les comparaisons portent sur les char UTF-16, sans normalisation ; le point
 * universel inclut les sauts de ligne. Le découpage en lignes reste à la charge
 * de l'appelant. La minimisation n'est pas réalisée ici.
 * </p>
 */
public final class NativeSearch implements SearchAlgorithm<Automaton> {
    /** Crée un moteur sans cache mutable ; coût O(1). */
    public NativeSearch() {
    }

    /**
     * Prépare le DFA puis cherche une occurrence dans le texte.
     *
     * @param text    texte non nul, éventuellement vide
     * @param pattern DFA non nul, sans ε ni ambiguïté entre ses arcs sortants
     * @return true si une sous-chaîne est reconnue, y compris le mot vide
     * @throws NullPointerException     si un argument est nul
     * @throws IllegalArgumentException si le motif n'est pas un DFA au format
     *                                  accepté
     * @throws IllegalStateException    si plusieurs états sont initiaux
     */
    @Override
    public boolean search(String text, Automaton pattern) {
        Objects.requireNonNull(text, "Le texte ne doit pas être nul");
        return prepare(pattern).search(text);
    }

    /**
     * Construit une recherche réutilisable, indépendante des futures mutations du
     * motif.
     *
     * <p>
     * Le format accepté possède au plus un arc par lettre et au plus un arc ANY
     * par état. Cet arc ANY doit exclure toutes les lettres des arcs explicites
     * du même état. C'est notamment le format produit par DFA.convert. Les
     * doublons,
     * les arcs ε et les chevauchements sont rejetés au lieu d'être écrasés.
     * </p>
     *
     * <p>
     * La copie est O(Q + T + X) ; la déterminisation supplémentaire peut être
     * exponentielle. Ne pas modifier le motif pendant cette préparation.
     * </p>
     *
     * @param pattern automate déterministe représentant les mots recherchés
     * @return objet immuable dont chaque recherche est indépendante
     * @throws NullPointerException     si pattern est nul
     * @throws IllegalArgumentException si le graphe manque d'état initial ou viole
     *                                  le format DFA
     * @throws IllegalStateException    si plusieurs états sont initiaux
     */
    public static Prepared prepare(Automaton pattern) {
        // Valider avant de copier : aucune transition invalide ne doit être ignorée.
        new Index(Objects.requireNonNull(pattern, "Le DFA ne doit pas être nul"));
        Automaton searchable = new Automaton();
        Map<State, State> copies = new HashMap<>();
        for (State state : pattern.getStates()) {
            // Les anciens rôles initiaux sont retirés, mais les rôles finaux sont
            // conservés.
            State copy = new State(state.getLabel(), state.getStatus().isFinal()
                    ? Status.FINAL
                    : Status.INTERMEDIATE);
            copies.put(state, copy);
            searchable.addState(copy);
        }
        for (Transition transition : pattern.getTransitions()) {
            State source = copies.get(transition.getSource());
            State destination = copies.get(transition.getDestination());
            searchable.add(transition.getType() == Transition.Type.CHARACTER
                    ? new Transition(source, destination, transition.getSymbol())
                    : Transition.anyExcept(source, destination, transition.getExcludedSymbols()));
        }
        // Ce nouvel état permet de commencer le motif à toutes les positions du texte.
        State scan = new State("search-start", Status.ENTER);
        searchable.add(Transition.any(scan, scan));
        searchable.add(new Transition(scan, copies.get(pattern.getInitialState())));
        return new Prepared(new Index(DFA.convert(searchable)));
    }

    /**
     * Recherche déjà préparée, sans état d'exécution partagé entre les appels.
     * Le graphe et ses index restent privés ; seule la variable locale current
     * avance.
     */
    public static final class Prepared {
        /** Index privé du DFA de recherche, construit sur une copie indépendante. */
        private final Index index;

        /**
         * Conserve l'index sans le recopier ; coût O(1).
         * 
         * @param index index validé appartenant uniquement à cette recherche
         */
        private Prepared(Index index) {
            this.index = index;
        }

        /**
         * Cherche une occurrence en un seul parcours du texte.
         *
         * <p>
         * L'état initial est testé avant de lire : un motif acceptant le mot
         * vide correspond à tout texte. Chaque caractère effectue au plus deux
         * accès aux index. Temps moyen O(n), mémoire supplémentaire O(1) ; aucun
         * tableau de copie du texte ni nouvel automate n'est créé.
         * </p>
         *
         * @param text texte non nul à examiner
         * @return true dès qu'une occurrence, même vide, est reconnue
         * @throws NullPointerException si text est nul
         */
        public boolean search(String text) {
            Objects.requireNonNull(text, "Le texte ne doit pas être nul");
            State current = index.start;
            if (index.finals.contains(current)) {
                return true;
            }
            for (int position = 0; position < text.length(); position++) {
                char symbol = text.charAt(position);
                State next = index.characters.getOrDefault(current, Map.of()).get(symbol);
                if (next == null) {
                    Transition other = index.others.get(current);
                    if (other != null && other.matches(symbol)) {
                        next = other.getDestination();
                    }
                }
                if (next == null) {
                    return false;
                }
                current = next;
                if (index.finals.contains(current)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Index interne validant un DFA puis donnant accès à ses arcs en temps moyen
     * constant.
     */
    private static final class Index {
        /** État initial unique du graphe indexé. */
        private final State start;
        /** Copie des états finaux au moment de l'indexation. */
        private final Set<State> finals;
        /** Destinations des arcs littéraux, groupées par état source et par char. */
        private final Map<State, Map<Character, State>> characters = new HashMap<>();
        /**
         * Au plus un arc complémentaire par état source, disjoint des arcs littéraux.
         */
        private final Map<State, Transition> others = new HashMap<>();

        /**
         * Valide et indexe tous les arcs, même ceux d'états inaccessibles.
         *
         * <p>
         * Temps moyen O(Q + T) et mémoire O(Q + T). La vérification des
         * exclusions utilise contains, sans recopier leurs ensembles.
         * </p>
         *
         * @param dfa graphe non nul à indexer
         * @throws IllegalArgumentException si l'état initial manque, ou si des arcs
         *                                  sont invalides
         * @throws IllegalStateException    si plusieurs états sont initiaux
         */
        private Index(Automaton dfa) {
            start = dfa.getInitialState();
            if (start == null) {
                throw new IllegalArgumentException("Le DFA doit posséder un état initial");
            }
            finals = Set.copyOf(dfa.getFinalStates());
            for (Transition transition : dfa.getTransitions()) {
                if (transition.isEpsilon()) {
                    throw new IllegalArgumentException("Un DFA ne peut pas contenir de transition ε");
                }
                if (transition.getType() == Transition.Type.ANY) {
                    if (others.putIfAbsent(transition.getSource(), transition) != null) {
                        throw new IllegalArgumentException("Plusieurs arcs ANY pour un même état");
                    }
                } else {
                    Map<Character, State> row = characters.computeIfAbsent(
                            transition.getSource(), key -> new HashMap<>());
                    if (row.putIfAbsent(transition.getSymbol(), transition.getDestination()) != null) {
                        throw new IllegalArgumentException("Plusieurs arcs pour une même lettre et un même état");
                    }
                }
            }
            for (Map.Entry<State, Map<Character, State>> row : characters.entrySet()) {
                Transition other = others.get(row.getKey());
                if (other != null) {
                    for (char symbol : row.getValue().keySet()) {
                        if (other.matches(symbol)) {
                            throw new IllegalArgumentException("Chevauchement entre un arc littéral et un arc ANY");
                        }
                    }
                }
            }
        }
    }
}
