package com.sorbonne.regex;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;
import com.sorbonne.regex.NodeType;
import com.sorbonne.regex.SyntaxTree;

import java.util.Objects;
import java.util.Set;

/**
 * Fabrique construisant un automate non déterministe avec transitions epsilon (epsilon-NFA)
 * à partir d'un arbre syntaxique abstrait, suivant la construction d'Aho-Ullman (Chapitre 10).
 */
public class NFA {

    private int stateCounter = 0;

    /**
     * Point d'entrée pour convertir un arbre syntaxique en automate avec transitions epsilon.
     *
     * @param tree l'arbre syntaxique racine, non nul
     * @return un automate équivalent possédant un unique état initial (ENTER)
     *         et un unique état d'acceptation (FINAL)
     */
    public static Automaton buildNFA(SyntaxTree tree) {
        Objects.requireNonNull(tree, "L'arbre syntaxique ne peut pas être nul");
        NFA builder = new NFA();
        return builder.build(tree);
    }

    /**
     * Construction par récursion structurelle sur les noeuds de l'arbre.
     * @param tree	l'arbre syntaxique racine, non nul.
     * @return		l'automate NFA correspondant.
     */
    private Automaton build(SyntaxTree tree) {
        NodeType type = tree.getNodeType();

        switch (type) {
            case LETTER:
                return buildLeaf(tree.getLetter().charAt(0));

            case DOT:
                return buildAnyLeaf();

            case CONCATENATION:
                return buildConcatenation(build(tree.getLeft()), build(tree.getRight()));

            case ALTERNATION:
                return buildAlternation(build(tree.getLeft()), build(tree.getRight()));

            case STAR:
                return buildStar(build(tree.getLeft()));

            case PROTECTION:
                // Le noeud PROTECTION est une enveloppe transparente pour le sous-arbre gauche
                return build(tree.getLeft());

            default:
                throw new IllegalArgumentException("Type de noeud non supporté pour la conversion : " + type);
        }
    }

    // =========================================================================
    // CAS DE BASE, les feuilles (caractères ou point)
    // =========================================================================

    /**
     * Crée l'automate pour un caractère littéral : (start) --'c'--> ((final))
     * @param symbol	La lette à crée l'automate.
     * @return			L'automate correspondant.
     */
    private Automaton buildLeaf(char symbol) {
        Automaton automaton = new Automaton();
        State start = createState(Status.ENTER);
        State accept = createState(Status.FINAL);

        automaton.add(new Transition(start, accept, symbol));
        return automaton;
    }

    /**
     * Crée l'automate pour le point universel (any) : (start) --ANY--> ((final))
     */
    private Automaton buildAnyLeaf() {
        Automaton automaton = new Automaton();
        State start = createState(Status.ENTER);
        State accept = createState(Status.FINAL);

        automaton.add(Transition.any(start, accept));
        return automaton;
    }

    // =========================================================================
    // CAS INDUCTIFS
    // =========================================================================

    /**
     * Concaténation de deux sous-automates A1 et A2 (Fig 10.28(b)):'
     * On relie l'état final de A1 à l'état initial de A2 par une transition-epsilon.
     * @param a1	un automate à concatener
     * @param a2	un automate à concatener
     * @return		l'automate final.
     */
    private Automaton buildConcatenation(Automaton a1, Automaton a2) {
        State accept1 = getUniqueFinalState(a1);
        State start2 = a2.getInitialState();

        // L'acceptant de A1 et l'initial de A2 deviennent des états intermédiaires
        accept1.setStatus(Status.INTERMEDIATE);
        start2.setStatus(Status.INTERMEDIATE);

        // creation du nouvel automate resultant
        Automaton result = new Automaton();
        mergeInto(result, a1);
        mergeInto(result, a2);

        // Transition epsilon entre la fin de A1 et le début de A2
        result.add(new Transition(accept1, start2));

        return result;
    }

    /**
     * Alternance (Union) de deux sous-automates A1 et A2 (Fig. 10.28(a)) :
     * Nouvel état initial avec transitions-epsilon vers les débuts de A1 et A2.
     * Transitions-epsilon depuis les fins de A1 et A2 vers le nouvel état final.
     */
    /**
     * Concaténation de deux sous-automates A1 et A2 (Fig 10.28(b)):'
     * On relie l'état final de A1 à l'état initial de A2 par une transition-epsilon.
     * @param a1	un automate à alterner
     * @param a2	un automate à alterner
     * @return		l'automate final.
     */
    private Automaton buildAlternation(Automaton a1, Automaton a2) {
        State start1 = a1.getInitialState();
        State accept1 = getUniqueFinalState(a1);
        State start2 = a2.getInitialState();
        State accept2 = getUniqueFinalState(a2);

        // Tous les anciens états début/fin deviennent intermédiaires
        start1.setStatus(Status.INTERMEDIATE);
        accept1.setStatus(Status.INTERMEDIATE);
        start2.setStatus(Status.INTERMEDIATE);
        accept2.setStatus(Status.INTERMEDIATE);

        // nouvelles états initial et final
        State newStart = createState(Status.ENTER);
        State newAccept = createState(Status.FINAL);

        // creation du nouvel automate resultant
        Automaton result = new Automaton();
        mergeInto(result, a1);
        mergeInto(result, a2);

        // Branchements depuis newStart
        result.add(new Transition(newStart, start1));
        result.add(new Transition(newStart, start2));

        // Ralliements vers newAccept
        result.add(new Transition(accept1, newAccept));
        result.add(new Transition(accept2, newAccept));

        return result;
    }

    /**
     * Étoile de Kleene d'un sous-automate A1 (Fig. 10.28(c)) :
     * Crée un nouveau début et une nouvelle fin reliés entre eux par epsilon (mot vide).
     * Ajoute une transition-epsilon arrière de la fin vers le début pour la boucle.
     * @param a1	automate à mettre sous etoile.
     * @return		l'automate sous etoile.
     */
    private Automaton buildStar(Automaton a1) {
        State oldStart = a1.getInitialState();
        State oldAccept = getUniqueFinalState(a1);

        // les anciens état final/inital ne le sont plus
        oldStart.setStatus(Status.INTERMEDIATE);
        oldAccept.setStatus(Status.INTERMEDIATE);

        // creation des nouveaux états final/initial 
        State newStart = createState(Status.ENTER);
        State newAccept = createState(Status.FINAL);

        // creation du nouvel automate resultant
        Automaton result = new Automaton();
        mergeInto(result, a1);

        // Court-circuit mot vide (0 occurrence)
        result.add(new Transition(newStart, newAccept));
        // Entrée dans le motif
        result.add(new Transition(newStart, oldStart));
        // Boucle arrière pour répéter le motif (1 ou plusieurs occurrences)
        result.add(new Transition(oldAccept, oldStart));
        // Sortie du motif
        result.add(new Transition(oldAccept, newAccept));

        return result;
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================

    /**
     * Crée un nouvel état avec un label unique q0, q1, ...
     * @param status	Le status de l'état (ENTER, FINAL, ...)
     * @return			L'etat crée.
     */
    private State createState(Status status) {
        return new State("q" + (stateCounter++), status);
    }

    /**
     * Copie tous les états et toutes les transitions d'une source vers une cible.
     * @param target
     * @param source
     */
    private void mergeInto(Automaton target, Automaton source) {
        for (State state : source.getStates()) {
            target.addState(state);
        }
        for (Transition transition : source.getTransitions()) {
            target.add(transition);
        }
    }

    /**
     * Récupère l'unique état final de l'automate construit selon le pattern inductif du livre.
     * @param automaton		L'automate à recuperer l'état final.
     * @return				L'etat final.
     */
    private State getUniqueFinalState(Automaton automaton) {
        Set<State> finalStates = automaton.getFinalStates();
        if (finalStates.size() != 1) {
            throw new IllegalStateException("L'automate sous-jacent doit avoir exactement un état final, trouvé : " 
                    + finalStates.size());
        }
        return finalStates.iterator().next();
    }
}