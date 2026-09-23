package com.sorbonne.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Vérifie le choix du moteur, le parcours réel des fichiers et les erreurs propagées. */
@SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
class BenchmarkTest {
    /** Dossier isolé, créé puis supprimé automatiquement par JUnit. */
    @TempDir
    Path directory;

    /** Crée les tests sans ouvrir de fichier. */
    BenchmarkTest() {
    }

    // ====================================================================
    // CAS SIMPLES — MOTIFS ET LIGNES
    // ====================================================================

    /**
     * Une ligne contenant plusieurs occurrences ne compte qu'une fois.
     * @param strategy moteur imposé ou sélection automatique
     * @throws Exception si le fichier de test ou le pipeline échoue
     */
    @ParameterizedTest
    @EnumSource(Benchmark.Strategy.class)
    void countsMatchingLines(Benchmark.Strategy strategy) throws Exception {
        Path file = write("bonjour monsieur bienvenue\nmonsieur monsieur\nmadame\n");
        Benchmark.Result result = new Benchmark(file, "monsieur", strategy).pipeline();
        assertEquals(3, result.totalLines());
        assertEquals(2, result.matchingLines());
        assertEquals(file, result.file());
        assertEquals("monsieur", result.pattern());
        assertEquals(strategy == Benchmark.Strategy.AUTO ? Benchmark.Strategy.KMP : strategy,
                result.strategy());
    }

    /**
     * Les opérateurs échappés et les parenthèses ne faussent pas la sélection du moteur.
     * @param pattern expression à interpréter
     * @param text contenu du fichier
     * @param strategy moteur attendu
     * @param matches nombre attendu de lignes correspondantes
     * @throws Exception si le pipeline échoue
     */
    @ParameterizedTest
    @MethodSource("routingCases")
    void choosesEngineFromSyntax(String pattern, String text, Benchmark.Strategy strategy, long matches)
            throws Exception {
        Benchmark.Result result = new Benchmark(write(text), pattern).pipeline();
        assertEquals(strategy, result.strategy());
        assertEquals(matches, result.matchingLines());
    }

    /**
     * Fournit des expressions couvrant concaténation, échappement, étoile, point et alternative.
     * @return expression, contenu, stratégie et nombre de lignes attendues
     */
    private static Stream<Arguments> routingCases() {
        return Stream.of(
                Arguments.of("a(b)c", "xxabc\nac\n", Benchmark.Strategy.KMP, 1L),
                Arguments.of("a\\.b", "a.b\naxb\n", Benchmark.Strategy.KMP, 1L),
                Arguments.of("\\*\\|\\(\\)", "*|()\nxxx\n", Benchmark.Strategy.KMP, 1L),
                Arguments.of("a.b", "a.b\naxb\nab\n", Benchmark.Strategy.AUTOMATON, 2L),
                Arguments.of("a|bc*", "xxa\nxxbccc\nxxx\n\n", Benchmark.Strategy.AUTOMATON, 2L),
                Arguments.of("a*", "\nbbb\naaa\n", Benchmark.Strategy.AUTOMATON, 3L));
    }

    /**
     * Le moteur repart de son état initial à chaque ligne, sans correspondance entre deux lignes.
     * @param strategy moteur à vérifier
     * @throws Exception si le pipeline échoue
     */
    @ParameterizedTest
    @EnumSource(Benchmark.Strategy.class)
    void resetsSearchAtEachLine(Benchmark.Strategy strategy) throws Exception {
        Benchmark.Result result = new Benchmark(write("a\nb\nab\na\nb"), "ab", strategy).pipeline();
        assertEquals(5, result.totalLines());
        assertEquals(1, result.matchingLines());
    }

    // ====================================================================
    // CAS INTERMÉDIAIRES — OCTETS ET LIMITES DU PARCOURS
    // ====================================================================

    /**
     * Distingue fichier vide, ligne vide, séparateurs mixtes et dernière ligne non terminée.
     * @param content texte écrit dans le fichier
     * @param lines nombre réel de lignes attendu
     * @throws Exception si la lecture échoue
     */
    @ParameterizedTest
    @MethodSource("lineCases")
    void countsLineBoundaries(String content, long lines) throws Exception {
        Benchmark.Result result = new Benchmark(write(content), "a*").pipeline();
        assertEquals(lines, result.totalLines());
        assertEquals(lines, result.matchingLines());
    }

    /**
     * Fournit les principales combinaisons de séparateurs reconnues par le scanner binaire.
     * @return texte et nombre de lignes attendues
     */
    private static Stream<Arguments> lineCases() {
        return Stream.of(Arguments.of("", 0L), Arguments.of("\n", 1L),
                Arguments.of("a\n", 1L), Arguments.of("a\n\n", 2L),
                Arguments.of("a\r\nb\rc\n\rd", 5L));
    }

    /**
     * Lit les octets de valeur haute autour d'une limite du tampon sans conversion.
     * @param strategy moteur à vérifier
     * @throws Exception si le pipeline échoue
     */
    @ParameterizedTest
    @EnumSource(Benchmark.Strategy.class)
    void handlesLongByteLines(Benchmark.Strategy strategy) throws Exception {
        byte[] prefix = new byte[65_535];
        java.util.Arrays.fill(prefix, (byte) 'x');
        byte[] suffix = new byte[] {'n','e','e','d','l','e',(byte) 0xff,'x','\n',
                (byte) 0x80,'n','e','e','d','l','e','\n','x'};
        byte[] content = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, content, 0, prefix.length);
        System.arraycopy(suffix, 0, content, prefix.length, suffix.length);
        Path file = directory.resolve("bytes.txt");
        Files.write(file, content);
        Benchmark.Result result = new Benchmark(file, "needle", strategy).pipeline();
        assertEquals(3, result.totalLines());
        assertEquals(2, result.matchingLines());
    }

    /**
     * Vérifie les relations entre mesures sans seuil temporel dépendant de la machine.
     * Les compteurs sont réinitialisés lors d'une seconde exécution.
     * @throws Exception si le pipeline échoue
     */
    @Test
    void reportsDurationsAndIndependentRuns() throws Exception {
        Benchmark benchmark = new Benchmark(write("abc\nxyz"), "abc");
        Benchmark.Result first = benchmark.pipeline();
        Benchmark.Result second = benchmark.pipeline();
        assertEquals(first.totalLines(), second.totalLines());
        assertEquals(first.matchingLines(), second.matchingLines());
        Benchmark.Timings time = second.timings();
        assertEquals(time.preparationNanos() + time.scanNanos(), time.totalNanos());
        assertTrue(time.parsingNanos() >= 0);
        assertTrue(time.searchPreparationNanos() >= 0);
        assertTrue(time.scanNanos() >= 0);
        assertTrue(time.preparationNanos() >= time.parsingNanos() + time.searchPreparationNanos());
        assertEquals(0, time.nfaNanos());
        assertEquals(0, time.dfaNanos());
        assertEquals(0, time.minimizationNanos());
    }

    /** Les témoins DFA ne paient pas Hopcroft ; les trois chemins préservent les lignes. */
    @ParameterizedTest
    @EnumSource(value = Benchmark.Strategy.class, names = {"DFA", "DFAM", "AUTOMATON"})
    void explicitAutomataPreserveRegexAndNullableShortcut(Benchmark.Strategy strategy) throws Exception {
        Path file = write("ab\nac\nxxacxx\nno\n\n");
        Benchmark.Result result = new Benchmark(file, "ab|ac", strategy).pipeline();
        assertEquals(3, result.matchingLines());
        assertEquals(strategy, result.strategy());
        if (strategy == Benchmark.Strategy.DFA) {
            assertEquals(0, result.timings().minimizationNanos());
        }
        Benchmark.Result nullable = new Benchmark(file, "(a|b)*", strategy).pipeline();
        assertEquals(5, nullable.matchingLines());
        assertEquals(0, nullable.timings().nfaNanos());
        assertEquals(0, nullable.timings().dfaNanos());
        assertEquals(0, nullable.timings().minimizationNanos());
    }

    // ====================================================================
    // ERREURS — AUCUN RÉSULTAT PARTIEL OU SILENCIEUX
    // ====================================================================

    /**
     * Refuse les configurations nulles et les expressions incompatibles ou incorrectes.
     * @throws Exception si la préparation du fichier échoue
     */
    @Test
    void rejectsInvalidConfiguration() throws Exception {
        Path file = write("abc");
        assertThrows(NullPointerException.class, () -> new Benchmark(null, "a"));
        assertThrows(NullPointerException.class, () -> new Benchmark(file, null));
        assertThrows(NullPointerException.class, () -> new Benchmark(file, "a", null));
        assertThrows(IllegalArgumentException.class,
                () -> new Benchmark(file, "a*", Benchmark.Strategy.KMP).pipeline());
        assertThrows(Exception.class, () -> new Benchmark(file, "(").pipeline());
        assertThrows(Exception.class, () -> new Benchmark(file, "").pipeline());
    }

    /** Propage les erreurs de chemin mais accepte toutes les valeurs d'octet. */
    @Test
    void propagatesIoErrorsAndAcceptsAllBytes() throws Exception {
        assertThrows(IOException.class, () -> new Benchmark(directory.resolve("absent.txt"), "a").pipeline());
        assertThrows(IOException.class, () -> new Benchmark(directory, "a").pipeline());
        Path binary = directory.resolve("bytes.bin");
        Files.write(binary, new byte[] {'a', '\n', (byte) 0xc3, 0x28, (byte) 0xff});
        Benchmark.Result result = new Benchmark(binary, ".", Benchmark.Strategy.DFA).pipeline();
        assertEquals(2, result.totalLines());
        assertEquals(2, result.matchingLines());
    }

    /**
     * Écrit le contenu exact sans ajouter de séparateur final.
     * @param content texte ASCII à écrire
     * @return chemin du fichier de test
     * @throws IOException si l'écriture échoue
     */
    private Path write(String content) throws IOException {
        return Files.writeString(directory.resolve("sample.txt"), content, StandardCharsets.US_ASCII);
    }
}
