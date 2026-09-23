package com.sorbonne.benchmark;

import java.io.BufferedReader;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import com.sorbonne.automata.Automaton;
import com.sorbonne.regex.DFA;
import com.sorbonne.regex.DFAMMoore;
import com.sorbonne.regex.NFA;
import com.sorbonne.regex.RegexParser;
import com.sorbonne.regex.SyntaxTree;
import com.sorbonne.search.KMPSearch;
import com.sorbonne.search.NativeSearch;
import com.sorbonne.utils.FileLoader;

/**
 * Mesure une recherche ligne par ligne dans un seul fichier texte UTF-8.
 *
 * <p>
 * Le motif est une expression régulière du langage de {@link RegexParser},
 * pas un filtre de noms de fichiers. En mode automatique, une concaténation
 * de lettres utilise KMP ; les autres expressions passent par NFA, DFA,
 * {@link DFAMMoore#minimize(Automaton)} puis NativeSearch. La minimisation est
 * encore
 * une étape provisoire qui rend le même automate.
 * </p>
 *
 * <p>
 * Chaque appel prépare une seule fois le moteur puis parcourt le fichier sans
 * conserver les lignes déjà lues. Une ligne correspondante compte une fois,
 * quel que soit son nombre d'occurrences. Aucune recherche ne traverse une fin
 * de ligne. Aucun affichage n'est effectué pendant les mesures.
 * </p>
 *
 * <p>
 * Il s'agit d'une mesure individuelle dans une JVM déjà démarrée.
 * L'échauffement,
 * les répétitions, les statistiques et la comparaison à grep restent à
 * orchestrer.
 * Le cache du système et le ramasse-miettes peuvent influencer les durées.
 * </p>
 */
public final class Benchmark {
    // ====================================================================
    // CONFIGURATION ET RÉSULTATS
    // ====================================================================

    /**
     * Choix du moteur, pour comparer notamment KMP et les automates sur un même
     * mot.
     */
    public enum Strategy {
        /** Choisit KMP uniquement si l'arbre représente un motif littéral. */
        AUTO,
        /** Impose KMP ; refuse les expressions comportant un opérateur non littéral. */
        KMP,
        /** Impose les automates, y compris pour un motif littéral. */
        AUTOMATON
    }

    /** Consommateur d'une ligne correspondante, avec son numéro dans le fichier. */
    @FunctionalInterface
    public interface LineConsumer {
        /**
         * Traite une ligne sans imposer de stockage de tout le résultat.
         * 
         * @param lineNumber numéro de ligne, à partir de 1
         * @param line       contenu sans séparateur de ligne
         * @throws Exception si l'écriture ou le traitement échoue
         */
        void accept(long lineNumber, String line) throws Exception;
    }

    /** Fichier à lire ; son existence sera vérifiée à l'ouverture. */
    private final Path file;
    /** Expression régulière interprétée selon la syntaxe du parseur du projet. */
    private final String pattern;
    /** Stratégie demandée, résolue après l'analyse du motif. */
    private final Strategy strategy;

    /**
     * Configure une mesure avec sélection automatique du moteur.
     * 
     * @param file    fichier texte UTF-8, non nul
     * @param pattern expression régulière non nulle, validée à l'exécution
     */
    public Benchmark(Path file, String pattern) {
        this(file, pattern, Strategy.AUTO);
    }

    /**
     * Configure une mesure sans ouvrir le fichier ni préparer le motif.
     * 
     * @param file     fichier texte UTF-8, non nul
     * @param pattern  expression régulière non nulle
     * @param strategy stratégie demandée, non nulle
     * @throws NullPointerException si un argument est nul
     */
    public Benchmark(Path file, String pattern, Strategy strategy) {
        this.file = Objects.requireNonNull(file, "Le fichier ne doit pas être nul");
        this.pattern = Objects.requireNonNull(pattern, "Le motif ne doit pas être nul");
        this.strategy = Objects.requireNonNull(strategy, "La stratégie ne doit pas être nulle");
    }

    /**
     * Durées en nanosecondes mesurées avec l'horloge monotone System.nanoTime.
     * Les étapes non exécutées valent zéro. Le total est préparation + parcours.
     * 
     * @param parsingNanos           analyse syntaxique
     * @param nfaNanos               construction du NFA, ou zéro avec KMP
     * @param dfaNanos               déterminisation, ou zéro avec KMP
     * @param minimizationNanos      appel du placeholder DFAM, pas une véritable
     *                               minimisation
     * @param searchPreparationNanos table KMP ou préparation de la recherche par
     *                               automate
     * @param preparationNanos       préparation entière, incluant le choix du
     *                               moteur
     * @param scanNanos              ouverture, lecture, décodage, recherche,
     *                               comptage et fermeture
     * @param totalNanos             durée globale, hors création du résultat et
     *                               affichage
     */
    public record Timings(long parsingNanos, long nfaNanos, long dfaNanos,
            long minimizationNanos, long searchPreparationNanos,
            long preparationNanos, long scanNanos, long totalNanos) {
    }

    /**
     * Résultat immuable d'un parcours complet ; aucune ligne de texte n'est
     * conservée.
     * 
     * @param file          fichier parcouru
     * @param pattern       expression régulière demandée
     * @param strategy      moteur réellement utilisé, jamais AUTO
     * @param totalLines    nombre de lignes lues
     * @param matchingLines nombre de lignes contenant au moins une occurrence
     * @param timings       durées de cette exécution
     */
    public record Result(Path file, String pattern, Strategy strategy,
            long totalLines, long matchingLines, Timings timings) {
    }

    // ====================================================================
    // PRÉPARATION UNIQUE, PUIS PARCOURS BUFFERISÉ
    // ====================================================================

    /**
     * Prépare le motif, lit le fichier et compte les lignes correspondantes.
     *
     * <p>
     * Pour C unités UTF-16 réparties sur L lignes, le parcours prend
     * O(C + L) avec KMP, et en moyenne avec les tables de NativeSearch.
     * La mémoire du parcours est O(B + M), où B est le tampon et M la longueur
     * de la plus grande ligne, en plus du moteur préparé. KMP prépare sa table
     * en O(m) ; le coût du parseur reste distinct. Les déterminisations peuvent
     * produire un nombre exponentiel d'états : la préparation par automate
     * n'est donc pas annoncée comme linéaire.
     * </p>
     *
     * <p>
     * Le parcours inclut les entrées-sorties : il ne mesure pas isolément
     * l'algorithme de recherche. Une erreur est propagée, sans résultat partiel.
     * Les appels successifs repartent de zéro et rouvrent le fichier.
     * </p>
     *
     * @return compteurs et durées du parcours complet
     * @throws Exception                si la syntaxe est invalide, notamment vide,
     *                                  ou si la lecture échoue
     * @throws IllegalArgumentException si KMP est imposé à une expression non
     *                                  littérale
     */
    public Result pipeline() throws Exception {
        long start = System.nanoTime();
        SyntaxTree tree = RegexParser.parse(pattern);
        long parsingNanos = System.nanoTime() - start;
        Optional<String> literal = literalPattern(tree);
        Strategy selected = strategy == Strategy.AUTO
                ? (literal.isPresent() ? Strategy.KMP : Strategy.AUTOMATON)
                : strategy;

        // Les étapes propres aux automates restent à zéro pour le chemin KMP.
        long nfaNanos = 0;
        long dfaNanos = 0;
        long minimizationNanos = 0;
        long searchPreparationNanos;
        Predicate<String> search;
        if (selected == Strategy.KMP) {
            String word = literal.orElseThrow(
                    () -> new IllegalArgumentException("KMP exige une concaténation de caractères littéraux"));
            long phaseStart = System.nanoTime();
            KMPSearch.Prepared prepared = KMPSearch.prepare(word);
            search = prepared::search;
            searchPreparationNanos = System.nanoTime() - phaseStart;
        } else {
            long phaseStart = System.nanoTime();
            Automaton nfa = NFA.buildNFA(tree);
            nfaNanos = System.nanoTime() - phaseStart;
            phaseStart = System.nanoTime();
            Automaton dfa = DFA.convert(nfa);
            dfaNanos = System.nanoTime() - phaseStart;
            phaseStart = System.nanoTime();
            // Automaton minimized = DFAMMoore.minimize(dfa, false);
            minimizationNanos = System.nanoTime() - phaseStart;
            phaseStart = System.nanoTime();
            NativeSearch.Prepared prepared = NativeSearch.prepare(dfa);
            search = prepared::search;
            searchPreparationNanos = System.nanoTime() - phaseStart;
        }

        long scanStart = System.nanoTime();
        long totalLines = 0;
        long matchingLines = 0;
        try (BufferedReader reader = FileLoader.open(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalLines++;
                if (search.test(line)) {
                    matchingLines++;
                }
            }
        }
        long end = System.nanoTime();
        return new Result(file, pattern, selected, totalLines, matchingLines,
                new Timings(parsingNanos, nfaNanos, dfaNanos, minimizationNanos,
                        searchPreparationNanos, scanStart - start, end - scanStart, end - start));
    }

    /**
     * Parcourt le fichier et transmet chaque ligne correspondante au consommateur.
     * Le motif est préparé une seule fois et les lignes ne sont jamais accumulées.
     * 
     * @param consumer traitement d'une ligne correspondante et de son numéro
     * @throws Exception si le motif, le fichier ou le consommateur échoue
     */
    public void forEachMatchingLine(LineConsumer consumer) throws Exception {
        Objects.requireNonNull(consumer, "Le consommateur ne doit pas être nul");
        SyntaxTree tree = RegexParser.parse(pattern);
        Optional<String> literal = literalPattern(tree);
        Strategy selected = strategy == Strategy.AUTO
                ? (literal.isPresent() ? Strategy.KMP : Strategy.AUTOMATON)
                : strategy;
        Predicate<String> search;
        if (selected == Strategy.KMP) {
            String word = literal.orElseThrow(
                    () -> new IllegalArgumentException("KMP exige une concaténation de caractères littéraux"));
            search = KMPSearch.prepare(word)::search;
        } else {
            Automaton nfa = NFA.buildNFA(tree);
            Automaton dfa = DFA.convert(nfa);
            // Automaton minimized = DFAMMoore.minimize(dfa, false);
            search = NativeSearch.prepare(dfa)::search;
        }

        try (BufferedReader reader = FileLoader.open(file)) {
            long lineNumber = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (search.test(line)) {
                    consumer.accept(lineNumber, line);
                }
            }
        }
    }

    /**
     * Extrait les lettres d'une concaténation, après décodage par le parseur.
     * Ainsi {@code a\.b} est littéral, mais {@code a.b} contient un point
     * universel.
     * Le parcours itératif coûte O(n) en temps et au plus O(n) en mémoire
     * pour n nœuds, dont les feuilles portent chacune une unité UTF-16.
     * 
     * @param tree arbre produit par le parseur
     * @return mot littéral, ou absence si un opérateur exige les automates
     */
    private static Optional<String> literalPattern(SyntaxTree tree) {
        StringBuilder word = new StringBuilder();
        Deque<SyntaxTree> pending = new ArrayDeque<>();
        pending.push(tree);
        while (!pending.isEmpty()) {
            SyntaxTree node = pending.pop();
            switch (node.getNodeType()) {
                case LETTER -> word.append(node.getLetter());
                case CONCATENATION -> {
                    pending.push(node.getRight());
                    pending.push(node.getLeft());
                }
                case PROTECTION -> pending.push(node.getLeft());
                default -> {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(word.toString());
    }
}
