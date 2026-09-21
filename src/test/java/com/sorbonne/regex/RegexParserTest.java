package com.sorbonne.regex;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Vérifie la construction d'un {@link SyntaxTree} à partir d'une expression
 * régulière.
 *
 * <p>Les scénarios couvrent la tokenisation, les opérateurs atomiques et leur
 * priorité, les parenthèses, les caractères échappés ainsi que les erreurs de
 * syntaxe détectées par le parseur.</p>
 */
class RegexParserTest {

	/**
	 * Vérifie la conversion des caractères de l'expression en nœuds élémentaires.
	 *
	 * <p>Scénario : l'expression contient une lettre, un point, une étoile, une
	 * alternation et des parenthèses. Résultat attendu : chaque caractère produit
	 * le type de nœud correspondant dans l'ordre d'apparition.</p>
	 */
	@Test
	void tokenizesRegexOperatorsAndLetters() {
		List<SyntaxTree> trees = RegexParser.initSyntaxTreeList("a.(b|c)*");

		// La tokenisation conserve les caractères et transforme les opérateurs en types dédiés.
		assertEquals(8, trees.size());
		assertEquals(NodeType.LETTER, trees.get(0).getNodeType());
		assertEquals("a", trees.get(0).getLetter());
		assertEquals(NodeType.DOT, trees.get(1).getNodeType());
		assertEquals(NodeType.OPEN_PARENTHESE, trees.get(2).getNodeType());
		assertEquals(NodeType.LETTER, trees.get(3).getNodeType());
		assertEquals(NodeType.ALTERNATION, trees.get(4).getNodeType());
		assertEquals(NodeType.LETTER, trees.get(5).getNodeType());
		assertEquals(NodeType.CLOSE_PARENTHESE, trees.get(6).getNodeType());
		assertEquals(NodeType.STAR, trees.get(7).getNodeType());
	}

	/**
	 * Vérifie qu'un littéral échappé n'est pas interprété comme un opérateur.
	 *
	 * <p>Scénario : le point, l'étoile, la barre verticale et les parenthèses sont
	 * précédés d'une barre oblique inverse. Résultat attendu : chaque caractère
	 * échappé devient une feuille {@link NodeType#LETTER} contenant le caractère
	 * littéral.</p>
	 */
	@Test
	void tokenizesEscapedOperatorsAsLetters() {
		List<SyntaxTree> trees = RegexParser.initSyntaxTreeList("\\.\\*\\|\\(\\)");

		// Les opérateurs échappés doivent tous rester des caractères ordinaires.
		assertEquals(5, trees.size());
		assertEquals(List.of(".", "*", "|", "(", ")"),
			trees.stream().map(SyntaxTree::getLetter).toList());
		assertTrue(trees.stream().allMatch(tree -> tree.getNodeType() == NodeType.LETTER));
	}

	/**
	 * Vérifie la priorité de l'étoile, de la concaténation et de l'alternation.
	 *
	 * <p>Scénario : l'expression {@code ab*|c} combine les trois opérateurs.
	 * Résultat attendu : l'étoile s'applique à {@code b}, puis {@code a} est
	 * concaténé avec ce sous-arbre, avant l'alternation avec {@code c}.</p>
	 */
	@Test
	void respectsOperatorPriority() throws Exception {
		SyntaxTree tree = RegexParser.parse("ab*|c");

		// L'alternation est la racine car elle est traitée après les autres opérateurs.
		assertEquals(NodeType.ALTERNATION, tree.getNodeType());
		assertEquals("c", tree.getRight().getLetter());
		assertEquals(NodeType.CONCATENATION, tree.getLeft().getNodeType());
		assertEquals("a", tree.getLeft().getLeft().getLetter());
		assertEquals(NodeType.STAR, tree.getLeft().getRight().getNodeType());
		assertEquals("b", tree.getLeft().getRight().getLeft().getLetter());
	}

	/**
	 * Vérifie que les parenthèses modifient la structure de l'expression.
	 *
	 * <p>Scénario : l'expression {@code a(b|c)} contient une alternation protégée
	 * par des parenthèses. Résultat attendu : la concaténation est la racine et
	 * son second enfant est l'alternation complète.</p>
	 */
	@Test
	void parsesParenthesizedSubexpressions() throws Exception {
		SyntaxTree tree = RegexParser.parse("a(b|c)");

		// Le groupe est réduit avant la concaténation avec la lettre initiale.
		assertEquals(NodeType.CONCATENATION, tree.getNodeType());
		assertEquals("a", tree.getLeft().getLetter());
		assertEquals(NodeType.ALTERNATION, tree.getRight().getNodeType());
		assertEquals("b", tree.getRight().getLeft().getLetter());
		assertEquals("c", tree.getRight().getRight().getLetter());
	}

	/**
	 * Vérifie que les parenthèses sont retirées de l'arbre final après réduction.
	 *
	 * <p>Scénario : une expression ne contenant qu'un groupe est analysée.
	 * Résultat attendu : la racine finale est l'opérateur du groupe et aucun nœud
	 * {@link NodeType#PROTECTION} ne subsiste.</p>
	 */
	@Test
	void removesProtectionNodesFromResult() throws Exception {
		SyntaxTree tree = RegexParser.parse("(ab)");

		assertEquals(NodeType.CONCATENATION, tree.getNodeType());
		assertEquals("a", tree.getLeft().getLetter());
		assertEquals("b", tree.getRight().getLetter());
		assertFalse(containsNodeType(tree, NodeType.PROTECTION));
	}

	/**
	 * Vérifie le traitement d'une barre oblique inverse finale.
	 *
	 * <p>Résultat attendu : une barre oblique inverse sans caractère suivant est
	 * conservée comme une feuille littérale.</p>
	 */
	@Test
	void preservesTrailingEscapeCharacter() throws Exception {
		SyntaxTree tree = RegexParser.parse("a\\");

		assertEquals(NodeType.CONCATENATION, tree.getNodeType());
		assertEquals("a", tree.getLeft().getLetter());
		assertEquals("\\", tree.getRight().getLetter());
	}

	/**
	 * Vérifie le rejet des expressions vides ou nulles.
	 *
	 * <p>Résultat attendu : les deux entrées provoquent une exception avant toute
	 * construction d'arbre.</p>
	 */
	@Test
	void rejectsNullAndEmptyExpressions() {
		assertThrows(Exception.class, () -> RegexParser.parse(null));
		assertThrows(Exception.class, () -> RegexParser.parse(""));
	}

	/**
	 * Vérifie le rejet des opérateurs sans opérande valide.
	 *
	 * <p>Scénario : étoile initiale, alternation sans opérande gauche ou droit,
	 * et parenthèses non équilibrées. Résultat attendu : chaque expression est
	 * signalée comme syntaxiquement invalide.</p>
	 */
	@Test
	void rejectsMalformedExpressions() {
		assertAll(
			() -> assertThrows(Exception.class, () -> RegexParser.parse("*a")),
			() -> assertThrows(Exception.class, () -> RegexParser.parse("|a")),
			() -> assertThrows(Exception.class, () -> RegexParser.parse("a|")),
			() -> assertThrows(Exception.class, () -> RegexParser.parse("(a")),
			() -> assertThrows(Exception.class, () -> RegexParser.parse("a)")),
			() -> assertThrows(Exception.class, () -> RegexParser.parse("()"))
		);
	}

	/**
	 * Recherche récursivement un type de nœud dans un arbre.
	 *
	 * @param tree arbre à parcourir
	 * @param nodeType type recherché
	 * @return {@code true} si le type est présent dans l'arbre
	 */
	private static boolean containsNodeType(SyntaxTree tree, NodeType nodeType) {
		if (tree == null) {
			return false;
		}
		return tree.getNodeType() == nodeType
			|| containsNodeType(tree.getLeft(), nodeType)
			|| containsNodeType(tree.getRight(), nodeType);
	}

}
