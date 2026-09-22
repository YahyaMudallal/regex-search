package com.sorbonne;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;
import com.sorbonne.regex.RegexParser;
import com.sorbonne.regex.SyntaxTree;
import com.sorbonne.regex.NFA;

/**
 * Point d'entrée progressif du projet de recherche par expression régulière.
 *
 * <p>Le programme démontre les fonctionnalités disponibles : construction manuelle
 * d'un automate, affichage du graphe et comparaison des types de transition.
 * Les prochaines étapes sont annoncées à la fin de l'exécution.</p>
 */
public class Main {
    /** Ligne utilisée dans la console pour séparer visuellement les sections. */
    private static final String SEPARATOR = "=".repeat(64);

    /**
     * Crée une instance sans configuration.
     * Le lancement par {@link #main(String[])} ne nécessite pas d'instance.
     */
    public Main() {
    }

    /**
     * Exécute les démonstrations disponibles, puis présente les étapes à compléter.
     *
     * @param args arguments de la ligne de commande, actuellement inutilisés
     */
    public static void main(String[] args) {
        System.out.println("REGEX SEARCH — Projet DAAR");
        System.out.println("Démonstration de l'implémentation actuelle");

        // ================================================================
        // Pipeline complet à partir d'un regex
        // ================================================================
        try {
            // construction d'un arbre syntaxique à partir d'une expression régulière
        	System.out.println(" Construction d'un arbre syntaxique à partir du regex : a|bc* ");
			SyntaxTree tree = RegexParser.parse("a|bc*");
			System.out.println(tree.toString());
            
            //  construction d'un automate à partir de l'arbre syntaxique
        	System.out.println(" Construction d'un arbre syntaxique à partir du regex : a|bc* ");
            Automaton automaton = NFA.buildNFA(tree);
            System.out.println("Automate construit à partir de l'arbre syntaxique :");
            System.out.println(automaton.toString());
            
            
		} catch (Exception e) {
			e.printStackTrace();
		}        
        
        // ================================================================
        // PARTIE 1 — Construction et affichage d'un automate
        // ================================================================
        demonstrateAutomaton();

        // ================================================================
        // PARTIE 2 — Caractère littéral, point universel et transition ε
        // ================================================================
        demonstrateTransitionTypes();

        // ================================================================
        // PARTIE 3 — Étapes à intégrer au fil de l'implémentation
        // ================================================================
        printNextSteps();
    }

    // ====================================================================
    // CONSTRUCTION ET AFFICHAGE DU GRAPHE
    // ====================================================================

    /**
     * Construit un exemple d'automate et affiche ses états et ses transitions.
     *
     * <p>L'exemple représente le motif {@code ab*|c} : soit un {@code a} suivi
     * de zéro ou plusieurs {@code b}, soit un {@code c}. Le graphe est construit
     * à la main ; aucun analyseur d'expression régulière n'est encore appelé.</p>
     */
    private static void demonstrateAutomaton() {
        printSection("1. Construction manuelle d'un automate");
        System.out.println("Motif illustré : ab*|c");
        System.out.println("Exemples de mots du langage : a, ab, abb, c.");
        System.out.println("Le graphe est affiché ; la reconnaissance reste à implémenter.");

        // Graphe d'exemple contenant une branche, une boucle et une transition ε.
        Automaton automaton = createDemoAutomaton();

        System.out.println("\nÉtats (" + automaton.getStates().size() + ") :");
        // État courant, affiché avec son rôle sans les détails de son UUID.
        for (State state : automaton.getStates()) {
            System.out.println("  " + state.getLabel() + " : " + state.getStatus());
        }

        System.out.println("\nÉtat initial : " + automaton.getInitialState().getLabel());
        System.out.println("États finaux :");
        // État acceptant repéré à partir de son statut actuel.
        for (State state : automaton.getFinalStates()) {
            System.out.println("  " + state.getLabel());
        }

        System.out.println("\nTransitions (" + automaton.getTransitions().size() + ") :");
        // Arc du graphe ; son affichage indique la source, le symbole et la destination.
        for (Transition transition : automaton.getTransitions()) {
            System.out.println("  " + transition);
        }
    }

    /**
     * Crée le graphe utilisé par la démonstration du motif {@code ab*|c}.
     *
     * <p>Le premier chemin lit {@code a}, boucle sur {@code b}, puis rejoint
     * l'état final par ε. Le second chemin lit directement {@code c}.</p>
     *
     * @return nouvel automate avec trois états et quatre transitions
     */
    private static Automaton createDemoAutomaton() {
        // Point de départ commun aux deux chemins.
        State start = new State("q0", Status.ENTER);
        // État atteint après a, permettant de répéter b.
        State middle = new State("q1", Status.INTERMEDIATE);
        // État final commun aux deux chemins.
        State end = new State("q2", Status.FINAL);

        // Les états sont enregistrés automatiquement lors de l'ajout des arcs.
        Automaton automaton = new Automaton();
        automaton.add(new Transition(start, middle, 'a'));
        automaton.add(new Transition(middle, middle, 'b'));
        automaton.add(new Transition(middle, end));
        automaton.add(new Transition(start, end, 'c'));
        return automaton;
    }

    // ====================================================================
    // DÉMONSTRATION DES TYPES DE TRANSITION
    // ====================================================================

    /**
     * Compare un point littéral, un point universel et une transition ε.
     *
     * <p>Ces transitions sont indépendantes du graphe de la première partie.
     * Les appels à {@link Transition#matches(char)} vérifient un seul caractère,
     * sans effectuer de reconnaissance d'un mot complet.</p>
     */
    private static void demonstrateTransitionTypes() {
        printSection("2. Les trois types de transition");

        // Extrémités communes aux trois transitions à comparer.
        State source = new State("départ", Status.ENTER);
        State destination = new State("arrivée", Status.FINAL);
        // Attend le caractère point lui-même.
        Transition literal = new Transition(source, destination, '.');
        // Accepte un caractère quelconque.
        Transition wildcard = Transition.any(source, destination);
        // Change d'état sans lire de caractère.
        Transition epsilon = new Transition(source, destination);

        System.out.println("Point littéral : " + literal);
        System.out.println("  Accepte '.' : " + literal.matches('.'));
        System.out.println("  Accepte 'a' : " + literal.matches('a'));

        System.out.println("\nPoint universel : " + wildcard);
        System.out.println("  Accepte '.' : " + wildcard.matches('.'));
        System.out.println("  Accepte 'a' : " + wildcard.matches('a'));

        System.out.println("\nTransition ε : " + epsilon);
        System.out.println("  Ne consomme aucun caractère : " + epsilon.isEpsilon());
        System.out.println("  Accepte 'a' : " + epsilon.matches('a'));
    }

    // ====================================================================
    // PROCHAINES ÉTAPES DU PROJET
    // ====================================================================

    /**
     * Affiche les modules à intégrer aux prochaines versions du programme.
     *
     * <p>Cette liste décrit le travail restant. Elle n'appelle aucun module
     * encore vide et n'annonce aucun résultat de recherche ou de performance.</p>
     */
    private static void printNextSteps() {
        printSection("3. Suite du projet — à implémenter");

        // TODO : Ajouter une démonstration de RegexParser et SyntaxTree.
        System.out.println("[Fait] Transformer une expression régulière en arbre syntaxique.");

        // TODO : Construire le NFA depuis l'arbre avec la méthode Aho-Ullman.
        System.out.println("[À faire] Construire l'automate non déterministe avec transitions ε.");

        // TODO : Déterminiser le NFA par la méthode des sous-ensembles.
        System.out.println("[À faire] Convertir cet automate en automate déterministe (DFA).");

        // TODO : Ajouter la minimisation du DFA.
        System.out.println("[À faire] Minimiser le nombre d'états du DFA.");

        // TODO : Lire le motif et le chemin du fichier dans args, puis utiliser FileLoader.
        System.out.println("[À faire] Rechercher le motif et afficher les lignes correspondantes.");

        // TODO : Intégrer KMPSearch pour les motifs constitués de caractères littéraux.
        System.out.println("[Option]   Ajouter KMP pour les motifs simples.");

        // TODO : Comparer les résultats à egrep et mesurer les performances avec Benchmark.
        System.out.println("[À faire] Valider les résultats et comparer les performances.");
    }

    // ====================================================================
    // UTILITAIRES D'AFFICHAGE
    // ====================================================================

    /**
     * Affiche un titre entre deux séparateurs dans la console.
     *
     * @param title titre de la partie à présenter
     */
    private static void printSection(String title) {
        System.out.println();
        System.out.println(SEPARATOR);
        System.out.println(title);
        System.out.println(SEPARATOR);
    }
}
