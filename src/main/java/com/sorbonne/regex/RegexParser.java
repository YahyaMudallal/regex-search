package com.sorbonne.regex;

import java.util.ArrayList;
import java.util.List;

public class RegexParser {

    /**
     * Analyse l'expression régulière et retourne son arbre syntaxique.
     *
     * @param regex l'expression régulière brute
     * @return l'{@link SyntaxTree} correspondant à l'expression
     * @throws Exception en cas d'erreur de syntaxe
     */
    public static SyntaxTree parse(String regex) throws Exception {
        if (regex == null || regex.isEmpty()) {
            throw new Exception("Empty regular expression");
        }

        List<SyntaxTree> result = initSyntaxTreeList(regex);
        return parse(result);
    }

    /**
     * Initialise la liste de {@link SyntaxTree} utilisée par l'algorithme d'analyse.
     *
     * @param regex l'expression régulière d'origine
     * @return la liste des arbres syntaxiques
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
            
            // Crée le noeud syntaxique correspondant à chaque caractère
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
     * Boucle principale de l'analyse, selon l'ordre de priorité des opérateurs.
     *
     * @param result la liste des arbres syntaxiques
     * @return l'arbre syntaxique final
     * @throws Exception si une réduction échoue
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

        // Retourne l'arbre final débarrassé des noeuds de protection
        return removeProtection(result.get(0));
    }

    /**
     * Vérifie si la liste contient des parenthèses.
     *
     * @param trees la liste à vérifier
     * @return vrai si une parenthèse est présente
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
     * Traite les parenthèses de la liste.
     * Une expression parenthésée est alors placée sous un noeud racine PROTECTION.
     *
     * @param trees les arbres syntaxiques à traiter
     * @return la nouvelle liste après traitement des parenthèses
     * @throws Exception si une parenthèse n'est pas fermée
     */
    private static List<SyntaxTree> processParenthese(List<SyntaxTree> trees) throws Exception {
        List<SyntaxTree> result = new ArrayList<>();
        boolean found = false;

        for (SyntaxTree t : trees) {
            if (!found && t.getNodeType() == NodeType.CLOSE_PARENTHESE) {
                boolean done = false;
                List<SyntaxTree> content = new ArrayList<>();

                // Retire les noeuds jusqu'à trouver '('
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
                // Isole le sous-arbre sous un noeud PROTECTION
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
     * Vérifie si la liste contient une étoile.
     *
     * @param trees la liste à vérifier
     * @return vrai si une étoile non traitée est présente
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
     * Traite les étoiles de la liste.
     *
     * @param trees les arbres syntaxiques à traiter
     * @return la nouvelle liste après traitement des étoiles
     * @throws Exception si l'opérande de l'étoile est absente
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
     * Vérifie si la liste contient une concaténation.
     *
     * @param trees la liste à vérifier
     * @return vrai si une concaténation est présente
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
     * Traite les concaténations de la liste.
     *
     * @param trees les arbres syntaxiques à traiter
     * @return la nouvelle liste après traitement des concaténations
     * @throws Exception en cas d'erreur lors du traitement
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
     * Vérifie si la liste contient une alternance.
     *
     * @param trees la liste à vérifier
     * @return vrai si une alternance non traitée est présente
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
     * Traite les alternances de la liste.
     *
     * @param trees les arbres syntaxiques à traiter
     * @return la nouvelle liste après traitement des alternances
     * @throws Exception en cas d'erreur lors du traitement
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
     * Supprime les noeuds de protection de l'arbre final.
     *
     * @param tree l'arbre à nettoyer
     * @return l'arbre sans les noeuds de protection
     * @throws Exception si un noeud de protection n'a pas de fils
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