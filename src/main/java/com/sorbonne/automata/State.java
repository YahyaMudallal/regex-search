package com.sorbonne.automata;

import java.util.Objects;
import java.util.UUID;

/**
 * Représente un état, c'est-à-dire un sommet du graphe d'un automate.
 *
 * <p>Son nom sert à l'affichage et son statut indique son rôle : initial,
 * final, intermédiaire ou à la fois initial et final. Ces deux informations
 * peuvent changer ; l'identifiant UUID reste le même pendant toute sa durée de vie.</p>
 *
 * <p>Cette classe conserve l'égalité par référence héritée de {@link Object} :
 * deux instances sont distinctes même si elles portent le même nom.
 * L'UUID n'est pas utilisé pour redéfinir {@code equals} ou {@code hashCode}.</p>
 */
public class State {
    /** Nom lisible de l'état, par exemple {@code q0} ; jamais nul, pas forcément unique. */
    private String label;

    /** Rôle actuel de l'état dans l'automate ; jamais nul. */
    private Status status;

    /** Identifiant aléatoire créé une seule fois, utile pour distinguer les états à l'affichage. */
    private final UUID uuid;

    /**
     * Crée un état avec un nom, un rôle et un nouvel identifiant UUID.
     *
     * @param label nom affiché pour cet état, non nul
     * @param status rôle initialement attribué à cet état, non nul
     * @throws NullPointerException si le nom ou le statut est nul
     */
    public State(String label, Status status) {
        this.label = Objects.requireNonNull(label, "Le nom de l'état est obligatoire");
        this.status = Objects.requireNonNull(status, "Le statut de l'état est obligatoire");
        uuid = UUID.randomUUID();
    }

    /**
     * Renvoie le nom lisible de l'état.
     *
     * @return nom actuel, jamais nul
     */
    public String getLabel() {
        return label;
    }

    /**
     * Change le nom affiché sans modifier l'identité ni le rôle de l'état.
     *
     * @param label nouveau nom, non nul
     * @throws NullPointerException si {@code label} est nul
     */
    public void setLabel(String label) {
        this.label = Objects.requireNonNull(label, "Le nom de l'état est obligatoire");
    }

    /**
     * Renvoie le rôle actuel de l'état.
     *
     * @return statut actuel, jamais nul
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Change le rôle de l'état, par exemple lors de la composition de deux automates.
     *
     * <p>Le changement est visible dans tous les automates contenant cet objet.
     * Cette méthode ne vérifie pas qu'un automate garde un seul état initial.</p>
     *
     * @param status nouveau rôle, non nul
     * @throws NullPointerException si {@code status} est nul
     */
    public void setStatus(Status status) {
        this.status = Objects.requireNonNull(status, "Le statut de l'état est obligatoire");
    }

    /**
     * Renvoie l'identifiant attribué à la création de l'état.
     *
     * @return UUID constant pour cette instance, jamais nul
     */
    public UUID getUuid() {
        return this.uuid;
    }

    /**
     * Décrit l'état pour faciliter la lecture des traces de débogage.
     *
     * @return texte lisible au format {@code nom [statut]}
     */
    @Override
    public String toString() {
        return label + " [" + status + "]";
    }
}
