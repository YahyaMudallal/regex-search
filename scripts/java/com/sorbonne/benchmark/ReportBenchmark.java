package com.sorbonne.benchmark;

import java.nio.file.Path;

/**
 * Mesures du pipeline dans UNE JVM, avec un seul motif/stratégie par processus.
 * Les forks, paramètres fixes et validations externes sont gérés par Python.
 * Les observations sont écrites après les mesures pour éviter les IO de rapport
 * entre répétitions. Le pipeline inclut les IO du corpus et prépare à chaque appel.
 */
public final class ReportBenchmark {
    private static volatile Benchmark.Result consumed;

    private ReportBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException("file regex strategy warmups samples expected-count");
        }
        Benchmark benchmark = new Benchmark(Path.of(args[0]), args[1], Benchmark.Strategy.valueOf(args[2]));
        int warmups = Integer.parseInt(args[3]);
        int samples = Integer.parseInt(args[4]);
        long expected = Long.parseLong(args[5]);
        if (warmups < 0 || samples < 1 || expected < 0) {
            throw new IllegalArgumentException("Paramètres de mesure invalides");
        }
        Benchmark.Result[] results = new Benchmark.Result[warmups + samples];
        for (int iteration = 0; iteration < results.length; iteration++) {
            Benchmark.Result result = benchmark.pipeline();
            if (result.matchingLines() != expected) {
                throw new IllegalStateException("Comptage différent de l'oracle");
            }
            consumed = result;
            results[iteration] = result;
        }
        System.out.println("phase,iteration,matching_lines,total_lines,parsing_ns,nfa_ns,dfa_ns,minimization_ns,search_preparation_ns,preparation_ns,scan_ns,total_ns");
        for (int i = 0; i < results.length; i++) {
            Benchmark.Result result = results[i];
            Benchmark.Timings timing = result.timings();
            System.out.printf("%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    i < warmups ? "warmup" : "measure", i < warmups ? i + 1 : i - warmups + 1,
                    result.matchingLines(), result.totalLines(), timing.parsingNanos(), timing.nfaNanos(),
                    timing.dfaNanos(), timing.minimizationNanos(), timing.searchPreparationNanos(),
                    timing.preparationNanos(), timing.scanNanos(), timing.totalNanos());
        }
    }
}
