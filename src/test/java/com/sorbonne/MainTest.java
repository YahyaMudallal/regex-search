package com.sorbonne;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Vérifie la sortie de comptage utilisée par les scripts, sans dépendre de
 * l'affichage humain.
 */
@ResourceLock("SYSTEM_OUT")
class MainTest {
    /** Répertoire isolé pour les fichiers passés au point d'entrée. */
    @TempDir
    Path directory;

    /** Crée les tests de l'interface en ligne de commande. */
    MainTest() {
    }

    /**
     * Chaque stratégie affiche uniquement le compte, sans titre ni chronométrage.
     * 
     * @throws Exception si le fichier ou le pipeline échoue
     */
    @Test
    void countModePrintsOnlyMatchingLineCount() throws Exception {
        Path file = Files.writeString(directory.resolve("texte avec espaces.txt"), "ab ab\nx\nab");
        for (String strategy : new String[] { "AUTO", "KMP", "DFA", "DFAM", "AUTOMATON" }) {
            assertEquals("2" + System.lineSeparator(), capture("--count", file.toString(), "ab", strategy));
        }
    }

    /**
     * Aucun résultat reste une exécution réussie qui affiche zéro.
     * 
     * @throws Exception si la recherche échoue
     */
    @Test
    void countModeSupportsNoMatchesAndEmptyFile() throws Exception {
        Path file = Files.writeString(directory.resolve("empty.txt"), "");
        assertEquals("0" + System.lineSeparator(), capture("--count", file.toString(), "a*"));
        Files.writeString(file, "bbb\nccc");
        assertEquals("0" + System.lineSeparator(), capture("--count", file.toString(), "a"));
    }

    /** L'aide CLI doit être accessible sans fichier ni compilation de benchmark. */
    @Test
    void helpDescribesModesAndStrategies() throws Exception {
        String help = capture("--help");
        org.junit.jupiter.api.Assertions.assertTrue(help.contains("--count"));
        org.junit.jupiter.api.Assertions.assertTrue(help.contains("--print"));
        org.junit.jupiter.api.Assertions.assertTrue(help.contains("AUTOMATON"));
    }

    /**
     * Vérifie qu'une commande incomplète n'est pas exécutée comme une
     * démonstration.
     */
    @Test
    void countModeRequiresFileAndPattern() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            assertEquals(2, Main.runCommand(new String[] { "--count" }, out, err));
        }
        org.junit.jupiter.api.Assertions.assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Usage :"));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
    }

    /** Les erreurs CLI attendues restent concises et ne produisent pas de stack trace. */
    @Test
    void invalidStrategyReturnsUsageError() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            assertEquals(2, Main.runCommand(new String[] { "fichier.txt", "abc", "INCONNUE" }, out, err));
        }
        String message = errors.toString(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("Stratégie inconnue"));
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("AUTO, KMP, DFA, DFAM, AUTOMATON"));
        org.junit.jupiter.api.Assertions.assertFalse(message.contains("Exception"));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
    }

    /**
     * Vérifie que le mode d'affichage produit uniquement les lignes et leurs
     * numéros.
     */
    @Test
    void printModeDisplaysMatchingLinesWithNumbers() throws Exception {
        Path file = Files.writeString(directory.resolve("print.txt"), "avant\nabc abc\napres\nabc\n");

        assertEquals("2:abc abc" + System.lineSeparator() + "4:abc" + System.lineSeparator(),
                capture("--print", file.toString(), "abc", "KMP"));
    }

    /**
     * Vérifie le chemin automate du mode d'affichage et l'absence de sortie sans
     * correspondance.
     */
    @Test
    void printModeSupportsAutomatonAndNoMatch() throws Exception {
        Path file = Files.writeString(directory.resolve("print-regex.txt"), "ab\naxb\nccc\n");

        assertEquals("2:axb" + System.lineSeparator(),
                capture("--print", file.toString(), "a.b", "AUTOMATON"));
        assertEquals("", capture("--print", file.toString(), "zz"));
    }

    /**
     * Capture temporairement stdout, puis restaure systématiquement le flux
     * partagé.
     * 
     * @param arguments paramètres de Main
     * @return sortie exacte du programme
     * @throws Exception si Main signale une erreur
     */
    private static String capture(String... arguments) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            Main.main(arguments);
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
