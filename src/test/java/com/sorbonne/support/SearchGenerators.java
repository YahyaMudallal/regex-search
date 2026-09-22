package com.sorbonne.support;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;
import com.sorbonne.regex.NodeType;
import com.sorbonne.regex.SyntaxTree;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Générateurs bornés et oracles de test ; ils n'appellent ni DFA.convert ni NativeSearch. */
public final class SearchGenerators {
    /** Classe utilitaire. */
    private SearchGenerators() {
    }

    /**
     * Paire décrivant la même expression pour notre fabrique NFA et pour java.util.regex.
     * @param tree arbre construit sans utiliser RegexParser
     * @param expression expression Java équivalente, avec parenthèses explicites
     */
    public record RegexCase(SyntaxTree tree, String expression) {
    }

    /**
     * Génère une expression sur a, b, é et le point, puis union, concaténation ou étoile.
     * La profondeur limite l'explosion des sous-ensembles et le temps du moteur oracle.
     * @param random générateur initialisé par une graine reproductible
     * @param depth profondeur maximale positive ou nulle
     * @return arbre et expression représentant le même langage
     */
    public static RegexCase regex(Random random, int depth) {
        if (depth == 0 || random.nextInt(4) == 0) {
            int atom = random.nextInt(4);
            return atom == 3 ? new RegexCase(new SyntaxTree(null, null, NodeType.DOT), ".")
                    : new RegexCase(new SyntaxTree("abé".substring(atom, atom + 1)),
                            "abé".substring(atom, atom + 1));
        }
        RegexCase left = regex(random, depth - 1);
        int operator = random.nextInt(3);
        if (operator == 0) {
            return new RegexCase(new SyntaxTree(left.tree(), null, NodeType.STAR),
                    "(" + left.expression() + ")*");
        }
        RegexCase right = regex(random, depth - 1);
        return new RegexCase(new SyntaxTree(left.tree(), right.tree(),
                operator == 1 ? NodeType.ALTERNATION : NodeType.CONCATENATION),
                "(" + left.expression() + (operator == 1 ? "|" : "") + right.expression() + ")");
    }

    /**
     * Tire une chaîne de taille bornée avec répétitions et caractères spéciaux possibles.
     * @param random générateur du cas
     * @param maxLength longueur maximale incluse
     * @param alphabet unités UTF-16 autorisées, non vide
     * @return chaîne pouvant être vide
     */
    public static String text(Random random, int maxLength, String alphabet) {
        int length = random.nextInt(maxLength + 1);
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return text.toString();
    }

    /**
     * Construit un petit NFA quelconque, avec cycles, arcs parallèles et états inaccessibles possibles.
     * @param random générateur du cas
     * @return NFA de 1 à 6 états, jusqu'à 18 arcs de tous les types
     */
    public static Automaton nfa(Random random) {
        int count = 1 + random.nextInt(6);
        AutomatonBuilder builder = new AutomatonBuilder();
        for (int i = 0; i < count; i++) {
            boolean accepting = random.nextBoolean();
            builder.state("" + i, i == 0 ? (accepting ? Status.ENTER_FINAL : Status.ENTER)
                    : (accepting ? Status.FINAL : Status.INTERMEDIATE));
        }
        int arcs = random.nextInt(19);
        for (int i = 0; i < arcs; i++) {
            String from = "" + random.nextInt(count);
            String to = "" + random.nextInt(count);
            switch (random.nextInt(4)) {
                case 0 -> builder.epsilon(from, to);
                case 1 -> builder.character(from, to, "abé".charAt(random.nextInt(3)));
                case 2 -> builder.any(from, to);
                default -> builder.anyExcept(from, to, Set.of('a', 'é'));
            }
        }
        return builder.build();
    }

    /**
     * Simule un NFA par ensembles d'états et fermetures obtenues par point fixe.
     * Le choix volontaire de balayer tous les arcs diffère de l'indexation de production.
     * @param graph automate à simuler
     * @param word mot complet à reconnaître
     * @return acceptation complète du mot
     */
    public static boolean accepts(Automaton graph, String word) {
        Set<State> current = closure(graph, Set.of(graph.getInitialState()));
        for (int i = 0; i < word.length(); i++) {
            Set<State> next = new LinkedHashSet<>();
            for (Transition transition : graph.getTransitions()) {
                if (current.contains(transition.getSource()) && transition.matches(word.charAt(i))) {
                    next.add(transition.getDestination());
                }
            }
            current = closure(graph, next);
        }
        return current.stream().anyMatch(state -> state.getStatus().isFinal());
    }

    /**
     * Oracle simple de recherche : essaie explicitement toutes les sous-chaînes.
     * Réservé aux petits textes de test, sans prétention de performance.
     * @param graph automate original
     * @param text texte à examiner
     * @return true si une sous-chaîne, même vide, est acceptée
     */
    public static boolean contains(Automaton graph, String text) {
        for (int start = 0; start <= text.length(); start++) {
            for (int end = start; end <= text.length(); end++) {
                if (accepts(graph, text.substring(start, end))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Calcule les états accessibles par ε en répétant le balayage jusqu'à stabilité.
     * @param graph graphe oracle
     * @param seeds états de départ
     * @return ensemble fermé par ε
     */
    private static Set<State> closure(Automaton graph, Set<State> seeds) {
        Set<State> result = new LinkedHashSet<>(seeds);
        boolean changed;
        do {
            changed = false;
            for (Transition transition : graph.getTransitions()) {
                if (transition.isEpsilon() && result.contains(transition.getSource())) {
                    changed |= result.add(transition.getDestination());
                }
            }
        } while (changed);
        return result;
    }

    /**
     * Liste tous les petits mots d'un alphabet, y compris le mot vide.
     * @param alphabet caractères distincts à combiner
     * @param maxLength longueur maximale incluse
     * @return liste exhaustive classée par longueur
     */
    public static List<String> words(String alphabet, int maxLength) {
        List<String> all = new ArrayList<>(List.of(""));
        List<String> level = List.of("");
        for (int length = 1; length <= maxLength; length++) {
            List<String> next = new ArrayList<>();
            for (String prefix : level) {
                for (int i = 0; i < alphabet.length(); i++) {
                    next.add(prefix + alphabet.charAt(i));
                }
            }
            all.addAll(next);
            level = next;
        }
        return all;
    }
}
