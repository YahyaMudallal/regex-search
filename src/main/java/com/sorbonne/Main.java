package com.sorbonne;

import java.nio.file.Path;
import java.util.Locale;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;
import com.sorbonne.benchmark.Benchmark;
import com.sorbonne.regex.DFA;
import com.sorbonne.regex.DFAMMoore;
import com.sorbonne.regex.NFA;
import com.sorbonne.regex.RegexParser;
import com.sorbonne.regex.SyntaxTree;
import com.sorbonne.search.KMPSearch;
import com.sorbonne.search.NativeSearch;
import com.sorbonne.search.SearchAlgorithm;

/**
 * Point d'entrée progressif du projet de recherche par expression régulière.
 *
 * <p>
 * Le programme démontre les fonctionnalités disponibles : construction manuelle
 * d'un automate, comparaison des types de transition, analyse d'expressions
 * régulières en arbres syntaxiques, construction NFA puis DFA, recherche par
 * automate,
 * recherche littérale par KMP et benchmark sur un fichier texte de
 * {@code Samples}.
 * Les prochaines étapes sont annoncées à la fin de l'exécution.
 * </p>
 *
 * <p>
 * Le programme doit être lancé depuis la racine du projet pour accéder au
 * dossier {@code Samples} utilisé par la démonstration du benchmark.
 * </p>
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
     * @param args aucun pour les démonstrations, ou fichier, regex et stratégie
     *             optionnelle
     *             (AUTO, KMP ou AUTOMATON) pour une mesure seule ; préfixer par
     *             --count
     *             pour afficher uniquement le nombre de lignes correspondantes, ou
     *             par
     *             --print pour afficher les lignes correspondantes avec leur numéro
     * @throws Exception si le motif est invalide ou si la lecture du fichier échoue
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            boolean countOnly = "--count".equals(args[0]);
            boolean printLines = "--print".equals(args[0]);
            int offset = countOnly || printLines ? 1 : 0;
            int remaining = args.length - offset;
            if (remaining < 2 || remaining > 3) {
                throw new IllegalArgumentException(
                        "Usage : Main [--count|--print] <fichier> <regex> [AUTO|KMP|AUTOMATON]");
            }
            Benchmark.Strategy strategy = remaining == 3
                    ? Benchmark.Strategy.valueOf(args[offset + 2].toUpperCase(Locale.ROOT))
                    : Benchmark.Strategy.AUTO;
            Benchmark benchmark = new Benchmark(Path.of(args[offset]), args[offset + 1], strategy);
            if (printLines) {
                benchmark.forEachMatchingLine((lineNumber, line) -> System.out.println(lineNumber + ":" + line));
                return;
            }
            Benchmark.Result result = benchmark.pipeline();
            if (countOnly) {
                System.out.println(result.matchingLines());
            } else {
                printBenchmarkResult(result);
            }
            return;
        }
        System.out.println("REGEX SEARCH — Projet DAAR");
        System.out.println("Démonstration de l'implémentation actuelle");

        // ================================================================
        // Pipeline complet à partir d'un regex
        // ================================================================
        demonstrateRegexPipeline();

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
        // PARTIE 5 — Benchmark : préparation et parcours d’un fichier texte
        // ================================================================
        demonstrateBenchmark();

        // ================================================================
        // PARTIE 6 — Étapes à intégrer au fil de l'implémentation
        // ================================================================
        printNextSteps();
    }

    // ====================================================================
    // EXPRESSION RÉGULIÈRE → NFA → DFA → DFAM → RECHERCHE
    // ====================================================================

    /**
     * Construit un motif automate puis réutilise sa préparation sur plusieurs
     * textes.
     *
     * <p>
     * La préparation est séparée de la lecture pour ne pas déterminer un nouvel
     * automate à chaque ligne. Le DFA représente des mots complets ; NativeSearch
     * prépare la recherche d'une occurrence à n'importe quelle position.
     * </p>
     *
     * @throws Exception si l'expression de démonstration ne peut pas être analysée
     */
    private static void demonstrateRegexPipeline() throws Exception {
        printSection("Pipeline regex : arbre → NFA → DFA → DFAM → recherche");
        // Motif combinant une alternative, une concaténation et une étoile.
        String expression = "a|bc*";
        SyntaxTree tree = RegexParser.parse(expression);
        Automaton nfa = NFA.buildNFA(tree);
        Automaton dfa = DFA.convert(nfa);
        // Automaton minimized = DFAMMoore.minimize(dfa, false);
        System.out.println("Expression : " + expression);
        System.out.println("Arbre : " + tree);
        System.out.println("NFA :\n" + nfa);
        System.out.println("DFA :\n" + dfa);
        // System.out.println("DFAM :\n" + minimized);

        // La préparation peut être coûteuse ; elle est partagée entre toutes les
        // lignes.
        System.out.println("DFAM : étape provisoire, le DFA est conservé tel quel.");
        NativeSearch.Prepared prepared = NativeSearch.prepare(dfa);
        for (String text : new String[] { "xxa", "xxbccc", "xxx", "" }) {
            System.out.printf("  Texte : \"%s\" -> occurrence : %s%n", text, prepared.search(text));
        }
    }

    // ====================================================================
    // CONSTRUCTION ET AFFICHAGE DU GRAPHE
    // ====================================================================

    /**
     * Construit un exemple d'automate et affiche ses états et ses transitions.
     *
     * <p>
     * L'exemple représente le motif {@code ab*|c} : soit un {@code a} suivi
     * de zéro ou plusieurs {@code b}, soit un {@code c}. Le graphe est construit
     * à la main ; aucun analyseur d'expression régulière n'est encore appelé.
     * </p>
     */
    private static void demonstrateAutomaton() {
        printSection("1. Construction manuelle d'un automate");
        System.out.println("Motif illustré : ab*|c");
        System.out.println("Exemples de mots du langage : a, ab, abb, c.");
        System.out.println("Ce graphe est construit manuellement ; le pipeline précédent montre la recherche par DFA.");

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
        // Arc du graphe ; son affichage indique la source, le symbole et la
        // destination.
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
     * <p>
     * Le premier chemin lit {@code a}, boucle sur {@code b}, puis rejoint
     * l'état final par ε. Le second chemin lit directement {@code c}.
     * </p>
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
     * <p>
     * Ces transitions sont indépendantes du graphe de la première partie.
     * Les appels à {@link Transition#matches(char)} vérifient un seul caractère,
     * sans effectuer de reconnaissance d'un mot complet.
     * </p>
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
     * Analyse plusieurs expressions et affiche les nœuds de leurs arbres
     * syntaxiques.
     *
     * <p>
     * Les exemples couvrent la priorité des opérateurs, les parenthèses,
     * le point universel et le point échappé. Une expression incorrecte illustre
     * ensuite le signalement d'une erreur de syntaxe.
     * </p>
     *
     * @throws Exception si une expression valide de la démonstration est rejetée
     */
    private static void demonstrateRegexParser() throws Exception {
        printSection("3. Expressions régulières et arbres syntaxiques");
        System.out.println("Priorités : parenthèses, étoile, concaténation, alternative.");
        System.out.println("Les types des nœuds distinguent le point universel du point littéral.");

        // Expressions valides illustrant les opérateurs actuellement pris en charge.
        String[] expressions = { "ab*|c", "a(b|c)*", "a.b", "a\\.b" };
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
            // Erreur attendue pour cet exemple ; les autres démonstrations peuvent
            // continuer.
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
     * <p>
     * La même instance est réutilisée via {@link SearchAlgorithm}. Chaque résultat
     * est affiché avec celui de {@link String#contains(CharSequence)} comme
     * référence.
     * Il ne s'agit pas d'un benchmark : aucun temps n'est mesuré.
     * </p>
     */
    private static void demonstrateKMPSearch() {
        printSection("4. Recherche littérale avec KMP");
        System.out.println("KMP cherche des caractères consécutifs, en respectant la casse.");

        // Moteur concret utilisé à travers l'interface commune des recherches
        // littérales.
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
     * Mesure deux moteurs sur le même mot, puis une expression avec alternative.
     * Ces mesures uniques illustrent le pipeline ; elles ne constituent pas
     * une comparaison statistique, notamment à cause de l'échauffement et du cache.
     * 
     * @throws Exception si le fichier d'exemple est inaccessible ou le motif
     *                   invalide
     */
    private static void demonstrateBenchmark() throws Exception {
        printSection("5. Benchmark — un fichier texte, lu ligne par ligne");
        Path file = Path.of("Samples", "PrideAndPrejudice.txt");
        printBenchmarkResult(new Benchmark(file, "Elizabeth").pipeline());
        printBenchmarkResult(new Benchmark(file, "Elizabeth", Benchmark.Strategy.AUTOMATON).pipeline());
        printBenchmarkResult(new Benchmark(file, "Elizabeth|Darcy").pipeline());
    }

    /**
     * Présente une mesure terminée ; aucun affichage ne perturbe les chronomètres.
     * Les nanosecondes sont converties en millisecondes uniquement pour la lecture.
     * 
     * @param result compteurs et durées d'une exécution complète
     */
    private static void printBenchmarkResult(Benchmark.Result result) {
        Benchmark.Timings time = result.timings();
        System.out.printf("%nFichier : %s%nRegex : %s | Moteur : %s%n",
                result.file(), result.pattern(), result.strategy());
        System.out.printf("Lignes correspondantes : %d / %d%n", result.matchingLines(), result.totalLines());
        System.out.printf("Préparation : %.3f ms (analyse : %.3f ; moteur : %.3f)%n",
                time.preparationNanos() / 1_000_000.0, time.parsingNanos() / 1_000_000.0,
                time.searchPreparationNanos() / 1_000_000.0);
        if (result.strategy() == Benchmark.Strategy.AUTOMATON) {
            System.out.printf("  NFA : %.3f ms | DFA : %.3f ms | DFAM provisoire : %.3f ms%n",
                    time.nfaNanos() / 1_000_000.0, time.dfaNanos() / 1_000_000.0,
                    time.minimizationNanos() / 1_000_000.0);
            System.out.println("  Minimisation non implémentée : DFAM renvoie le même automate.");
        }
        System.out.printf("Lecture + recherche (IO incluses) : %.3f ms | Total : %.3f ms%n",
                time.scanNanos() / 1_000_000.0, time.totalNanos() / 1_000_000.0);
        System.out.println("Mesure unique ; échauffement et répétitions nécessaires pour comparer les performances.");
    }

    // ====================================================================
    // PROCHAINES ÉTAPES DU PROJET
    // ====================================================================

    /**
     * Affiche les modules à intégrer aux prochaines versions du programme.
     *
     * <p>
     * Cette liste décrit le travail restant. Elle n'appelle aucun module
     * encore vide et n'annonce aucun résultat de recherche ou de performance.
     * </p>
     */
    private static void printNextSteps() {
        printSection("6. Suite du projet — à implémenter");

        System.out.println("[Fait] Transformer une expression régulière en arbre syntaxique.");

        System.out.println("[Fait] Construire le NFA avec transitions ε.");

        System.out.println("[Fait] Déterminiser le NFA en DFA avec classes de caractères disjointes.");

        // TODO : Implémenter la minimisation dans la classe DFAM à la place du
        // placeholder.
        System.out.println("[À faire] Minimiser le nombre d'états du DFA.");

        System.out.println("[Fait] Afficher les lignes correspondantes hors du benchmark chronométré.");

        // Le choix automatique repose sur l’arbre, pour respecter les caractères
        // échappés.
        System.out.println("[Fait] Lire un fichier bufferisé et choisir KMP pour les motifs littéraux.");

        System.out.println("[Fait] Rechercher avec NativeSearch et partager le contrat typé avec KMP.");

        // TODO : Échauffer, répéter, calculer les statistiques et comparer à egrep.
        System.out.println("[À faire] Répéter les mesures et comparer les résultats et performances à egrep.");
    }

    // ====================================================================
    // UTILITAIRES D'AFFICHAGE
    // ====================================================================

    /**
     * Affiche récursivement le type des nœuds et la valeur des feuilles d'un arbre.
     *
     * @param tree        nœud courant, non nul
     * @param indentation espaces placés avant la ligne pour représenter la
     *                    profondeur
     * @param role        rôle du nœud : racine, enfant gauche ou enfant droit
     */
    private static void printSyntaxTree(SyntaxTree tree, String indentation, String role) {
        // Une lettre est affichée entre guillemets ; les opérateurs n'ont pas de valeur
        // littérale.
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
     * Affiche le résultat réel d'une recherche et celui de la recherche native
     * Java.
     *
     * @param search  algorithme littéral utilisé, non nul
     * @param text    texte de démonstration, non nul
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
