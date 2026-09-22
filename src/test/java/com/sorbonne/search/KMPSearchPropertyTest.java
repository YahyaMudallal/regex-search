package com.sorbonne.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Vérifie des propriétés de KMP sur des chaînes générées, uniquement avec JUnit.
 *
 * <p>Comme avec des stratégies de génération, on définit des longueurs et des
 * alphabets, puis on vérifie une règle sur beaucoup d'exemples. Les petits alphabets
 * provoquent des répétitions ; les autres couvrent les espaces, les opérateurs
 * littéraux et les unités UTF-16, y compris les substituts isolés.</p>
 *
 * <p>Les 2 000 cas générés utilisent des graines fixes, affichées dans leur nom
 * JUnit. Chaque cas possède son propre générateur : son résultat est indépendant
 * de l'ordre des tests. Le test exhaustif vérifie en plus 945 couples sur {a, b}.</p>
 *
 * <p>Ces tests sont reproductibles mais ne réduisent pas automatiquement un cas
 * en échec à un contre-exemple minimal, contrairement à Hypothesis. Les chaînes
 * en échec sont affichées avec des échappements pour rendre les caractères invisibles lisibles.</p>
 */
class KMPSearchPropertyTest {
    /** Moteur testé, également utilisé à travers le contrat commun des recherches. */
    private final SearchAlgorithm<String> search = new KMPSearch();

    /** Graine de base constante : changer cette valeur permet d'explorer d'autres exemples. */
    private static final long BASE_SEED = 20260921L;

    /** Alphabets ciblés ; une stratégie supplémentaire couvre toutes les unités UTF-16. */
    private static final String[] ALPHABETS = {
        "a", "ab", "abc", "abc XYZ012.*|()[]\\\n\t\0éà東京😀"
    };

    /** Crée les tests de propriétés sans dépendance de génération supplémentaire. */
    KMPSearchPropertyTest() {
    }

    // ====================================================================
    // PROPRIÉTÉS VÉRIFIÉES SUR DES DONNÉES GÉNÉRÉES
    // ====================================================================

    /**
     * Compare KMP à la recherche native sur des motifs aléatoires ou extraits du texte.
     *
     * @return 1 000 tests, mêlant occurrences garanties et recherches indépendantes
     */
    @TestFactory
    @DisplayName("Propriété : même résultat que String.contains")
    Stream<DynamicTest> agreesWithContains() {
        return generatedCases("accord avec contains", 1_000, 0, random -> {
            // Texte de longueur bornée pour garder les exemples rapides et lisibles.
            String text = randomString(random, 0, 160);
            // Une partie des motifs est extraite du texte pour garantir des cas positifs.
            String pattern = random.nextBoolean()
                    ? substring(random, text) : randomString(random, 0, 180);
            assertEquals(text.contains(pattern), search.search(text, pattern),
                    () -> describe(text, pattern));
        });
    }

    /**
     * Vérifie qu'un motif inséré entre deux chaînes quelconques est toujours trouvé.
     *
     * @return 250 tests avec un motif non vide et des contextes variables
     */
    @TestFactory
    @DisplayName("Propriété : un motif inséré est présent")
    Stream<DynamicTest> findsInsertedPatterns() {
        return generatedCases("insertion", 250, 1_000, random -> {
            // Motif non vide dont on garantit explicitement l'insertion.
            String pattern = randomString(random, 1, 40);
            // Texte formé d'un préfixe, du motif et d'un suffixe.
            String text = randomString(random, 0, 100) + pattern + randomString(random, 0, 100);
            assertTrue(search.search(text, pattern), () -> describe(text, pattern));
        });
    }

    /**
     * Vérifie qu'une portion extraite d'un texte, même vide, est reconnue dans ce texte.
     *
     * @return 250 tests de sous-chaînes aux bornes variables
     */
    @TestFactory
    @DisplayName("Propriété : toute sous-chaîne extraite est présente")
    Stream<DynamicTest> findsExtractedSubstrings() {
        return generatedCases("extraction", 250, 1_250, random -> {
            // Texte pouvant être vide et contenir des unités UTF-16 quelconques.
            String text = randomString(random, 0, 200);
            // Portion consécutive du texte, pouvant aller du motif vide au texte entier.
            String pattern = substring(random, text);
            assertTrue(search.search(text, pattern), () -> describe(text, pattern));
        });
    }

    /**
     * Vérifie l'absence d'un motif contenant un symbole impossible dans le texte.
     *
     * @return 250 cas négatifs garantis, sans dépendre du résultat de String.contains
     */
    @TestFactory
    @DisplayName("Propriété : un symbole absent empêche toute occurrence")
    Stream<DynamicTest> rejectsPatternsContainingAnAbsentSymbol() {
        return generatedCases("symbole absent", 250, 1_500, random -> {
            // Le texte est limité à a, b et c : il ne peut jamais contenir #.
            String text = randomStringFromAlphabet(random, 0, 200, "abc");
            // Le marqueur interdit est placé entre deux portions variables du motif.
            String pattern = randomStringFromAlphabet(random, 0, 20, "abc")
                    + "#" + randomStringFromAlphabet(random, 0, 20, "abc");
            assertFalse(search.search(text, pattern), () -> describe(text, pattern));
        });
    }

    /**
     * Vérifie qu'entourer un texte de caractères supplémentaires conserve une occurrence.
     *
     * @return 250 tests où une occurrence connue reste présente après extension du texte
     */
    @TestFactory
    @DisplayName("Propriété : ajouter du contexte préserve une occurrence")
    Stream<DynamicTest> preservesMatchesWhenAddingContext() {
        return generatedCases("ajout de contexte", 250, 1_750, random -> {
            // Texte initial non vide, dans lequel un motif sera extrait.
            String original = randomString(random, 1, 100);
            // Choix d'une position puis d'une fin strictement après elle : motif non vide.
            int start = random.nextInt(original.length());
            int end = start + 1 + random.nextInt(original.length() - start);
            String pattern = original.substring(start, end);
            // Ajout de contexte sans supprimer ni modifier le texte d'origine.
            String extended = randomString(random, 0, 80) + original + randomString(random, 0, 80);
            assertTrue(search.search(original, pattern), () -> describe(original, pattern));
            assertTrue(search.search(extended, pattern), () -> describe(extended, pattern));
        });
    }

    /**
     * Compare tous les petits mots binaires, sans tirage aléatoire.
     *
     * <p>Les 63 textes de longueur 0 à 5 sont croisés avec les 15 motifs de longueur
     * 0 à 3 : 945 couples couvrant les vides, les chevauchements et les absences.</p>
     */
    @Test
    @DisplayName("Exhaustif : 945 couples de petites chaînes sur {a, b}")
    void agreesWithContainsForEverySmallBinaryWord() {
        // Ensemble complet des textes et motifs, y compris la chaîne vide.
        List<String> texts = binaryWordsUpTo(5);
        List<String> patterns = binaryWordsUpTo(3);
        for (String text : texts) {
            for (String pattern : patterns) {
                assertEquals(text.contains(pattern), search.search(text, pattern),
                        () -> describe(text, pattern));
            }
        }
    }

    // ====================================================================
    // GÉNÉRATEURS ET MESSAGES DE DIAGNOSTIC
    // ====================================================================

    /**
     * Crée des tests indépendants dont chacun est identifié par sa graine.
     *
     * @param name nom de la propriété
     * @param count nombre de cas à générer
     * @param seedOffset décalage évitant de partager les graines entre propriétés
     * @param property assertions à exécuter avec le générateur du cas
     * @return flux de tests dynamiques JUnit
     */
    private Stream<DynamicTest> generatedCases(String name, int count, long seedOffset,
            Consumer<Random> property) {
        return IntStream.range(0, count).mapToObj(index -> {
            // Une graine propre à ce cas évite toute dépendance à l'ordre d'exécution.
            long seed = BASE_SEED + seedOffset + index;
            return DynamicTest.dynamicTest(name + " — graine=" + seed,
                    () -> property.accept(new Random(seed)));
        });
    }

    /**
     * Choisit un alphabet ciblé ou l'ensemble des unités UTF-16, puis crée une chaîne.
     *
     * @param random générateur propre au test
     * @param minLength longueur minimale incluse
     * @param maxLength longueur maximale incluse
     * @return chaîne respectant les bornes de longueur
     */
    private static String randomString(Random random, int minLength, int maxLength) {
        // Le dernier choix représente toutes les valeurs char, de 0 à 65 535.
        int strategy = random.nextInt(ALPHABETS.length + 1);
        return randomStringFromAlphabet(random, minLength, maxLength,
                strategy == ALPHABETS.length ? null : ALPHABETS[strategy]);
    }

    /**
     * Génère une chaîne avec un alphabet et des bornes de longueur contrôlés.
     *
     * <p>Les longueurs comptent les unités UTF-16, comme String.length.
     * Un emoji de l'alphabet peut donc fournir une unité de substitution isolée,
     * ce qui est volontaire pour tester exactement le contrat des String Java.</p>
     *
     * @param random générateur pseudo-aléatoire
     * @param minLength longueur minimale incluse, positive ou nulle
     * @param maxLength longueur maximale incluse, au moins égale au minimum
     * @param alphabet caractères autorisés, non vide ; null autorise toute valeur char
     * @return chaîne générée sans modifier les bornes demandées
     */
    private static String randomStringFromAlphabet(Random random, int minLength, int maxLength,
            String alphabet) {
        // Nombre de caractères tiré uniformément dans l'intervalle demandé.
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        // Tampon recevant les unités UTF-16 une par une.
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append(alphabet == null ? (char) random.nextInt(65_536)
                    : alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return result.toString();
    }

    /**
     * Extrait une portion du texte avec des bornes valides, éventuellement identiques.
     *
     * @param random générateur du test
     * @param text texte source, éventuellement vide
     * @return sous-chaîne pouvant être vide
     */
    private static String substring(Random random, String text) {
        // Début inclus et fin exclue, comme pour String.substring.
        int start = random.nextInt(text.length() + 1);
        int end = start + random.nextInt(text.length() - start + 1);
        return text.substring(start, end);
    }

    /**
     * Construit tous les mots sur {a, b} jusqu'à la longueur maximale incluse.
     *
     * @param maxLength longueur maximale positive ou nulle
     * @return mots classés par longueur, en commençant par la chaîne vide
     */
    private static List<String> binaryWordsUpTo(int maxLength) {
        // Résultat cumulé et mots de la longueur actuellement traitée.
        List<String> all = new ArrayList<>(List.of(""));
        List<String> level = List.of("");
        for (int length = 1; length <= maxLength; length++) {
            // Chaque mot donne deux prolongements, un par lettre de l'alphabet.
            List<String> next = new ArrayList<>();
            for (String prefix : level) {
                next.add(prefix + "a");
                next.add(prefix + "b");
            }
            all.addAll(next);
            level = next;
        }
        return all;
    }

    /**
     * Décrit les entrées fautives sans laisser de caractère invisible dans le message.
     *
     * @param text texte testé
     * @param pattern motif testé
     * @return diagnostic contenant les deux chaînes échappées
     */
    private static String describe(String text, String pattern) {
        return "texte=" + escaped(text) + ", motif=" + escaped(pattern);
    }

    /**
     * Représente chaque unité UTF-16 par son code hexadécimal.
     *
     * @param value chaîne à rendre lisible dans un échec JUnit
     * @return chaîne entre guillemets, avec échappements Unicode
     */
    private static String escaped(String value) {
        // Les échappements conservent aussi les substituts isolés et caractères de contrôle.
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            result.append(String.format("\\u%04x", (int) value.charAt(index)));
        }
        return result.append('"').toString();
    }
}
