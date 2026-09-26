package com.sorbonne.regex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Analyse itérative à deux piles : O(m) temps et mémoire, arbre compris. */
public class RegexParser {
    /**
     * Construit l'arbre avec les priorités étoile, concaténation, alternative.
     * Chaque opérateur entre et sort de sa pile une seule fois. Les groupes
     * ne créent pas de nœuds de protection à nettoyer après l'analyse.
     *
     * @param regex expression non vide
     * @return arbre syntaxique, sans récursion même pour des groupes profonds
     * @throws Exception si un opérande manque ou si les parenthèses sont incorrectes
     */
    public static SyntaxTree parse(String regex) throws Exception {
        if (regex == null || regex.isEmpty()) {
            throw new Exception("Empty regular expression");
        }
        Deque<SyntaxTree> operands = new ArrayDeque<>();
        Deque<NodeType> operators = new ArrayDeque<>();
        boolean expectsOperand = true;
        for (SyntaxTree token : initSyntaxTreeList(regex)) {
            switch (token.getNodeType()) {
                case LETTER, DOT -> {
                    if (!expectsOperand) {
                        pushOperator(NodeType.CONCATENATION, operators, operands);
                    }
                    operands.push(token);
                    expectsOperand = false;
                }
                case OPEN_PARENTHESE -> {
                    if (!expectsOperand) {
                        pushOperator(NodeType.CONCATENATION, operators, operands);
                    }
                    operators.push(NodeType.OPEN_PARENTHESE);
                    expectsOperand = true;
                }
                case CLOSE_PARENTHESE -> {
                    if (expectsOperand) {
                        throw new Exception("Groupe vide ou opérande manquant avant ')'");
                    }
                    while (!operators.isEmpty() && operators.peek() != NodeType.OPEN_PARENTHESE) {
                        reduce(operators.pop(), operands);
                    }
                    if (operators.isEmpty()) {
                        throw new Exception("')' sans '(' correspondant");
                    }
                    operators.pop();
                    expectsOperand = false;
                }
                case STAR -> {
                    if (expectsOperand) {
                        throw new Exception("'*' sans opérande");
                    }
                    operands.push(new SyntaxTree(operands.pop(), null, NodeType.STAR));
                }
                case ALTERNATION -> {
                    if (expectsOperand) {
                        throw new Exception("'|' sans opérande gauche");
                    }
                    pushOperator(NodeType.ALTERNATION, operators, operands);
                    expectsOperand = true;
                }
                default -> throw new Exception("Jeton inattendu : " + token.getNodeType());
            }
        }
        if (expectsOperand) {
            throw new Exception("Expression incomplète");
        }
        while (!operators.isEmpty()) {
            NodeType operator = operators.pop();
            if (operator == NodeType.OPEN_PARENTHESE) {
                throw new Exception("'(' sans ')' correspondant");
            }
            reduce(operator, operands);
        }
        return operands.pop();
    }

    /**
     * Pousse un opérateur sur la pile des opérateurs.
     * 
     * @param operator l'opérateur à pousser
     * @param operators la pile des opérateurs
     * @param operands la pile des opérandes
    */
    private static void pushOperator(NodeType operator, Deque<NodeType> operators,
            Deque<SyntaxTree> operands) {
        while (!operators.isEmpty() && operators.peek() != NodeType.OPEN_PARENTHESE
                && priority(operators.peek()) >= priority(operator)) {
            reduce(operators.pop(), operands);
        }
        operators.push(operator);
    }

    /**
     * Renvoie la priorité d'un opérateur.
     * 
     * @param operator l'opérateur
     * @return la priorité
     */
    private static int priority(NodeType operator) {
        return operator == NodeType.CONCATENATION ? 2 : 1;
    }

    /**
     * Réduit les deux derniers opérandes avec l'opérateur donné.
     * 
     * @param operator l'opérateur à appliquer
     * @param operands la pile des opérandes
     */
    private static void reduce(NodeType operator, Deque<SyntaxTree> operands) {
        SyntaxTree right = operands.pop();
        SyntaxTree left = operands.pop();
        operands.push(new SyntaxTree(left, right, operator));
    }

    /** 
     * Tokenise le sous-ensemble ASCII ; un échappement final reste un antislash littéral.
     * 
     * @param regex expression régulière
     * @return liste de nœuds syntaxiques 
     */
    private static void requireAscii(char symbol) {
        if (symbol > 0x7f) {
            throw new IllegalArgumentException("Le motif doit appartenir a l'alphabet ASCII");
        }
    }

    /**
     * Initialise la liste des nœuds syntaxiques à partir d'une expression régulière.
     * 
     * @param regex expression régulière
     * @return liste de nœuds syntaxiques
     */
    protected static List<SyntaxTree> initSyntaxTreeList(String regex) {
        List<SyntaxTree> result = new ArrayList<>();
        if (regex == null) {
            return result;
        }
        for (int i = 0; i < regex.length(); i++) {
            char symbol = regex.charAt(i);
            requireAscii(symbol);
            if (symbol == '\\') {
                if (i + 1 >= regex.length()) {
                    throw new IllegalArgumentException("Echappement final incomplet");
                }
                symbol = regex.charAt(++i);
                requireAscii(symbol);
                if (".*|()\\".indexOf(symbol) < 0) {
                    throw new IllegalArgumentException("Echappement non pris en charge : \\" + symbol);
                }
                result.add(new SyntaxTree(String.valueOf(symbol)));
                continue;
            }
            if ("+?[]{}^$".indexOf(symbol) >= 0) {
                throw new IllegalArgumentException("Operateur ERE non pris en charge : " + symbol);
            }
            NodeType type = switch (symbol) {
                case '(' -> NodeType.OPEN_PARENTHESE;
                case ')' -> NodeType.CLOSE_PARENTHESE;
                case '*' -> NodeType.STAR;
                case '.' -> NodeType.DOT;
                case '|' -> NodeType.ALTERNATION;
                default -> NodeType.LETTER;
            };
            result.add(type == NodeType.LETTER ? new SyntaxTree(String.valueOf(symbol))
                    : new SyntaxTree(null, null, type));
        }
        return result;
    }
}