package com.sorbonne.regex;

import java.util.ArrayList;
import java.util.List;

public class RegexParser {

    /**
     * Parse the regex String and return a SyntaxTree.
     * @param regex			The plain regex.
     * @return 			 	The {@link SyntaxTree} corresponding to the regex.
     * @throws Exception 	In case of a syntax error.
     */
    public static SyntaxTree parse(String regex) throws Exception {
        if (regex == null || regex.isEmpty()) {
            throw new Exception("Empty regular expression");
        }

        List<SyntaxTree> result = initSyntaxTreeList(regex);
        return parse(result);
    }

    
    /**
     * Initialize the list of {@link SyntaxTree} for the parsing algorithm.s
     * @param regex		The original regex.
     * @return			The list of SyntaxTree.
     */
    protected static List<SyntaxTree> initSyntaxTreeList(String regex) {
        List<SyntaxTree> result = new ArrayList<>();
        if (regex == null || regex.isEmpty()) {
            return result;
        }

        boolean escaped = false;
        for (char c : regex.toCharArray()) {
            if (escaped) {
                result.add(new SyntaxTree(String.valueOf(c)));
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            // create the syntax tree according the each character
            switch (c) {
                case '(':
                    result.add(new SyntaxTree(null, null, NodeType.OPEN_PARENTHESE));
                    break;
                case ')':
                    result.add(new SyntaxTree(null, null, NodeType.CLOSE_PARENTHESE));
                    break;
                case '*':
                    result.add(new SyntaxTree(null, null, NodeType.STAR));
                    break;
                case '.':
                    result.add(new SyntaxTree(null, null, NodeType.DOT));
                    break;
                case '|':
                    result.add(new SyntaxTree(null, null, NodeType.ALTERNATION));
                    break;
                default:
                    result.add(new SyntaxTree(String.valueOf(c)));
                    break;
            }
        }

        if (escaped) {
            result.add(new SyntaxTree("\\"));
        }

        return result;
    }

    /**
     * Main loop of parsing ordered by priority.
     * @param result		The list of syntax trees.
     * @return				The final SyntaxTree.
     * @throws Exception	If the reduction fails.
     */
    private static SyntaxTree parse(List<SyntaxTree> result) throws Exception {
        while (containParenthese(result)) {
            result = processParenthese(result);
        }
        while (containEtoile(result)) {
            result = processEtoile(result);
        }
        while (containConcat(result)) {
            result = processConcat(result);
        }
        while (containAltern(result)) {
            result = processAltern(result);
        }

        if (result.size() != 1) {
            throw new Exception("Syntax error : incomplete reduction of the Syntax Tree list.");
        }

        // return the final tree cleaned from the protection nodes
        return removeProtection(result.get(0));
    }

    /**
     * Checks if the tree contains parentheses.
     * @param trees		The tree to check.
     * @return			boolean corresponding to the result of the check.
     */
    private static boolean containParenthese(List<SyntaxTree> trees) {
        for (SyntaxTree t : trees) {
            if (t.getNodeType() == NodeType.OPEN_PARENTHESE || t.getNodeType() == NodeType.CLOSE_PARENTHESE) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Process the parentheses in the array of trees, 
     * once treated the expression with parentheses is under a tree with a PROTECTION root.
     * @param trees			The Syntax trees to process.
     * @return				The new list with the parentheses processed.
     * @throws Exception	a parentheses insn't closed.
     */
    private static List<SyntaxTree> processParenthese(List<SyntaxTree> trees) throws Exception {
        List<SyntaxTree> result = new ArrayList<>();
        boolean found = false;

        for (SyntaxTree t : trees) {
            if (!found && t.getNodeType() == NodeType.CLOSE_PARENTHESE) {
                boolean done = false;
                List<SyntaxTree> content = new ArrayList<>();

                // pop until finding '('
                while (!done && !result.isEmpty()) {
                    SyntaxTree last = result.remove(result.size() - 1);
                    if (last.getNodeType() == NodeType.OPEN_PARENTHESE) {
                        done = true;
                    } else {
                        content.add(0, last);
                    }
                }

                if (!done) {
                    throw new Exception("Syntax error : ')' without '(' associated");
                }

                found = true;
                SyntaxTree subTree = parse(content);
                // Isolate the sub tree under a PROTECTION node
                result.add(new SyntaxTree(subTree, null, NodeType.PROTECTION));
            } else {
                result.add(t);
            }
        }

        if (!found) {
            throw new Exception("Erreur de parenthésage : '(' sans ')' correspondant");
        }

        return result;
    }

    /**
     * Checks if the tree contains a star.
     * @param trees		The tree to check.
     * @return			boolean corresponding to the result of the check.
     */
    private static boolean containEtoile(List<SyntaxTree> trees) {
        for (SyntaxTree t : trees) {
            if (t.getNodeType() == NodeType.STAR && t.getLeft() == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Process the stars in the array of trees.
     * @param trees			The Syntax trees to process.
     * @return				The new list with the stars processed.
     * @throws Exception	If an argument is missing.
     */
    private static List<SyntaxTree> processEtoile(List<SyntaxTree> trees) throws Exception {
        List<SyntaxTree> result = new ArrayList<>();
        boolean found = false;

        for (SyntaxTree t : trees) {
            if (!found && t.getNodeType() == NodeType.STAR && t.getLeft() == null) {
                if (result.isEmpty()) {
                    throw new Exception("Syntax error : '*' without argument");
                }
                found = true;
                SyntaxTree operand = result.remove(result.size() - 1);
                result.add(new SyntaxTree(operand, null, NodeType.STAR));
            } else {
                result.add(t);
            }
        }

        return result;
    }

    /** 
     * Checks if the tree contains a concatenation.
     * @param trees		The tree to check.
     * @return			boolean corresponding to the result of the check.
     */
    private static boolean containConcat(List<SyntaxTree> trees) {
        boolean firstFound = false;
        for (SyntaxTree t : trees) {
            if (!firstFound && t.getNodeType() != NodeType.ALTERNATION) {
                firstFound = true;
                continue;
            }
            if (firstFound) {
                if (t.getNodeType() != NodeType.ALTERNATION) {
                    return true;
                } else {
                    firstFound = false;
                }
            }
        }
        return false;
    }

    /**
     * Process the concatenation in the array of trees.
     * @param trees			The Syntax trees to process.
     * @return				The new list with the concatenations processed.
     * @throws Exception	If an error occur.
     */
    private static List<SyntaxTree> processConcat(List<SyntaxTree> trees) throws Exception {
        List<SyntaxTree> result = new ArrayList<>();
        boolean found = false;
        boolean firstFound = false;

        for (SyntaxTree t : trees) {
            if (!found && !firstFound && t.getNodeType() != NodeType.ALTERNATION) {
                firstFound = true;
                result.add(t);
                continue;
            }
            if (!found && firstFound && t.getNodeType() == NodeType.ALTERNATION) {
                firstFound = false;
                result.add(t);
                continue;
            }
            if (!found && firstFound && t.getNodeType() != NodeType.ALTERNATION) {
                found = true;
                SyntaxTree left = result.remove(result.size() - 1);
                result.add(new SyntaxTree(left, t, NodeType.CONCATENATION));
            } else {
                result.add(t);
            }
        }

        return result;
    }

    /** 
     * Checks if the tree contains alternation.
     * @param trees		The tree to check.
     * @return			boolean corresponding to the result of the check.
     */
    private static boolean containAltern(List<SyntaxTree> trees) {
        for (SyntaxTree t : trees) {
            if (t.getNodeType() == NodeType.ALTERNATION && t.getLeft() == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Process the alternation in the array of trees.
     * @param trees			The Syntax trees to process.
     * @return				The new list with the alternation processed.
     * @throws Exception	If an error occur.
     */
    private static List<SyntaxTree> processAltern(List<SyntaxTree> trees) throws Exception {
        List<SyntaxTree> result = new ArrayList<>();
        boolean found = false;
        SyntaxTree left = null;
        boolean done = false;

        for (SyntaxTree t : trees) {
            if (!found && t.getNodeType() == NodeType.ALTERNATION && t.getLeft() == null) {
                if (result.isEmpty()) {
                    throw new Exception("Syntax error : '|' without left operator ");
                }
                found = true;
                left = result.remove(result.size() - 1);
                continue;
            }

            if (found && !done) {
                if (left == null) {
                    throw new Exception("Error while processing '|");
                }
                done = true;
                result.add(new SyntaxTree(left, t, NodeType.ALTERNATION));
            } else {
                result.add(t);
            }
        }

        if (found && !done) {
            throw new Exception("Erreur de syntaxe : '|' sans opérande droit");
        }

        return result;
    }

    /**
     * Remove the protection nodes from the final tree.
     * @param tree			The tree to cleanup.
     * @return				The tree without the protection nodes.
     * @throws Exception	If a protection node has no child.
     */
    private static SyntaxTree removeProtection(SyntaxTree tree) throws Exception {
        if (tree == null) {
            return null;
        }

        if (tree.getNodeType() == NodeType.PROTECTION) {
            if (tree.getLeft() == null) {
                throw new Exception("Protection node without child");
            }
            return removeProtection(tree.getLeft());
        }

        SyntaxTree cleanLeft = removeProtection(tree.getLeft());
        SyntaxTree cleanRight = removeProtection(tree.getRight());

        tree.setLeft(cleanLeft);
        tree.setRight(cleanRight);

        return tree;
    }
}