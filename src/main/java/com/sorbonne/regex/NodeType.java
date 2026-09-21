package com.sorbonne.regex;

/**
 * Enum representing all the nodes of the types of SyntaxTree.
 */
public enum NodeType {
	ALTERNATION,
	CONCATENATION,
	STAR,
	DOT,
	OPEN_PARENTHESE,
	CLOSE_PARENTHESE,
	PROTECTION,
	LETTER
	
}