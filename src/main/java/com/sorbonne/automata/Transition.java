package com.sorbonne.automata;

import java.util.Objects;
import java.util.Set;

/**
 * Représente un arc orienté entre deux états d'un automate.
 *
 * <p>Une transition peut lire un caractère précis, lire n'importe quel caractère
 * (point universel), ou ne lire aucun caractère (transition ε).
 * Le point littéral {@code '.'} et le point universel sont deux cas distincts.</p>
 *
 * <p>Les extrémités, le type et le symbole sont fixés à la construction.
 * Les objets {@link State} référencés restent toutefois modifiables.
 * Une transition peut revenir sur son état source : elle forme alors une boucle.</p>
 */
public class Transition {
    /** Indique ce que la transition consomme lors de la lecture du texte. */
    public enum Type {
        /** Consomme uniquement le caractère littéral stocké dans la transition. */
        CHARACTER,

        /** Ne consomme aucun caractère ; représente un déplacement ε. */
        EPSILON,

        /** Consomme un caractère quelconque sauf les exclusions éventuelles. */
        ANY
    }

    /** État de départ, non nul ; la référence reste fixe. */
    private final State source;

    /** État d'arrivée, non nul ; peut être le même objet que la source. */
    private final State destination;

    /** Mode de lecture de cette transition : caractère précis, ε ou caractère quelconque. */
    private final Type type;

    /** Caractère à lire pour {@link Type#CHARACTER}, ou {@code null} pour les autres types. */
    private final Character symbol;

    /** Ensemble immuable de caractères exclus d'un arc ANY ; vide pour le point universel. */
    private final Set<Character> excludedSymbols;

    /**
     * Crée une transition ε, qui change d'état sans consommer de caractère.
     *
     * @param source état de départ, non nul
     * @param destination état d'arrivée, non nul
     * @throws NullPointerException si l'un des deux états est nul
     */
    public Transition(State source, State destination) {
        this(source, destination, Type.EPSILON, null);
    }

    /**
     * Crée une transition qui lit exactement le caractère fourni.
     *
     * <p>Le caractère {@code '.'} est traité comme un point littéral.
     * Utiliser {@link #any(State, State)} pour créer un point universel.</p>
     *
     * @param source état de départ, non nul
     * @param destination état d'arrivée, non nul
     * @param symbol caractère littéral que la transition doit consommer
     * @throws NullPointerException si l'un des deux états est nul
     */
    public Transition(State source, State destination, char symbol) {
        this(source, destination, Type.CHARACTER, symbol);
    }

    /**
     * Initialise les informations communes aux trois types de transition.
     *
     * <p>Les constructeurs publics et la fabrique {@link #any(State, State)}
     * garantissent la cohérence entre le type et le symbole.</p>
     *
     * @param source état de départ, non nul
     * @param destination état d'arrivée, non nul
     * @param type mode de lecture choisi par l'appelant interne, non nul
     * @param symbol caractère pour {@link Type#CHARACTER}, sinon {@code null}
     * @throws NullPointerException si l'un des deux états est nul
     */
    private Transition(State source, State destination, Type type, Character symbol) {
        this(source, destination, type, symbol, Set.of());
    }

    /**
     * Initialise un arc, en copiant ses exclusions pour empêcher leur modification.
     * Coût O(x) pour x exclusions, sauf si l'ensemble immuable est réutilisable.
     *
     * @param source état de départ non nul
     * @param destination état d'arrivée non nul
     * @param type type fixé par les fabriques internes
     * @param symbol symbole littéral ou null
     * @param excludedSymbols caractères exclus, sans valeur nulle
     */
    private Transition(State source, State destination, Type type, Character symbol,
            Set<Character> excludedSymbols) {
        this.source = Objects.requireNonNull(source, "L'état source est obligatoire");
        this.destination = Objects.requireNonNull(destination, "L'état destination est obligatoire");
        this.type = type;
        this.symbol = symbol;
        this.excludedSymbols = Set.copyOf(excludedSymbols);
    }

    /**
     * Crée une transition correspondant au point universel de l'expression régulière.
     *
     * @param source état de départ, non nul
     * @param destination état d'arrivée, non nul
     * @return nouvelle transition de type {@link Type#ANY}
     * @throws NullPointerException si l'un des deux états est nul
     */
    public static Transition any(State source, State destination) {
        return new Transition(source, destination, Type.ANY, null);
    }

    /**
     * Crée un arc acceptant tout caractère sauf ceux explicitement exclus.
     *
     * <p>Il représente la classe « autres caractères » d'un DFA. Par exemple,
     * un arc pour 'a' et un arc excluant 'a' sont disjoints : aucun ordre de
     * priorité implicite n'est nécessaire. Coût O(x) pour x exclusions.</p>
     *
     * @param source état de départ non nul
     * @param destination état d'arrivée non nul
     * @param excludedSymbols caractères à exclure, ensemble non nul sans éléments nuls
     * @return nouvel arc ANY muni d'exclusions immuables
     * @throws NullPointerException si un argument requis ou une exclusion est nul
     */
    public static Transition anyExcept(State source, State destination, Set<Character> excludedSymbols) {
        return new Transition(source, destination, Type.ANY, null, excludedSymbols);
    }

    /**
     * Renvoie les exclusions de l'arc, sans copie ni modification possible.
     *
     * @return ensemble immuable, vide pour un point universel sans restriction ; coût O(1)
     */
    public Set<Character> getExcludedSymbols() {
        return excludedSymbols;
    }

    /**
     * Renvoie l'état depuis lequel cette transition peut être suivie.
     *
     * @return état source, jamais nul
     */
    public State getSource() {
        return source;
    }

    /**
     * Renvoie l'état atteint après cette transition.
     *
     * @return état destination, jamais nul
     */
    public State getDestination() {
        return destination;
    }

    /**
     * Renvoie le mode de lecture de la transition.
     *
     * @return type fixé à la construction, jamais nul
     */
    public Type getType() {
        return type;
    }

    /**
     * Renvoie le caractère précis attendu, si la transition en possède un.
     *
     * @return caractère littéral, ou {@code null} pour ε et le point universel
     */
    public Character getSymbol() {
        return symbol;
    }

    /**
     * Indique si cette transition peut être suivie sans lire de caractère.
     *
     * @return {@code true} uniquement pour le type {@link Type#EPSILON}
     */
    public boolean isEpsilon() {
        return type == Type.EPSILON;
    }

    /**
     * Vérifie si la transition peut consommer le caractère fourni.
     *
     * <p>Une transition ε renvoie toujours {@code false}, car elle ne consomme
     * rien, même si le caractère fourni est la lettre grecque {@code 'ε'}.
     * Le type {@link Type#ANY} accepte toute valeur {@code char} non exclue,
     * y compris un saut de ligne : le découpage du fichier appartient au moteur.
     * Coût moyen O(1), grâce à l'ensemble de hachage des exclusions.</p>
     *
     * @param character caractère du texte à comparer
     * @return {@code true} si le caractère peut être consommé, sinon {@code false}
     */
    public boolean matches(char character) {
        return (type == Type.ANY && !excludedSymbols.contains(character))
                || (type == Type.CHARACTER && symbol == character);
    }

    /**
     * Décrit l'arc avec les noms des états et son étiquette de lecture.
     *
     * @return texte tel que {@code (q0) --'a'--> (q1)}, avec ε ou un point universel si nécessaire
     */
    @Override
    public String toString() {
        String label = switch (type) {
            case EPSILON -> "ε";
            case ANY -> excludedSymbols.isEmpty() ? "." : ". sauf " + excludedSymbols;
            case CHARACTER -> "'" + symbol + "'";
        };

        return source.getLabel() + " --" + label + "--> " + destination.getLabel();
    }

}
