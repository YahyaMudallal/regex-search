package com.sorbonne.regex;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Vérifie la construction et la représentation des arbres syntaxiques
 * utilisés pour les expressions régulières.
 *
 * <p>Les scénarios couvrent les feuilles littérales, les nœuds opérateurs,
 * la modification des nœuds par accesseurs, ainsi que les représentations
 * textuelles des différents types de nœuds et de leurs enfants optionnels.</p>
 */
class SyntaxTreeTest {

	/**
	 * Vérifie qu'une feuille conserve sa lettre et ne possède aucun enfant.
	 *
	 * <p>Scénario : un arbre est construit avec la lettre {@code a}.
	 * Résultat attendu : le type est {@link NodeType#LETTER}, les enfants sont
	 * absents et la représentation textuelle est la lettre elle-même.</p>
	 */
	@Test
	void createsLetterLeafWithExpectedValues() {
		// Une feuille littérale ne possède ni opérateur ni sous-arbre.
		SyntaxTree tree = new SyntaxTree("a");

		// Le constructeur doit initialiser les propriétés propres à une feuille.
		assertEquals(NodeType.LETTER, tree.getNodeType());
		assertEquals("a", tree.getLetter());
		assertNull(tree.getLeft());
		assertNull(tree.getRight());
		assertEquals("a", tree.toString());
	}

	/**
	 * Vérifie qu'un nœud opérateur conserve ses deux sous-arbres.
	 *
	 * <p>Scénario : les lettres {@code a} et {@code b} sont réunies par une
	 * alternation. Résultat attendu : les références originales sont conservées,
	 * le type est {@link NodeType#ALTERNATION}, aucune lettre n'est stockée sur
	 * le nœud opérateur et la forme textuelle est {@code |(a,b)}.</p>
	 */
	@Test
	void createsOperatorNodeWithChildrenAndExpectedValues() {
		// Les feuilles servent de branches gauche et droite de l'alternation.
		SyntaxTree left = new SyntaxTree("a");
		SyntaxTree right = new SyntaxTree("b");
		SyntaxTree tree = new SyntaxTree(left, right, NodeType.ALTERNATION);

		// Le nœud doit exposer les mêmes objets que ceux fournis au constructeur.
		assertSame(left, tree.getLeft());
		assertSame(right, tree.getRight());
		assertEquals(NodeType.ALTERNATION, tree.getNodeType());
		assertNull(tree.getLetter());
		assertEquals("|(a,b)", tree.toString());
	}

	/**
	 * Vérifie que les accesseurs modifient effectivement les propriétés de l'arbre.
	 *
	 * <p>Scénario : une feuille initiale est transformée en nœud de concaténation
	 * portant deux nouvelles branches. Résultat attendu : chaque valeur fournie
	 * par un setter est lisible par son getter et la représentation utilise les
	 * nouvelles branches.</p>
	 */
	@Test
	void updatesAllFieldsThroughSetters() {
		// L'arbre initial permet de vérifier que les quatre propriétés peuvent changer.
		SyntaxTree tree = new SyntaxTree("a");
		SyntaxTree left = new SyntaxTree("b");
		SyntaxTree right = new SyntaxTree("c");

		// Remplacement complet de la structure et des métadonnées du nœud.
		tree.setLeft(left);
		tree.setRight(right);
		tree.setNodeType(NodeType.CONCATENATION);
		tree.setLetter("ignored");

		assertSame(left, tree.getLeft());
		assertSame(right, tree.getRight());
		assertEquals(NodeType.CONCATENATION, tree.getNodeType());
		assertEquals("ignored", tree.getLetter());
		assertEquals(".(b,c)", tree.toString());
	}

	/**
	 * Vérifie la représentation textuelle de chaque type de nœud pris en charge.
	 *
	 * <p>Scénario : un point, une étoile, une concaténation, une alternation et
	 * une protection sont construits. Résultat attendu : chaque opérateur utilise
	 * sa notation textuelle dédiée.</p>
	 */
	@Test
	void formatsEverySupportedNodeType() {
		// Les deux feuilles sont partagées par les scénarios de représentation.
		SyntaxTree letter = new SyntaxTree("a");
		SyntaxTree other = new SyntaxTree("b");

		// assertAll conserve toutes les vérifications même si une notation échoue.
		assertAll(
			() -> assertEquals(".", new SyntaxTree(null, null, NodeType.DOT).toString()),
			() -> assertEquals("a*", new SyntaxTree(letter, null, NodeType.STAR).toString()),
			() -> assertEquals("*", new SyntaxTree(null, null, NodeType.STAR).toString()),
			() -> assertEquals(".(a,b)", new SyntaxTree(letter, other, NodeType.CONCATENATION).toString()),
			() -> assertEquals("|(a,b)", new SyntaxTree(letter, other, NodeType.ALTERNATION).toString()),
			() -> assertEquals("a", new SyntaxTree(letter, null, NodeType.PROTECTION).toString())
		);
	}

	/**
	 * Vérifie les représentations lorsque des enfants sont absents ou lorsque le
	 * type n'a pas de notation spécifique dans {@link SyntaxTree#toString()}.
	 *
	 * <p>Résultat attendu : les opérateurs binaires remplacent un enfant absent
	 * par une chaîne vide, la protection vide produit une chaîne vide et les types
	 * de parenthèse produisent la valeur de repli {@code "?"}.</p>
	 */
	@Test
	void formatsNullChildrenAndUnknownRepresentations() {
		// Les opérateurs binaires conservent leurs séparateurs même sans enfants.
		assertEquals(".(,)", new SyntaxTree(null, null, NodeType.CONCATENATION).toString());
		assertEquals("|(a,)", new SyntaxTree(new SyntaxTree("a"), null, NodeType.ALTERNATION).toString());
		// Une protection sans sous-arbre et les parenthèses utilisent les cas limites prévus.
		assertEquals("", new SyntaxTree(null, null, NodeType.PROTECTION).toString());
		assertEquals("?", new SyntaxTree(null, null, NodeType.OPEN_PARENTHESE).toString());
		assertEquals("?", new SyntaxTree(null, null, NodeType.CLOSE_PARENTHESE).toString());
	}

}
