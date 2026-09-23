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

    public SyntaxTree(SyntaxTree left, SyntaxTree right, NodeType type) {
        this.left = left;
        this.right = right;
        this.nodeType = type;
        this.letter = null;
    }

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
                boolean rightValue = values.pop();
                boolean leftValue = values.pop();
                values.push(node.nodeType == NodeType.CONCATENATION
                        ? leftValue && rightValue : leftValue || rightValue);
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
                    default -> throw new IllegalArgumentException(
                            "Type de nœud non supporté : " + node.nodeType);
                }
            }
        }
        return values.pop();
    }

    /** Rendu itératif en O(nombre de nœuds + taille de la sortie). */
    @Override
    public String toString() {
        record Visit(SyntaxTree node, String prefix, boolean tail) {}
        StringBuilder output = new StringBuilder();
        Deque<Visit> pending = new ArrayDeque<>();
        pending.push(new Visit(this, "", true));
        while (!pending.isEmpty()) {
            Visit visit = pending.pop();
            SyntaxTree node = visit.node();
            output.append(visit.prefix())
                    .append(visit.tail() ? "└── " : "├── ")
                    .append(getNodeLabel(node)).append('\n');
            String childPrefix = visit.prefix() + (visit.tail() ? "    " : "│   ");
            if (node.right != null) {
                pending.push(new Visit(node.right, childPrefix, true));
            }
            if (node.left != null) {
                pending.push(new Visit(node.left, childPrefix, node.right == null));
            }
        }
        return output.toString();
    }

    private String getNodeLabel(SyntaxTree node) {
        if (node.nodeType == NodeType.LETTER) {
            return node.letter == null || node.letter.isEmpty() ? "∅" : node.letter;
        }
        return switch (node.nodeType) {
            case DOT, CONCATENATION -> ".";
            case STAR -> "*";
            case ALTERNATION -> "|";
            case PROTECTION -> "PROTECTION";
            case OPEN_PARENTHESE -> "(";
            case CLOSE_PARENTHESE -> ")";
            default -> "?";
        };
    }
}
