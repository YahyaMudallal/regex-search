package com.sorbonne.automata;

import java.util.Objects;

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

        /** Consomme un caractère quelconque ; représente le point universel. */
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
        this.source = Objects.requireNonNull(source, "L'état source est obligatoire");
        this.destination = Objects.requireNonNull(destination, "L'état destination est obligatoire");
        this.type = type;
        this.symbol = symbol;
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
     * Le type {@link Type#ANY} accepte toute valeur {@code char}, y compris
     * un saut de ligne : le découpage du fichier en lignes appartient au moteur de recherche.</p>
     *
     * @param character caractère du texte à comparer
     * @return {@code true} si le caractère peut être consommé, sinon {@code false}
     */
    public boolean matches(char character) {
        return type == Type.ANY || (type == Type.CHARACTER && symbol == character);
    }

    /**
     * Décrit l'arc avec les noms des états et son étiquette de lecture.
     *
     * @return texte tel que {@code (q0) --'a'--> (q1)}, avec ε ou un point universel si nécessaire
     */
    @Override
    public String toString() {
        // Étiquette lisible permettant de distinguer les trois types de transition.
        String label = switch (type) {
            case EPSILON -> "ε";
            case ANY -> ". (universel)";
            case CHARACTER -> "'" + symbol + "'";
        };
        return "(" + source.getLabel() + ") --" + label + "--> (" + destination.getLabel() + ")";
    }

}
