package com.sorbonne.support;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.State;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builder de graphes de test nommés, indépendant du parseur et de la fabrique NFA. */
public final class AutomatonBuilder {
    /** Graphe progressivement rempli. */
    private final Automaton graph = new Automaton();
    /** Correspondance entre noms lisibles et objets états. */
    private final Map<String, State> states = new LinkedHashMap<>();

    /** Prépare un graphe vide. */
    public AutomatonBuilder() {
    }

    /**
     * Déclare un état ; deux déclarations du même nom sont interdites dans ce builder.
     * @param name nom de l'état
     * @param status rôle initial ou final
     * @return ce builder
     */
    public AutomatonBuilder state(String name, Status status) {
        if (states.containsKey(name)) {
            throw new IllegalArgumentException("État déjà déclaré : " + name);
        }
        State state = new State(name, status);
        states.put(name, state);
        graph.addState(state);
        return this;
    }

    /**
     * Ajoute un arc littéral, y compris un doublon pour tester les entrées invalides.
     * @param from source déjà déclarée
     * @param to destination déjà déclarée
     * @param symbol lettre de l'arc
     * @return ce builder
     */
    public AutomatonBuilder character(String from, String to, char symbol) {
        graph.add(new Transition(get(from), get(to), symbol));
        return this;
    }

    /**
     * Ajoute un arc sans consommation de caractère.
     * @param from source déjà déclarée
     * @param to destination déjà déclarée
     * @return ce builder
     */
    public AutomatonBuilder epsilon(String from, String to) {
        graph.add(new Transition(get(from), get(to)));
        return this;
    }

    /**
     * Ajoute un point universel.
     * @param from source déjà déclarée
     * @param to destination déjà déclarée
     * @return ce builder
     */
    public AutomatonBuilder any(String from, String to) {
        return anyExcept(from, to, Set.of());
    }

    /**
     * Ajoute un point restreint pour vérifier les classes complémentaires.
     * @param from source déjà déclarée
     * @param to destination déjà déclarée
     * @param excluded caractères exclus
     * @return ce builder
     */
    public AutomatonBuilder anyExcept(String from, String to, Set<Character> excluded) {
        graph.add(Transition.anyExcept(get(from), get(to), excluded));
        return this;
    }

    /**
     * Termine la préparation du graphe de test.
     * @return graphe construit, sans copie ni déterminisation cachée
     */
    public Automaton build() {
        return graph;
    }

    /**
     * Vérifie qu'un nom a été déclaré avant son utilisation.
     * @param name nom recherché
     * @return état correspondant
     */
    private State get(String name) {
        return Objects.requireNonNull(states.get(name), "État inconnu : " + name);
    }

    /**
     * Construit un DFA en chaîne pour une String littérale, y compris vide.
     * Aucun caractère n'est interprété comme une expression régulière.
     * @param pattern motif en symboles 8 bits
     * @return automate acceptant exactement ce mot complet
     */
    public static Automaton literal(String pattern) {
        AutomatonBuilder builder = new AutomatonBuilder();
        builder.state("0", pattern.isEmpty() ? Status.ENTER_FINAL : Status.ENTER);
        for (int i = 0; i < pattern.length(); i++) {
            builder.state("" + (i + 1), i + 1 == pattern.length() ? Status.FINAL : Status.INTERMEDIATE);
            builder.character("" + i, "" + (i + 1), pattern.charAt(i));
        }
        return builder.build();
    }
}
