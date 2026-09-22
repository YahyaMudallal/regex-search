package com.sorbonne;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;
import com.sorbonne.benchmark.Benchmark;
import com.sorbonne.regex.NFA;
import com.sorbonne.regex.RegexParser;
import com.sorbonne.regex.SyntaxTree;
import com.sorbonne.search.KMPSearch;
import com.sorbonne.search.SearchAlgorithm;
import java.nio.file.Path;

/**
 * Point d'entrée progressif du projet de recherche par expression régulière.
 *
 * <p>Le programme démontre les fonctionnalités disponibles : construction manuelle
 * d'un automate, comparaison des types de transition, analyse d'expressions
 * régulières en arbres syntaxiques, recherche littérale par KMP et liste
 * des fichiers texte du dossier {@code Samples}.
 * Les prochaines étapes sont annoncées à la fin de l'exécution.</p>
 *
 * <p>Le programme doit être lancé depuis la racine du projet pour accéder au
 * dossier {@code Samples} utilisé par la démonstration du benchmark.</p>
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
     * @throws Exception si le parseur rejette une expression valide de la démonstration
     */
    public static void main(String[] args) throws Exception {
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
        // PARTIE 3 — Analyse des expressions régulières et arbres syntaxiques
        // ================================================================
        demonstrateRegexParser();

        // ================================================================
        // PARTIE 4 — Recherche littérale avec KMP
        // ================================================================
        demonstrateKMPSearch();

        // ================================================================
        // PARTIE 5 — Première étape du benchmark : fichiers texte
        // ================================================================
        demonstrateBenchmark();

        // ================================================================
        // PARTIE 6 — Étapes à intégrer au fil de l'implémentation
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

        System.out.println("\nTransitions sortantes par état :");
        // Chaque liste est calculée d'après la source des arcs, pas leur ordre d'ajout.
        for (State state : automaton.getStates()) {
            System.out.println("  " + state.getLabel() + " : " + automaton.getOutgoingTransitions(state));
        }

        // Un automate peut aussi posséder un état isolé à la fois initial et final.
        Automaton emptyWordAutomaton = new Automaton();
        State initialAndFinal = new State("qVide", Status.ENTER_FINAL);
        emptyWordAutomaton.addState(initialAndFinal);
        System.out.println("\nAutre exemple : un état isolé pour représenter le mot vide.");
        System.out.println("  " + initialAndFinal.getLabel() + " : " + initialAndFinal.getStatus());
        System.out.println("  Nombre de transitions : " + emptyWordAutomaton.getTransitions().size());
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
    // ANALYSE DES EXPRESSIONS RÉGULIÈRES ET AFFICHAGE DES ARBRES
    // ====================================================================

    /**
     * Analyse plusieurs expressions et affiche les nœuds de leurs arbres syntaxiques.
     *
     * <p>Les exemples couvrent la priorité des opérateurs, les parenthèses,
     * le point universel et le point échappé. Une expression incorrecte illustre
     * ensuite le signalement d'une erreur de syntaxe.</p>
     *
     * @throws Exception si une expression valide de la démonstration est rejetée
     */
    private static void demonstrateRegexParser() throws Exception {
        printSection("3. Expressions régulières et arbres syntaxiques");
        System.out.println("Priorités : parenthèses, étoile, concaténation, alternative.");
        System.out.println("Les types des nœuds distinguent le point universel du point littéral.");

        // Expressions valides illustrant les opérateurs actuellement pris en charge.
        String[] expressions = {"ab*|c", "a(b|c)*", "a.b", "a\\.b"};
        for (String expression : expressions) {
            // Arbre construit par le parseur à partir de l'expression courante.
            SyntaxTree tree = RegexParser.parse(expression);
            System.out.println("\nExpression : " + expression);
            System.out.println("Notation compacte : " + tree);
            printSyntaxTree(tree, "  ", "racine");
        }

        // La parenthèse ouverte n'est pas fermée : le parseur doit signaler une erreur.
        String invalidExpression = "(ab";
        System.out.println("\nExpression incorrecte : " + invalidExpression);
        try {
            RegexParser.parse(invalidExpression);
        } catch (Exception error) {
            // Erreur attendue pour cet exemple ; les autres démonstrations peuvent continuer.
            System.out.println("  Erreur de syntaxe détectée : " + error.getMessage());
            return;
        }
        throw new IllegalStateException("Le parseur a accepté l'expression incorrecte : " + invalidExpression);
    }

    // ====================================================================
    // RECHERCHE DE MOTIFS LITTÉRAUX AVEC KMP
    // ====================================================================

    /**
     * Démontre la recherche KMP sur des exemples simples et quelques cas limites.
     *
     * <p>La même instance est réutilisée via {@link SearchAlgorithm}. Chaque résultat
     * est affiché avec celui de {@link String#contains(CharSequence)} comme référence.
     * Il ne s'agit pas d'un benchmark : aucun temps n'est mesuré.</p>
     */
    private static void demonstrateKMPSearch() {
        printSection("4. Recherche littérale avec KMP");
        System.out.println("KMP cherche des caractères consécutifs, en respectant la casse.");

        // Moteur concret utilisé à travers l'interface commune des recherches littérales.
        SearchAlgorithm<String> search = new KMPSearch();
        printSearchResult(search, "bonjour monsieur bienvenue", "monsieur");
        printSearchResult(search, "bonjour monsieur bienvenue", "madame");

        System.out.println("\nRépétitions et reprises après une correspondance partielle :");
        printSearchResult(search, "ababababac", "ababac");

        System.out.println("\nLe point reste littéral dans KMP :");
        printSearchResult(search, "abc", "a.c");
        printSearchResult(search, "a.c", "a.c");

        System.out.println("\nChaînes vides, casse et Unicode :");
        printSearchResult(search, "bonjour", "");
        printSearchResult(search, "", "");
        printSearchResult(search, "", "a");
        printSearchResult(search, "Bonjour", "bonjour");
        printSearchResult(search, "été 😀 hiver", "😀");
    }

    // ====================================================================
    // DÉMONSTRATION DU BENCHMARK
    // ====================================================================

    /**
     * Lance le pipeline actuel du benchmark sur le dossier {@code Samples}.
     *
     * <p>Pour le moment, il affiche seulement les noms correspondant à
     * {@code *.txt}, sans mesurer de performances. Le chemin est relatif
     * à la racine du projet, depuis laquelle le programme doit être lancé.</p>
     */
    private static void demonstrateBenchmark() {
        printSection("5. Benchmark — fichiers texte du dossier Samples");
        System.out.println("Listage des noms uniquement ; les mesures de performance restent à ajouter.");

        // Le filtre est un glob de noms de fichiers, pas une expression régulière.
        Benchmark instance = new Benchmark(Path.of("Samples"), "*.txt");
        instance.pipeline();
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
        printSection("6. Suite du projet — à implémenter");

        System.out.println("[Fait] Transformer une expression régulière en arbre syntaxique.");

        // TODO : Construire le NFA depuis l'arbre avec la méthode Aho-Ullman.
        System.out.println("[À faire] Construire l'automate non déterministe avec transitions ε.");

        // TODO : Déterminiser le NFA par la méthode des sous-ensembles.
        System.out.println("[À faire] Convertir cet automate en automate déterministe (DFA).");

        // TODO : Implémenter la minimisation dans la classe DFAM encore vide.
        System.out.println("[À faire] Minimiser le nombre d'états du DFA.");

        // TODO : Lire le motif et le chemin du fichier dans args, puis utiliser FileLoader.
        System.out.println("[À faire] Rechercher le motif et afficher les lignes correspondantes.");

        // TODO : Relier KMP à la lecture ligne par ligne et au choix de l'algorithme.
        System.out.println("[À faire] Brancher KMP sur les fichiers pour les motifs littéraux.");

        // TODO : Implémenter NativeSearch derrière l'interface SearchAlgorithm.
        System.out.println("[À faire] Ajouter NativeSearch pour comparer les recherches littérales.");

        // TODO : Comparer les résultats à egrep et mesurer les performances avec Benchmark.
        System.out.println("[À faire] Valider les résultats et comparer les performances.");
    }

    // ====================================================================
    // UTILITAIRES D'AFFICHAGE
    // ====================================================================

    /**
     * Affiche récursivement le type des nœuds et la valeur des feuilles d'un arbre.
     *
     * @param tree nœud courant, non nul
     * @param indentation espaces placés avant la ligne pour représenter la profondeur
     * @param role rôle du nœud : racine, enfant gauche ou enfant droit
     */
    private static void printSyntaxTree(SyntaxTree tree, String indentation, String role) {
        // Une lettre est affichée entre guillemets ; les opérateurs n'ont pas de valeur littérale.
        String literal = tree.getLetter() == null ? "" : " \"" + tree.getLetter() + "\"";
        System.out.println(indentation + role + " : " + tree.getNodeType() + literal);
        if (tree.getLeft() != null) {
            printSyntaxTree(tree.getLeft(), indentation + "  ", "gauche");
        }
        if (tree.getRight() != null) {
            printSyntaxTree(tree.getRight(), indentation + "  ", "droite");
        }
    }

    /**
     * Affiche le résultat réel d'une recherche et celui de la recherche native Java.
     *
     * @param search algorithme littéral utilisé, non nul
     * @param text texte de démonstration, non nul
     * @param pattern motif littéral, éventuellement vide mais non nul
     */
    private static void printSearchResult(SearchAlgorithm<String> search, String text, String pattern) {
        // Résultat de l'algorithme, calculé à chaque appel sur les données affichées.
        boolean found = search.search(text, pattern);
        System.out.printf("  Texte : \"%s\" | Motif : \"%s\" -> KMP : %s, String.contains : %s%n",
                text, pattern, found, text.contains(pattern));
    }

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
