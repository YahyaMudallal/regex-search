package com.sorbonne.automata;

/**
 * Définit le rôle d'un état dans un automate.
 *
 * <p>Un état initial est le point de départ de la lecture. Un état final
 * permet d'accepter un mot lorsque sa lecture complète s'y termine.
 * Un même état peut remplir les deux rôles.</p>
 */
public enum Status {
    /** État initial uniquement : la lecture commence ici. */
    ENTER,

    /** État final uniquement : un mot peut être accepté lorsque sa lecture s'y termine. */
    FINAL,

    /** État qui n'est ni initial ni final. */
    INTERMEDIATE,

    /** État à la fois initial et final, permettant notamment d'accepter le mot vide. */
    ENTER_FINAL;

    /**
     * Indique si ce rôle permet de commencer la lecture dans l'état.
     *
     * @return {@code true} pour {@link #ENTER} et {@link #ENTER_FINAL}, sinon {@code false}
     */
    public boolean isInitial() {
        return this == ENTER || this == ENTER_FINAL;
    }

    /**
     * Indique si ce rôle permet d'accepter un mot à la fin de sa lecture.
     *
     * @return {@code true} pour {@link #FINAL} et {@link #ENTER_FINAL}, sinon {@code false}
     */
    public boolean isFinal() {
        return this == FINAL || this == ENTER_FINAL;
    }
}
