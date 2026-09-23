package com.sorbonne.regex;

/**
 * Enumeration representant les différents types de noeuds dans l'arbre syntaxique.
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