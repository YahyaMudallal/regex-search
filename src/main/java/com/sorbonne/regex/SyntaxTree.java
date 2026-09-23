package com.sorbonne.regex;

import java.util.List;
import java.util.ArrayList;

/**
 * Arbre syntaxique retourné par le {@link RegexParser}.
 */
public class SyntaxTree {

    private NodeType nodeType;
    private String letter;

    private SyntaxTree left;
    private SyntaxTree right;

    /**
     * Construit un {@link SyntaxTree} représentant un noeud opérateur.
     *
     * @param left le sous-arbre fils gauche
     * @param right le sous-arbre fils droit
     * @param type le type du noeud
     */
    public SyntaxTree(SyntaxTree left, SyntaxTree right, NodeType type) {
        this.left = left;
        this.right = right;
        this.nodeType = type;
        this.letter = null;
    }

    /**
     * Construit une feuille du {@link SyntaxTree} contenant une lettre littérale.
     *
     * @param letter la lettre contenue dans le noeud
     */
    public SyntaxTree(String letter) {
        this.left = null;
        this.right = null;
        this.nodeType = NodeType.LETTER;
        this.letter = letter;
    }

    public SyntaxTree getLeft() { return left; }
    public void setLeft(SyntaxTree left) { this.left = left; }

    public SyntaxTree getRight() { return right; }
    public void setRight(SyntaxTree right) { this.right = right; }

    public NodeType getNodeType() { return nodeType; }
    public void setNodeType(NodeType nodeType) { this.nodeType = nodeType; }

    public String getLetter() { return letter; }
    public void setLetter(String letter) { this.letter = letter; }

    @Override
    public String toString() {
        return toTreeString(this, "", true);
    }

    /**
     * Représente l'arbre syntaxique sous forme d'arbre textuel.
     *
     * @param node le noeud courant à afficher
     * @param prefix l'indentation déjà appliquée
     * @param isTail vrai si le noeud est le dernier fils de son parent
     * @return une représentation arborescente multi-ligne lisible dans une console
     */
    private String toTreeString(SyntaxTree node, String prefix, boolean isTail) {
        if (node == null) {
            return prefix + (isTail ? "└── " : "├── ") + "∅\n";
        }

        StringBuilder builder = new StringBuilder();
        String connector = isTail ? "└── " : "├── ";
        builder.append(prefix).append(connector).append(getNodeLabel(node)).append("\n");

        List<SyntaxTree> children = new ArrayList<>();
        if (node.left != null) {
            children.add(node.left);
        }
        if (node.right != null) {
            children.add(node.right);
        }

        String childPrefix = prefix + (isTail ? "    " : "│   ");
        for (int i = 0; i < children.size(); i++) {
            boolean last = i == children.size() - 1;
            builder.append(toTreeString(children.get(i), childPrefix, last));
        }

        return builder.toString();
    }

    /**
     * Retourne l'étiquette affichée pour un noeud dans l'arbre.
     *
     * @param node le noeud dont l'étiquette doit être affichée
     * @return le symbole lisible correspondant au type du noeud
     */
    private String getNodeLabel(SyntaxTree node) {
        if (node.nodeType == NodeType.LETTER) {
            return node.letter == null || node.letter.isEmpty() ? "∅" : node.letter;
        }
        if (node.nodeType == NodeType.DOT) {
            return ".";
        }
        if (node.nodeType == NodeType.STAR) {
            return "*";
        }
        if (node.nodeType == NodeType.CONCATENATION) {
            return ".";
        }
        if (node.nodeType == NodeType.ALTERNATION) {
            return "|";
        }
        if (node.nodeType == NodeType.PROTECTION) {
            return "PROTECTION";
        }
        if (node.nodeType == NodeType.OPEN_PARENTHESE) {
            return "(";
        }
        if (node.nodeType == NodeType.CLOSE_PARENTHESE) {
            return ")";
        }
        return "?";
    }
}