package com.sorbonne.regex;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Class representing a syntax tree returned by the {@link RegexParser}.
 */
public class SyntaxTree {

    private NodeType nodeType;
    private String letter;

    private SyntaxTree left;
    private SyntaxTree right;

    /**
     * Constructor for a {@link SyntaxTree} with an operator node.
     * @param left  The subtree of the left child.
     * @param right The subtree of the right child.
     * @param type  The type of the node.
     */
    public SyntaxTree(SyntaxTree left, SyntaxTree right, NodeType type) {
        this.left = left;
        this.right = right;
        this.nodeType = type;
        this.letter = null;
    }

    /**
     * Constructor of a leaf of the {@link SyntaxTree} containing a literal letter.
     * @param letter The letter the node contains.
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

    /** Détermine en O(m) si l'arbre accepte ε, sans utiliser la pile Java. */
    public boolean acceptsEmpty() {
        record Visit(SyntaxTree node, boolean expanded) {}
        Deque<Visit> pending = new ArrayDeque<>();
        Deque<Boolean> values = new ArrayDeque<>();
        pending.push(new Visit(this, false));
        while (!pending.isEmpty()) {
            Visit visit = pending.pop();
            SyntaxTree node = visit.node();
            if (visit.expanded()) {
                boolean right = values.pop();
                boolean left = values.pop();
                values.push(node.nodeType == NodeType.CONCATENATION ? left && right : left || right);
            } else {
                switch (node.nodeType) {
                    case LETTER, DOT -> values.push(false);
                    case STAR -> values.push(true);
                    case PROTECTION -> pending.push(new Visit(node.left, false));
                    case CONCATENATION, ALTERNATION -> {
                        pending.push(new Visit(node, true));
                        pending.push(new Visit(node.right, false));
                        pending.push(new Visit(node.left, false));
                    }
                    default -> throw new IllegalArgumentException("Type de nœud non supporté : " + node.nodeType);
                }
            }
        }
        return values.pop();
    }

    /** Rendu itératif en O(nombre de nœuds + taille de la sortie). */
    @Override
    public String toString() {
        record Visit(SyntaxTree node, int depth, boolean tail) {}
        StringBuilder output = new StringBuilder();
        StringBuilder prefix = new StringBuilder();
        Deque<Visit> pending = new ArrayDeque<>();
        pending.push(new Visit(this, 0, true));
        while (!pending.isEmpty()) {
            Visit visit = pending.pop();
            SyntaxTree node = visit.node();
            prefix.setLength(visit.depth() * 4);
            output.append(prefix).append(visit.tail() ? "└── " : "├── ")
                    .append(getNodeLabel(node)).append('\n');
            prefix.append(visit.tail() ? "    " : "│   ");
            if (node.right != null) {
                pending.push(new Visit(node.right, visit.depth() + 1, true));
            }
            if (node.left != null) {
                pending.push(new Visit(node.left, visit.depth() + 1, node.right == null));
            }
        }
        return output.toString();
    }

    /**
     * Returns the label displayed for a node in the tree view.
     *
     * @param node the node whose label must be displayed
     * @return readable symbol for the node type
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