package com.sorbonne.regex;

import java.util.List;
import java.util.ArrayList;

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

    @Override
    public String toString() {
        return toTreeString(this, "", true);
    }

    /**
     * Renders the syntax tree as a branch-based text representation.
     *
     * @param node current node to display
     * @param prefix indentation already applied
     * @param isTail true when this node is the last child of its parent
     * @return a multi-line tree representation readable in a console
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