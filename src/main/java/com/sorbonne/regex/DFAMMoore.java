package com.sorbonne.regex;

import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Transition;
import com.sorbonne.automata.Status;

/**
 * Fabrique construisant un automate déterministe minimal à partir d'un
 * automate déterministe existant, selon l'algorithme de Moore.
 */
public final class DFAMMoore {
    /** Empêche l'instanciation de cette classe utilitaire. */
    private DFAMMoore() {
    }

    /**
     * Minimise un automate déterministe avec l'algorithme de Moore.
     * 
     * La partition initiale sépare les états finaux des autres. À chaque étape,
     * les états d'un même bloc sont séparés lorsque leurs transitions, pour un
     * symbole donné, aboutissent dans des blocs différents. L'algorithme
     * s'arrête lorsque la partition devient stable.
     * 
     * Complexité : O(n²) en temps et O(n) en mémoire, avec n le nombre d'états.
     * 
     * @param dfa automate déterministe non nul à minimiser
     * @param debug active le mode debug
     * @return l'automate minimisé
     * @throws NullPointerException si l'automate est nul
     */
    public static Automaton minimize(Automaton dfa, boolean debug) {
        Objects.requireNonNull(dfa, "Le DFA ne doit pas être nul");

        // alphabet ASCII
        List<Character> alphabet = new ArrayList<>(128);
        for (int i = 0; i < 128; i++) {
            alphabet.add((char) i);
        }

        State initialState = dfa.getInitialState();
        Objects.requireNonNull(initialState, "Le DFA doit posséder un état initial");
        
        /*
        * ------------------------------------------------------------
        * Construction de la fonction delta
        * ------------------------------------------------------------
        *
        * delta[state][c] = état destination
        *
        * Une valeur null signifie qu'il n'existe aucune transition
        * pour ce caractère.
        */
        Map<State, Map<Character, State>> delta = new HashMap<>();
        for (State state : dfa.getStates()) {
            Map<Character, State> transitions = new HashMap<>();
            for (char c : alphabet) {
                State destination = null;
                for (Transition transition : dfa.getOutgoingTransitions(state)) {
                    if (transition.matches(c)) {
                        destination = transition.getDestination();
                        break;
                    }
                }
                transitions.put(c, destination);
            }

            delta.put(state, transitions);
        }

        /*
        * ------------------------------------------------------------
        * État puits virtuel
        * ------------------------------------------------------------
        *
        * Un DFA peut être partiel dans notre représentation.
        *
        * Pour Moore, toutes les transitions manquantes doivent être
        * considérées comme allant vers un même état non final :
        *
        *                 ┌──────────────┐
        *                 │              ▼
        * q ----------- aucun --------> SINK
        *                                │
        *                                │ tous les caractères
        *                                ▼
        *                               SINK
        *
        * Le SINK est utilisé uniquement pendant la minimisation.
        */
        State sink = null;
        boolean needsSink = false;
        for (State state : dfa.getStates()) {
            for (char c : alphabet) {
                if (delta.get(state).get(c) == null) {
                    needsSink = true;
                    break;
                }
            }

            if (needsSink) {
                break;
            }
        }
        if (needsSink) {
            sink = new State("__MOORE_SINK__", Status.INTERMEDIATE);
            Map<Character, State> sinkTransitions = new HashMap<>();
            for (char c : alphabet) {
                sinkTransitions.put(c, sink);
            }
            delta.put(sink, sinkTransitions);
        }

        // Ensemble des états utilisés par Moore
        Set<State> allStates = new HashSet<>(dfa.getStates());
        if (sink != null) {
            allStates.add(sink);
        }

        // Partition initiale :  P0 = { états finaux ; états non finaux }
        Set<State> finalStates = new HashSet<>(dfa.getFinalStates());
        Set<State> nonFinalStates = new HashSet<>(allStates);
        nonFinalStates.removeAll(finalStates);
        List<Set<State>> partitions = new ArrayList<>();
        if (!finalStates.isEmpty()) {
            partitions.add(new HashSet<>(finalStates));
        }
        if (!nonFinalStates.isEmpty()) {
            partitions.add(new HashSet<>(nonFinalStates));
        }

        /*
        * ------------------------------------------------------------
        * Raffinement de Moore
        * ------------------------------------------------------------
        */
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Set<State>> newPartitions = new ArrayList<>();
            /*
            * Pour chaque état, on veut connaître le bloc auquel
            * appartient l'état atteint par chaque caractère.
            *
            * Exemple :
            *      P = { {A,B}, {C,D} }
            *      A --0--> B
            *
            * alors la signature contient :
            *      bloc(B) = 0
            *
            * et non pas B lui-même.
            */
            Map<State, Integer> stateToBlockId = new HashMap<>();
            for (int blockId = 0; blockId < partitions.size(); blockId++) {
                Set<State> block = partitions.get(blockId);
                for (State state : block) {
                    stateToBlockId.put(state, blockId);
                }
            }

            /*
            * Chaque bloc actuel est éventuellement séparé en plusieurs
            * sous-blocs selon les signatures de ses états.
            */
            for (Set<State> block : partitions) {
                // Un bloc singleton ne peut plus être séparé.
                if (block.size() <= 1) {
                    newPartitions.add(new HashSet<>(block));
                    continue;
                }

                /*
                * Signature -> états ayant cette signature
                * Une signature est :
                *      (bloc(delta(q, '\0')),
                *       bloc(delta(q, '\1')),
                *       ...
                *       bloc(delta(q, '\127')))
                */
                Map<List<Integer>, Set<State>> subBlocks = new HashMap<>();
                for (State state : block) {
                    List<Integer> signature = new ArrayList<>(alphabet.size());
                    for (char c : alphabet) {
                        State nextState = delta.get(state).get(c);
                        /*
                        * Avec le sink, nextState ne devrait être null
                        * que dans le cas théorique où l'état lui-même
                        * n'aurait pas de delta. On utilise -1 par
                        * sécurité.
                        */
                        int nextBlockId;
                        if (nextState == null) {
                            nextBlockId = -1;
                        } else {
                            Integer blockId = stateToBlockId.get(nextState);
                            Objects.requireNonNull(blockId, "État absent de la partition : " 
                                + nextState.getLabel());
                            nextBlockId = blockId;
                        }
                        signature.add(nextBlockId);
                    }

                    // Chaque signature identique regroupe les états dans un sous-bloc.
                    subBlocks.computeIfAbsent(
                        signature,
                        ignored -> new HashSet<>())
                    .add(state);
                }

                // Plusieurs signatures => le bloc doit être séparé.
                if (subBlocks.size() > 1) {
                    changed = true;
                }
                newPartitions.addAll(subBlocks.values());
            }
            partitions = newPartitions;

            if (debug) {
                System.out.println("Partition :");
                for (int i = 0; i < partitions.size(); i++) {
                    System.out.println("  B" + i + " = " + partitions.get(i));
                }
                System.out.println();
            }
        }

        //  Pour chaque état original, on mémorise le bloc auquel il appartient.
        Map<State, Set<State>> stateToBlock = new HashMap<>();

        for (Set<State> block : partitions) {
            for (State state : block) {
                stateToBlock.put(state, block);
            }
        }

        /*
        * ------------------------------------------------------------
        * Création des états du DFA minimal
        * ------------------------------------------------------------
        *
        * Le sink virtuel n'est pas forcément conservé.
        *
        * Si un bloc contient uniquement le sink, il représente un état
        * puits qui n'est jamais nécessaire pour reconnaître le langage
        * dans notre représentation d'automate partiel.
        *
        * En revanche, si le sink a fusionné avec un véritable état
        * original, le bloc doit évidemment être conservé.
        */
        Map<Set<State>, State> blockToNewState = new HashMap<>();
        Automaton minimized = new Automaton();
        for (Set<State> block : partitions) {

            boolean containsOriginalState = false;

            for (State state : block) {
                if (state != sink) {
                    containsOriginalState = true;
                    break;
                }
            }

            /*
            * Bloc contenant uniquement le sink virtuel :
            * on ne crée pas cet état dans l'automate final.
            */
            if (!containsOriginalState) {
                continue;
            }

            /*
            * Détermine si le bloc est final.
            *
            * Tous les états d'un bloc final doivent être finaux,
            * et tous les états d'un bloc non final doivent être non finaux.
            */
            boolean isFinal = false;
            for (State state : block) {
                if (state != sink && state.getStatus().isFinal()) {
                    isFinal = true;
                    break;
                }
            }

            // Le bloc contenant l'ancien état initial devient initial.
            boolean isInitial = block.contains(initialState);
            Status status;
            if (isInitial && isFinal) {
                status = Status.ENTER_FINAL;
            } else if (isInitial) {
                status = Status.ENTER;
            } else if (isFinal) {
                status = Status.FINAL;
            } else {
                status = Status.INTERMEDIATE;
            }

            /*
            * Nom du nouvel état.
            * Exemple :
            *      [q0,q2,q5]
            */
            StringBuilder label = new StringBuilder("[");
            boolean first = true;
            for (State state : block) {
                if (state == sink) {
                    continue;
                }
                if (!first) {
                    label.append(",");
                }
                label.append(state.getLabel());
                first = false;
            }
            label.append("]");
            State newState = new State(label.toString(), status);
            blockToNewState.put(block, newState);
            minimized.addState(newState);
        }

        /*
        * ------------------------------------------------------------
        * Reconstruction des transitions
        * ------------------------------------------------------------
        *
        * Pour chaque bloc B et chaque caractère c :
        *
        *      B --c--> B'
        *
        * si les états du bloc ont une transition vers B'.
        *
        * Comme on a un DFA minimisé, tous les états d'un même bloc
        * ont nécessairement la même destination modulo les blocs.
        */
        Set<String> createdTransitions = new HashSet<>();

        for (Set<State> block : partitions) {
            State newSource = blockToNewState.get(block);
            //  Bloc sink-only ignoré.
            if (newSource == null) {
                continue;
            }

            //  On prend un représentant original du bloc.
            State representative = null;
            for (State state : block) {
                if (state != sink) {
                    representative = state;
                    break;
                }
            }
            if (representative == null) {
                continue;
            }
            for (char c : alphabet) {
                // On récupère l'état destination du représentant pour ce caractère.
                State destination = delta.get(representative).get(c);
                if (destination == null) {
                    continue;
                }

                // On récupère le bloc de destination et l'état minimisé correspondant.
                Set<State> destinationBlock = stateToBlock.get(destination);
                Objects.requireNonNull(destinationBlock, "État absent de la partition : "
                    + destination.getLabel());

                State newDestination = blockToNewState.get(destinationBlock);

                /*
                * Transition vers le sink virtuel uniquement :
                * on peut laisser la transition absente.
                */
                if (newDestination == null) {
                    continue;
                }

                /*
                * Évite de créer 128 transitions CHARACTER identiques
                * lorsqu'un ANY pourrait représenter le même comportement.
                *
                * Pour l'instant, on construit simplement des transitions
                * ASCII explicites. Le DFA reste parfaitement valide.
                */
                String key = System.identityHashCode(newSource)
                    + ":"
                    + System.identityHashCode(newDestination)
                    + ":"
                    + c;

                if (createdTransitions.add(key)) {
                    minimized.add(
                        new Transition(
                            newSource,
                            newDestination,
                            c));
                }
            }
        }

        return minimized;
    }    
}


