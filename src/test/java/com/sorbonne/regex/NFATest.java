package com.sorbonne.regex;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sorbonne.automata.Automaton;
import com.sorbonne.automata.Status;
import com.sorbonne.automata.Transition;

/**
 * Vérifie la conversion d'un {@link SyntaxTree} en automate non déterministe
 * avec transitions epsilon (epsilon-NFA).
 *
 * <p>Les scénarios couvrent les cas de base, les opérateurs inductifs et la
 * structure attendue des états initiaux, finals et des transitions ε.</p>
 */
class NFATest {

	/**
	 * Vérifie la construction d'un automate pour un simple caractère littéral.
	 *
	 * <p>Scénario : le nœud {@link NodeType#LETTER} contient {@code a}.
	 * Résultat attendu : un unique état initial, un unique état final, et une
	 * transition qui consomme {@code 'a'} entre ces deux états.</p>
	 */
	@Test
	void buildsNfaForSingleLetter() {
		SyntaxTree tree = new SyntaxTree("a");

		Automaton automaton = NFA.buildNFA(tree);

		assertEquals(2, automaton.getStates().size());
		assertEquals(1, automaton.getTransitions().size());
		assertNotNull(automaton.getInitialState());
		assertEquals(1, automaton.getFinalStates().size());
		assertEquals(Status.ENTER, automaton.getInitialState().getStatus());
		assertEquals(Status.FINAL, automaton.getFinalStates().iterator().next().getStatus());
		assertTrue(automaton.getTransitions().get(0).matches('a'));
	}

	/**
	 * Vérifie la construction d'un automate pour le point universel.
	 *
	 * <p>Scénario : le nœud {@link NodeType#DOT} représente un caractère quelconque.
	 * Résultat attendu : un seul arc universel relie l'état initial et l'état final.</p>
	 */
	@Test
	void buildsNfaForWildcardDot() {
		SyntaxTree tree = new SyntaxTree(null, null, NodeType.DOT);

		Automaton automaton = NFA.buildNFA(tree);

		assertEquals(2, automaton.getStates().size());
		assertEquals(1, automaton.getTransitions().size());
		assertTrue(automaton.getTransitions().get(0).matches('a'));
		assertTrue(automaton.getTransitions().get(0).matches('.'));
		assertFalse(automaton.getTransitions().get(0).isEpsilon());
	}

	/**
	 * Vérifie la concaténation de deux automates élémentaires.
	 *
	 * <p>Scénario : l'arbre construit par {@code a.b} est converti en NFA.
	 * Résultat attendu : l'automate final comporte deux transitions de base et
	 * une transition epsilon entre les deux sous-automates.</p>
	 */
	@Test
	void buildsNfaForConcatenation() {
		SyntaxTree left = new SyntaxTree("a");
		SyntaxTree right = new SyntaxTree("b");
		SyntaxTree tree = new SyntaxTree(left, right, NodeType.CONCATENATION);

		Automaton automaton = NFA.buildNFA(tree);

		assertEquals(4, automaton.getStates().size());
		assertEquals(3, automaton.getTransitions().size());
		assertEquals(1, automaton.getFinalStates().size());
		assertTrue(automaton.getTransitions().stream().anyMatch(t -> t.isEpsilon()));
		assertTrue(automaton.getTransitions().stream().anyMatch(t -> t.matches('a')));
		assertTrue(automaton.getTransitions().stream().anyMatch(t -> t.matches('b')));
	}

	/**
	 * Vérifie l'union de deux sous-automates.
	 *
	 * <p>Scénario : l'arbre construit par {@code a|b} est converti en NFA.
	 * Résultat attendu : un nouvel état initial et un nouvel état final encadrent
	 * les deux sous-automates, reliés par des epsilon-transitions.</p>
	 */
	@Test
	void buildsNfaForAlternation() {
		SyntaxTree left = new SyntaxTree("a");
		SyntaxTree right = new SyntaxTree("b");
		SyntaxTree tree = new SyntaxTree(left, right, NodeType.ALTERNATION);

		Automaton automaton = NFA.buildNFA(tree);

		assertEquals(6, automaton.getStates().size());
        assertEquals(6, automaton.getTransitions().size()); // Corrigé : 4 transitions epsilon + 2 transitions littérales
        assertEquals(1, automaton.getFinalStates().size());
        assertEquals(4, automaton.getTransitions().stream().filter(Transition::isEpsilon).count());
        assertTrue(automaton.getTransitions().stream().anyMatch(t -> t.matches('a')));
        assertTrue(automaton.getTransitions().stream().anyMatch(t -> t.matches('b')));
	}

	/**
	 * Vérifie la fermeture de Kleene sur un automate élémentaire.
	 *
	 * <p>Scénario : le nœud {@link NodeType#STAR} porte sur {@code a}.
	 * Résultat attendu : le résultat contient au moins les états nécessaires au
	 * sous-automate, à l'entrée et à la sortie, reliés par des transitions epsilon.</p>
	 */
	@Test
	void buildsNfaForStar() {
		SyntaxTree inner = new SyntaxTree("a");
		SyntaxTree tree = new SyntaxTree(inner, null, NodeType.STAR);

		Automaton automaton = NFA.buildNFA(tree);

		assertEquals(4, automaton.getStates().size());
        assertEquals(5, automaton.getTransitions().size()); // Corrigé : 4 transitions epsilon + 1 transition littérale
        assertEquals(1, automaton.getFinalStates().size());
        assertEquals(4, automaton.getTransitions().stream().filter(Transition::isEpsilon).count());
        assertTrue(automaton.getTransitions().stream().anyMatch(t -> t.matches('a')));
	}

	/**
	 * Vérifie que la protection est transparente et ne modifie pas la structure.
	 *
	 * <p>Scénario : le sous-arbre est encapsulé sous un nœud {@link NodeType#PROTECTION}.
	 * Résultat attendu : la construction est identique à celle du sous-arbre seul.</p>
	 */
	@Test
	void ignoresProtectionNodeWhenBuildingNfa() {
		SyntaxTree inner = new SyntaxTree("a");
		SyntaxTree tree = new SyntaxTree(inner, null, NodeType.PROTECTION);

		Automaton direct = NFA.buildNFA(inner);
		Automaton protectedAutomaton = NFA.buildNFA(tree);

		assertEquals(direct.getStates().size(), protectedAutomaton.getStates().size());
		assertEquals(direct.getTransitions().size(), protectedAutomaton.getTransitions().size());
		assertEquals(direct.getInitialState().getStatus(), protectedAutomaton.getInitialState().getStatus());
		assertEquals(1, direct.getFinalStates().size());
		assertEquals(1, protectedAutomaton.getFinalStates().size());
		assertEquals(
				direct.getFinalStates().iterator().next().getStatus(),
				protectedAutomaton.getFinalStates().iterator().next().getStatus());
		assertEquals(
				direct.getTransitions().get(0).getSymbol(),
				protectedAutomaton.getTransitions().get(0).getSymbol());
	}

	/**
	 * Vérifie le rejet d'un arbre non supporté.
	 *
	 * <p>Scénario : un nœud de type {@link NodeType#OPEN_PARENTHESE} est passé à la
	 * conversion. Résultat attendu : une {@link IllegalArgumentException} est levée.</p>
	 */
	@Test
	void rejectsUnsupportedNodeTypes() {
		SyntaxTree tree = new SyntaxTree(null, null, NodeType.OPEN_PARENTHESE);

		assertThrows(IllegalArgumentException.class, () -> NFA.buildNFA(tree));
	}

	/**
	 * Vérifie qu'un automate construit avec un arbre vide ou nul est rejeté.
	 *
	 * <p>Résultat attendu : une exception est levée avant la construction.</p>
	 */
	@Test
	void rejectsNullTree() {
		assertThrows(NullPointerException.class, () -> NFA.buildNFA(null));
	}

	/**
	 * Vérifie qu'une composition conserve un unique état final.
	 *
	 * <p>La méthode {@code getUniqueFinalState} est interne à {@link NFA} ;
	 * elle est donc vérifiée indirectement par la construction d'une alternation.</p>
	 */
	@Test
	void buildsComposedNfaWithUniqueFinalState() {
		SyntaxTree tree = new SyntaxTree(new SyntaxTree("a"), new SyntaxTree("b"), NodeType.ALTERNATION);

		Automaton automaton = NFA.buildNFA(tree);

		assertEquals(1, automaton.getFinalStates().size());
		assertNotNull(automaton.getInitialState());
	}
}
