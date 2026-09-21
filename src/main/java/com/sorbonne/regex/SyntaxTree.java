package com.sorbonne.regex;

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
        if (nodeType == NodeType.LETTER) {
            return letter;
        }
        if (nodeType == NodeType.DOT) {
            return ".";
        }
        if (nodeType == NodeType.STAR) {
            return left != null ? left.toString() + "*" : "*";
        }
        if (nodeType == NodeType.CONCATENATION) {
            return ".(" + (left != null ? left.toString() : "") + "," + (right != null ? right.toString() : "") + ")";
        }
        if (nodeType == NodeType.ALTERNATION) {
            return "|(" + (left != null ? left.toString() : "") + "," + (right != null ? right.toString() : "") + ")";
        }
        if (nodeType == NodeType.PROTECTION) {
            return left != null ? left.toString() : "";
        }
        return "?";
    }
}