package com.sorbonne.regex;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Déterminise un automate par la construction des sous-ensembles avec fermeture ε.
 *
 * <p>Chaque état produit représente un ensemble d'états du NFA. Il est final dès
 * qu'un de ces états est final. Seuls les ensembles accessibles sont construits.
 * Le résultat est un DFA partiel : une transition absente signifie un rejet,
 * sans création obligatoire d'un état puits. La minimisation est une étape distincte.</p>
 *
 * <p>Les lettres explicites et les exclusions des arcs ANY découpent l'alphabet
 * en classes disjointes : un singleton par lettre, puis « tous les autres ».
 * Un déplacement sur une lettre réunit les arcs littéraux ET les arcs ANY qui
 * l'acceptent. La classe restante utilise {@link Transition#anyExcept} : aucune
 * priorité artificielle entre une lettre et le point universel n'est nécessaire.</p>
 *
 * <p><strong>Complexité :</strong> N états, E arcs, X exclusions stockées en entrée,
 * K classes de caractères et R sous-ensembles accessibles, avec R ≤ 2^N.
 * L'indexation coûte O(N + E + X). Chaque couple (sous-ensemble, classe) parcourt
 * au plus les états et arcs du NFA pour le déplacement et la fermeture :
 * O(N + E + X + R K (N + E)) en temps moyen avec les tables de hachage.
 * La mémoire supplémentaire est O(N + E + X + R(N + K)) : index, ensembles
 * mémorisés et graphe produit. L'ensemble des exclusions de sortie est partagé.</p>
 *
 * <p>L'explosion exponentielle peut être inévitable pour un DFA équivalent.
 * Cette implémentation évite les parcours complets des arcs pour chaque état,
 * mais ne prétend pas être optimale pour toute famille d'automates. L'entrée
 * n'est pas modifiée et ne doit pas être modifiée pendant la conversion.</p>
 */
public final class DFA {
    /** Fabrique statique : aucune instance à construire. */
    private DFA() {
    }

    /**
     * Construit un DFA reconnaissant exactement les mêmes mots complets que l'entrée.
     *
     * <p>Coût et notations : voir la documentation de la classe. Tous les char Java
     * sont pris en charge, sans développer un point en 65 536 arcs.</p>
     *
     * @param nfa automate non nul, avec exactement un état initial
     * @return nouveau graphe sans ε ni chevauchement d'étiquettes sortantes
     * @throws NullPointerException si nfa est nul
     * @throws IllegalArgumentException si aucun état initial n'existe
     * @throws IllegalStateException si plusieurs états sont initiaux
     */
    public static Automaton convert(Automaton nfa) {
        Objects.requireNonNull(nfa, "Le NFA ne doit pas être nul");
        State initial = nfa.getInitialState();
        if (initial == null) {
            throw new IllegalArgumentException("Le NFA doit posséder un état initial");
        }

        // Index calculé une seule fois ; getOutgoingTransitions reparcourrait tous les arcs.
        Map<State, List<Transition>> outgoing = new HashMap<>();
        // Lettres séparées explicitement ; les exclusions des ANY y participent aussi.
        Set<Character> alphabet = new LinkedHashSet<>();
        boolean hasAny = false;
        for (Transition transition : nfa.getTransitions()) {
            outgoing.computeIfAbsent(transition.getSource(), key -> new ArrayList<>()).add(transition);
            if (transition.getType() == Transition.Type.CHARACTER) {
                alphabet.add(transition.getSymbol());
            } else if (transition.getType() == Transition.Type.ANY) {
                hasAny = true;
                alphabet.addAll(transition.getExcludedSymbols());
            }
        }
        // Copie immuable commune aux arcs « autres caractères » du résultat.
        Set<Character> exclusions = Set.copyOf(alphabet);
        List<Character> representatives = new ArrayList<>(alphabet);
        if (hasAny) {
            // Tous les char hors alphabet ont le même comportement dans le NFA.
            for (int code = Character.MIN_VALUE; code <= Character.MAX_VALUE; code++) {
                if (!alphabet.contains((char) code)) {
                    representatives.add((char) code);
                    break;
                }
            }
        }

        Automaton dfa = new Automaton();
        // Les clés sont immuables : leur hachage reste stable pendant la construction.
        Map<Set<State>, State> known = new HashMap<>();
        Queue<Set<State>> pending = new ArrayDeque<>();
        Set<State> startSet = epsilonClosure(outgoing, Set.of(initial));
        register(dfa, known, pending, startSet, true);

        while (!pending.isEmpty()) {
            Set<State> current = pending.remove();
            State source = known.get(current);
            for (char symbol : representatives) {
                Set<State> next = epsilonClosure(outgoing, move(outgoing, current, symbol));
                if (next.isEmpty()) {
                    continue; // DFA partiel : l'ensemble vide correspond à un rejet.
                }
                State destination = register(dfa, known, pending, next, false);
                dfa.add(alphabet.contains(symbol)
                        ? new Transition(source, destination, symbol)
                        : Transition.anyExcept(source, destination, exclusions));
            }
        }
        return dfa;
    }

    /**
     * Suit tous les arcs ε accessibles, en visitant chaque état au plus une fois.
     * Les cycles ε terminent grâce à l'ensemble des états déjà vus.
     *
     * @param outgoing index des arcs par source
     * @param seeds états de départ
     * @return fermeture immuable ; temps O(N + E), mémoire O(N) au pire
     */
    private static Set<State> epsilonClosure(Map<State, List<Transition>> outgoing, Set<State> seeds) {
        Set<State> result = new LinkedHashSet<>(seeds);
        Deque<State> stack = new ArrayDeque<>(seeds);
        while (!stack.isEmpty()) {
            State state = stack.pop();
            for (Transition transition : outgoing.getOrDefault(state, List.of())) {
                if (transition.isEpsilon() && result.add(transition.getDestination())) {
                    stack.push(transition.getDestination());
                }
            }
        }
        return Set.copyOf(result);
    }

    /**
     * Réunit toutes les destinations possibles après la lecture d'un char.
     * Les arcs ANY compatibles contribuent même si un arc littéral existe.
     *
     * @param outgoing index des arcs par source
     * @param current ensemble actuellement représenté
     * @param symbol caractère représentatif de la classe à lire
     * @return destinations avant fermeture ε ; temps O(N + E), mémoire O(N) au pire
     */
    private static Set<State> move(Map<State, List<Transition>> outgoing, Set<State> current, char symbol) {
        Set<State> result = new LinkedHashSet<>();
        for (State state : current) {
            for (Transition transition : outgoing.getOrDefault(state, List.of())) {
                if (transition.matches(symbol)) {
                    result.add(transition.getDestination());
                }
            }
        }
        return result;
    }

    /**
     * Réutilise un état connu ou programme le traitement d'un nouvel ensemble.
     * Coût moyen O(N) : hachage de l'ensemble et lecture de ses statuts finaux.
     *
     * @param dfa graphe en construction
     * @param known association entre ensembles immuables et états du résultat
     * @param pending ensembles dont les arcs restent à construire
     * @param represented ensemble immuable à enregistrer
     * @param initial vrai uniquement pour l'ensemble initial
     * @return état existant ou nouvel état ajouté au graphe
     */
    private static State register(Automaton dfa, Map<Set<State>, State> known,
            Queue<Set<State>> pending, Set<State> represented, boolean initial) {
        State existing = known.get(represented);
        if (existing != null) {
            return existing;
        }
        boolean accepting = represented.stream().anyMatch(state -> state.getStatus().isFinal());
        Status status = initial ? (accepting ? Status.ENTER_FINAL : Status.ENTER)
                : (accepting ? Status.FINAL : Status.INTERMEDIATE);
        State created = new State("D" + known.size(), status);
        known.put(represented, created);
        pending.add(represented);
        dfa.addState(created);
        return created;
    }
}
