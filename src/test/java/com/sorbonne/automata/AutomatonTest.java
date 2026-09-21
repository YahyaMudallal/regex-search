package com.sorbonne.automata;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Vérifie les règles de représentation des automates et des transitions.
 *
 * <p>Chaque test construit son propre graphe. Les scénarios couvrent les branches,
 * les boucles, les différents symboles, les changements de statut et la protection
 * des collections. Ils ne testent pas encore la recherche de motifs dans un texte.</p>
 */
class AutomatonTest {
    /**
     * Vérifie qu'un graphe conserve sa structure quand ses arcs sont ajoutés dans le désordre.
     *
     * <p>Scénario : le chemin {@code q0 --a--> q1 --ε--> q2} est complété par
     * une boucle sur {@code q1} lisant {@code b} et un arc direct de {@code q0}
     * vers {@code q2} lisant {@code c}. La boucle est ajoutée en premier.</p>
     *
     * <p>Résultat attendu : trois états distincts, {@code q0} initial, {@code q2}
     * final et les bonnes transitions sortantes dans leur ordre d'ajout.</p>
     */
    @Test
    void representsBranchesAndLoopsRegardlessOfInsertionOrder() {
        // Point de départ des deux chemins possibles.
        State start = new State("q0", Status.ENTER);
        // État intermédiaire sur lequel la lecture de b peut se répéter.
        State middle = new State("q1", Status.INTERMEDIATE);
        // État final commun aux deux chemins.
        State end = new State("q2", Status.FINAL);
        // Boucle qui consomme b sans changer d'état.
        Transition loop = new Transition(middle, middle, 'b');
        // Sortie vers l'état final sans consommer de caractère.
        Transition exit = new Transition(middle, end);
        // Chemin direct vers l'état final en lisant c.
        Transition branch = new Transition(start, end, 'c');
        // Entrée dans le chemin passant par l'état intermédiaire en lisant a.
        Transition entry = new Transition(start, middle, 'a');
        // Graphe construit volontairement en commençant par la boucle.
        Automaton automaton = new Automaton();
        automaton.add(loop);
        automaton.add(exit);
        automaton.add(branch);
        automaton.add(entry);

        // L'ordre d'insertion ne change ni les états présents ni leur rôle.
        assertEquals(Set.of(start, middle, end), automaton.getStates());
        assertSame(start, automaton.getInitialState());
        assertEquals(Set.of(end), automaton.getFinalStates());
        // Chaque état ne doit exposer que ses propres arcs sortants.
        assertEquals(List.of(loop, exit), automaton.getOutgoingTransitions(middle));
        assertEquals(List.of(branch, entry), automaton.getOutgoingTransitions(start));
    }

    /**
     * Vérifie que le point littéral, le point universel et ε ont des sens distincts.
     *
     * <p>Scénario : trois transitions relient les mêmes états, chacune utilisant
     * un des trois modes de lecture.</p>
     *
     * <p>Résultat attendu : le point littéral accepte seulement {@code '.'} parmi
     * les caractères testés ; le point universel accepte {@code '.'} et {@code 'a'} ;
     * ε ne consomme ni {@code 'a'} ni le caractère grec {@code 'ε'}.</p>
     */
    @Test
    void distinguishesLiteralDotWildcardAndEpsilon() {
        // Source commune : seule l'étiquette des transitions change dans ce scénario.
        State start = new State("q0", Status.ENTER);
        // Destination commune aux trois types de transition.
        State end = new State("q1", Status.FINAL);
        // Transition qui attend le caractère point lui-même.
        Transition literal = new Transition(start, end, '.');
        // Transition qui accepte un caractère quelconque.
        Transition wildcard = Transition.any(start, end);
        // Transition qui ne lit aucun caractère.
        Transition epsilon = new Transition(start, end);

        // Un point littéral ne doit pas se comporter comme un caractère universel.
        assertTrue(literal.matches('.'));
        assertFalse(literal.matches('a'));
        // Un caractère universel consomme bien un caractère ; ce n'est pas ε.
        assertTrue(wildcard.matches('.'));
        assertTrue(wildcard.matches('a'));
        assertFalse(wildcard.isEpsilon());
        // La notation ε représente l'absence de lecture, pas une lettre à consommer.
        assertTrue(epsilon.isEpsilon());
        assertFalse(epsilon.matches('a'));
        assertFalse(epsilon.matches('ε'));
    }

    /**
     * Vérifie les états sans transition et la prise en compte des changements de statut.
     *
     * <p>Scénario : un état isolé est d'abord initial et final, puis intermédiaire.
     * Un second objet portant le même nom est ajouté comme état initial.
     * Enfin, le premier état redevient lui aussi initial.</p>
     *
     * <p>Résultats attendus : les rôles sont recalculés à chaque lecture,
     * les deux objets de même nom restent distincts et la présence de deux états
     * initiaux provoque une {@link IllegalStateException} lors de leur recherche.</p>
     */
    @Test
    // assertThrows vérifie l'exception ; son résultat est volontairement ignoré.
    @SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
    void supportsIsolatedStatesAndStatusChangesDuringConstruction() {
        // État isolé qui remplit au départ les deux rôles.
        State state = new State("q0", Status.ENTER_FINAL);
        // Automate contenant un état mais aucun arc.
        Automaton automaton = new Automaton();
        automaton.addState(state);

        assertSame(state, automaton.getInitialState());
        assertEquals(Set.of(state), automaton.getFinalStates());
        assertTrue(automaton.getTransitions().isEmpty());

        // Le changement de statut doit être visible sans réinsérer l'état.
        state.setStatus(Status.INTERMEDIATE);
        assertNull(automaton.getInitialState());
        assertTrue(automaton.getFinalStates().isEmpty());
        // Même nom d'affichage, mais objet distinct du premier état.
        State other = new State("q0", Status.ENTER);
        automaton.addState(other);
        assertEquals(2, automaton.getStates().size());
        assertSame(other, automaton.getInitialState());

        // Deux états initiaux rendent la recherche d'un état initial unique impossible.
        state.setStatus(Status.ENTER);
        assertThrows(IllegalStateException.class, automaton::getInitialState);
    }

    /**
     * Vérifie que les accès publics ne permettent pas de modifier directement les collections.
     *
     * <p>Scénario : sur un automate contenant un état isolé, on tente d'ajouter
     * une transition nulle, de vider l'ensemble des états, puis d'insérer une boucle
     * directement dans la liste renvoyée par {@link Automaton#getTransitions()}.</p>
     *
     * <p>Résultats attendus : l'ajout nul provoque une {@link NullPointerException},
     * les deux modifications directes provoquent une {@link UnsupportedOperationException},
     * et le graphe conserve son état unique et sa liste de transitions vide.</p>
     */
    @Test
    // assertThrows vérifie l'exception ; son résultat est volontairement ignoré.
    @SuppressWarnings({"ThrowableResultIgnored", "ThrowableResultOfMethodCallIgnored"})
    void protectsGraphCollectionsAndRejectsNullTransitions() {
        // Graphe dont on vérifie la protection contre les modifications directes.
        Automaton automaton = new Automaton();
        // Unique état servant aussi aux deux extrémités de la boucle tentée.
        State state = new State("q0", Status.ENTER_FINAL);
        automaton.addState(state);

        // Chaque opération interdite doit échouer avec l'exception attendue.
        assertThrows(NullPointerException.class, () -> automaton.add(null));
        assertThrows(UnsupportedOperationException.class, () -> automaton.getStates().clear());
        assertThrows(UnsupportedOperationException.class, () -> automaton.getTransitions().add(
                new Transition(state, state)));
        // Les tentatives rejetées ne doivent pas avoir modifié le contenu du graphe.
        assertEquals(Set.of(state), automaton.getStates());
        assertTrue(automaton.getTransitions().isEmpty());
    }
}
