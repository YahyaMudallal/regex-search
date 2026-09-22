package com.sorbonne.automata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Représente un automate sous la forme d'un graphe d'états et de transitions.
 *
 * <p>Le graphe peut contenir des branches, des boucles et des transitions ε
 * (sans lecture de caractère). Il peut donc représenter un automate non déterministe.
 * L'ordre d'ajout des transitions ne détermine pas l'état initial ou les états finaux :
 * ces rôles dépendent du {@link Status} de chaque état.</p>
 *
 * <p>Cette classe stocke la structure du graphe. Elle ne réalise pas encore
 * la reconnaissance d'un texte ni la conversion d'une expression régulière.</p>
 *
 * <p>Les états restent modifiables et peuvent être partagés entre automates.
 * Modifier le statut d'un état partagé affecte donc tous les automates qui le contiennent.
 * Les collections renvoyées empêchent l'ajout et la suppression directs, mais
 * ne rendent pas les états eux-mêmes immuables.</p>
 */
public class Automaton {
    /**
     * États du graphe, conservés dans leur ordre d'ajout.
     * Un même objet {@link State} n'est enregistré qu'une fois, même s'il participe
     * à plusieurs transitions. Deux objets de même nom restent deux états distincts.
     */
    private final Set<State> states = new LinkedHashSet<>();

    /** Transitions dans leur ordre d'ajout ; les doublons sont autorisés. */
    private final List<Transition> transitions = new ArrayList<>();

    /**
     * Crée un automate vide, sans état ni transition.
     *
     * <p>Les états peuvent ensuite être ajoutés avec {@link #addState(State)}
     * ou enregistrés automatiquement avec {@link #add(Transition)}.</p>
     */
    public Automaton() {
    }

    /**
     * Ajoute un état, même s'il ne participe à aucune transition.
     *
     * <p>Ajouter de nouveau le même objet n'a aucun effet. Cette méthode ne vérifie
     * pas l'unicité de l'état initial ; cette vérification est faite lors de l'appel
     * à {@link #getInitialState()}.</p>
     *
     * @param state état à enregistrer, non nul
     * @throws NullPointerException si {@code state} est nul
     */
    public void addState(State state) {
        states.add(Objects.requireNonNull(state, "L'état est obligatoire"));
    }

    /**
     * Ajoute une transition et enregistre automatiquement ses deux états.
     *
     * <p>Les branches, les boucles et plusieurs transitions portant le même
     * caractère sont autorisées. Aucun contrôle de déterminisme n'est effectué.</p>
     *
     * @param transition transition à ajouter, non nulle
     * @throws NullPointerException si {@code transition} est nulle
     */
    public void add(Transition transition) {
        Objects.requireNonNull(transition, "La transition est obligatoire");
        addState(transition.getSource());
        addState(transition.getDestination());
        transitions.add(transition);
    }

    /**
     * Donne accès aux états sans permettre de modifier directement leur collection.
     *
     * @return vue non modifiable, dans l'ordre d'ajout, qui reflète les ajouts futurs
     */
    public Set<State> getStates() {
        return Collections.unmodifiableSet(states);
    }

    /**
     * Donne accès aux transitions sans permettre de modifier directement leur liste.
     *
     * @return vue non modifiable, dans l'ordre d'ajout, qui reflète les ajouts futurs
     */
    public List<Transition> getTransitions() {
        return Collections.unmodifiableList(transitions);
    }

    /**
     * Recherche l'unique état initial à partir des statuts actuels.
     *
     * <p>Les statuts sont relus à chaque appel, car ils peuvent changer pendant
     * la construction. Les statuts {@link Status#ENTER} et {@link Status#ENTER_FINAL}
     * désignent tous deux un état initial.</p>
     *
     * @return état initial, ou {@code null} si aucun état n'est marqué initial
     * @throws IllegalStateException si plusieurs états sont marqués initiaux
     */
    public State getInitialState() {
        // Premier état initial rencontré ; null signifie qu'aucun n'a encore été trouvé.
        State initial = null;
        // Chaque état est examiné pour détecter aussi un éventuel second état initial.
        for (State state : states) {
            if (state.getStatus().isInitial()) {
                if (initial != null) {
                    throw new IllegalStateException("L'automate possède plusieurs états initiaux");
                }
                initial = state;
            }
        }
        return initial;
    }

    /**
     * Recherche les états finaux à partir des statuts actuels.
     *
     * <p>Le résultat contient les états {@link Status#FINAL} et
     * {@link Status#ENTER_FINAL}. Sa composition est fixée au moment de l'appel :
     * il faut rappeler la méthode après un changement de statut ou un ajout d'état.</p>
     *
     * @return ensemble non modifiable des états finaux, éventuellement vide
     */
    public Set<State> getFinalStates() {
        // Nouvelle collection contenant les états finaux trouvés pendant cet appel.
        Set<State> finals = new LinkedHashSet<>();
        // État dont on vérifie le rôle final, indépendamment de son rôle initial.
        for (State state : states) {
            if (state.getStatus().isFinal()) {
                finals.add(state);
            }
        }
        return Collections.unmodifiableSet(finals);
    }

    /**
     * Recherche les transitions dont l'état fourni est la source.
     *
     * <p>Les transitions ε sont incluses. Avec la classe {@link State} actuelle,
     * la recherche compare les objets, pas leurs noms. Un état absent du graphe
     * donne une liste vide.</p>
     *
     * @param state état de départ recherché, non nul
     * @return liste non modifiable, dans l'ordre d'ajout, sans les ajouts futurs
     * @throws NullPointerException si {@code state} est nul
     */
    public List<Transition> getOutgoingTransitions(State state) {
        Objects.requireNonNull(state, "L'état est obligatoire");
        return transitions.stream()
                // transition désigne chaque arc examiné ; seuls ceux partant de state restent.
                .filter(transition -> transition.getSource().equals(state))
                .toList();
    }

    /**
     * Affiche le graphe sous une forme lisible pour le débogage et la démonstration.
     *
     * <p>Le rendu liste chaque état avec son rôle, puis ses transitions sortantes,
     * ce qui permet de visualiser rapidement les branches et les boucles.</p>
     *
     * @return représentation textuelle de l'automate, organisée par état
     */
    @Override
    public String toString() {
        if (states.isEmpty()) {
            return "Automaton {}";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Automaton {\n");

        for (State state : states) {
            builder.append("  - ")
                    .append(state.getLabel())
                    .append(" [")
                    .append(state.getStatus())
                    .append("]");

            List<Transition> outgoing = getOutgoingTransitions(state);
            if (outgoing.isEmpty()) {
                builder.append("\n");
                continue;
            }

            builder.append("\n");
            for (Transition transition : outgoing) {
                String label = switch (transition.getType()) {
                    case EPSILON -> "ε";
                    case ANY -> ".";
                    case CHARACTER -> "'" + transition.getSymbol() + "'";
                };

                builder.append("      ")
                        .append(transition.getSource().getLabel())
                        .append(" --")
                        .append(label)
                        .append("--> ")
                        .append(transition.getDestination().getLabel())
                        .append("\n");
            }
        }

        builder.append("}");
        return builder.toString();
    }
}
