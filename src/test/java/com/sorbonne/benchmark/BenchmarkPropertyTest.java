package com.sorbonne.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sorbonne.support.SearchGenerators;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** Tests génératifs reproductibles du pipeline complet, avec Java regex comme oracle indépendant. */
class BenchmarkPropertyTest {
    /** Dossier partagé par les cas générés ; chaque graine reçoit un fichier distinct. */
    @TempDir
    Path directory;

    /** Crée la fabrique de cas sans lancer de recherche. */
    BenchmarkPropertyTest() {
    }

    /**
     * Compare les lignes trouvées sur 120 couples de fichiers et regex générés.
     * La profondeur et les tailles sont bornées pour limiter les coûts des deux moteurs.
     * Une graine affichée par JUnit permet de reproduire chaque échec.
     * @return tests dynamiques, un par graine
     */
    @TestFactory
    Stream<DynamicTest> generatedFilesAgreeWithJavaRegex() {
        return IntStream.range(0, 120).mapToObj(seed -> DynamicTest.dynamicTest("fichier/regex graine=" + seed,
                () -> checkGeneratedCase(seed)));
    }

    /**
     * Construit un fichier, calcule les résultats indépendamment puis compare AUTO et AUTOMATON.
     * Les fichiers peuvent être vides et les regex peuvent accepter le mot vide.
     * @param seed graine déterministe du cas
     * @throws Exception si le fichier ou le pipeline échoue
     */
    private void checkGeneratedCase(int seed) throws Exception {
        Random random = new Random(20_260_922L + seed);
        String expression = SearchGenerators.regex(random, 2).expression();
        List<String> lines = new ArrayList<>();
        int count = random.nextInt(16);
        for (int i = 0; i < count; i++) {
            lines.add(SearchGenerators.text(random, 50, "aabé xyz."));
        }
        // Chaque ligne reçoit un LF, y compris une dernière ligne vide.
        String content = lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
        Path file = Files.writeString(directory.resolve(seed + ".txt"), content, StandardCharsets.UTF_8);
        Pattern oracle = Pattern.compile(expression, Pattern.DOTALL);
        long expected = lines.stream().filter(line -> oracle.matcher(line).find()).count();
        for (Benchmark.Strategy strategy : List.of(Benchmark.Strategy.AUTO, Benchmark.Strategy.DFA, Benchmark.Strategy.DFAM, Benchmark.Strategy.AUTOMATON)) {
            Benchmark.Result result = new Benchmark(file, expression, strategy).pipeline();
            assertEquals(lines.size(), result.totalLines(), expression);
            assertEquals(expected, result.matchingLines(), expression + " / " + strategy);
            List<String> expectedLines = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (oracle.matcher(lines.get(i)).find()) {
                    expectedLines.add((i + 1) + ":" + lines.get(i));
                }
            }
            List<String> actualLines = new ArrayList<>();
            new Benchmark(file, expression, strategy).forEachMatchingLine(
                    (number, line) -> actualLines.add(number + ":" + line));
            assertEquals(expectedLines, actualLines, expression + " / lignes / " + strategy);
        }
    }
}
