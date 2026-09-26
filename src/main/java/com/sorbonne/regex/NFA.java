package com.sorbonne.regex;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;

/**
 * Construction de Thompson/Aho-Ullman dans un graphe commun.
 * Chaque nœud ajoute O(1) états/arcs : O(m) temps moyen et mémoire pour m nœuds.
 * Le parcours postordre est itératif, y compris pour les arbres déséquilibrés.
 */
public class NFA {
    private final Automaton automaton = new Automaton();
    private int stateCounter;

    /** Extrémités d'un fragment ; aucun sous-graphe n'est recopié. */
    
    /**
     * Fragment
     * Représente un fragment d'automate avec un état de départ et un état d'acceptation.
     * Chaque fragment est indépendant et possède une unique entrée et une unique sortie.
     * 
     * @param start l'état de départ du fragment
     * @param accept l'état d'acceptation du fragment
     */
    private record Fragment(State start, State accept) {
    }

    /**
     * Visit
     * Représente une visite dans l'arbre syntaxique pendant la construction de l'automate.
     * 
     * @param tree l'arbre syntaxique à visiter
     * @param expanded true si le nœud a déjà été étendu, false sinon
     */
    private record Visit(SyntaxTree tree, boolean expanded) {
    }

    /**
     * Construit un automate non déterministe (NFA) à partir d'un arbre syntaxique.
     * 
     * @param tree arbre non nul
     * @return graphe indépendant, avec une unique entrée et une unique sortie
     */
    public static Automaton buildNFA(SyntaxTree tree) {
        Objects.requireNonNull(tree, "L'arbre syntaxique ne peut pas être nul");
        return new NFA().build(tree);
    }

    /**
     * Construit un automate non déterministe (NFA) à partir d'un arbre syntaxique.
     *  
     * @param tree  l'arbre syntaxique à partir duquel construire le NFA
     * @return      l'automate non déterministe construit à partir de l'arbre syntaxique
     */
    private Automaton build(SyntaxTree tree) {
        Deque<Visit> visits = new ArrayDeque<>();
        Deque<Fragment> fragments = new ArrayDeque<>();
        visits.push(new Visit(tree, false));
        while (!visits.isEmpty()) {
            Visit visit = visits.pop();
            SyntaxTree node = visit.tree();
            NodeType type = node.getNodeType();
            if (!visit.expanded()) {
                switch (type) {
                    case LETTER, DOT -> {
                        State start = createState();
                        State accept = createState();
                        automaton.add(type == NodeType.DOT ? Transition.any(start, accept)
                                : new Transition(start, accept, node.getLetter().charAt(0)));
                        fragments.push(new Fragment(start, accept));
                    }
                    case CONCATENATION, ALTERNATION, STAR, PROTECTION -> {
                        visits.push(new Visit(node, true));
                        if (type == NodeType.CONCATENATION || type == NodeType.ALTERNATION) {
                            visits.push(new Visit(Objects.requireNonNull(node.getRight()), false));
                        }
                        visits.push(new Visit(Objects.requireNonNull(node.getLeft()), false));
                    }
                    default -> throw new IllegalArgumentException("Type de nœud non supporté : " + type);
                }
                continue;
            }
            if (type == NodeType.PROTECTION) {
                continue;
            }
            Fragment right = fragments.pop();
            if (type == NodeType.CONCATENATION) {
                Fragment left = fragments.pop();
                epsilon(left.accept(), right.start());
                fragments.push(new Fragment(left.start(), right.accept()));
            } else {
                State start = createState();
                State accept = createState();
                epsilon(start, right.start());
                epsilon(right.accept(), accept);
                if (type == NodeType.STAR) {
                    epsilon(start, accept);
                    epsilon(right.accept(), right.start());
                } else {
                    Fragment left = fragments.pop();
                    epsilon(start, left.start());
                    epsilon(left.accept(), accept);
                }
                fragments.push(new Fragment(start, accept));
            }
        }
        Fragment result = fragments.pop();
        result.start().setStatus(Status.ENTER);
        result.accept().setStatus(Status.FINAL);
        return automaton;
    }

    /**
     * Crée un nouvel état dans l'automate.
     * 
     * @return l'état créé
     */
    private State createState() {
        State state = new State("q" + stateCounter++, Status.INTERMEDIATE);
        automaton.addState(state);
        return state;
    }

    /**
     * Ajoute une transition epsilon entre deux états.
     * 
     * @param source      l'état de départ
     * @param destination l'état d'arrivée
     */
    private void epsilon(State source, State destination) {
        automaton.add(new Transition(source, destination));
    }
}
