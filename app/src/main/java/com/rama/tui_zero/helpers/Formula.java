package com.rama.tui_zero.helpers;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny expression parser. Supports + - * / ^, parentheses, implicit multiplication (2$t, 3($t+1), $t sin $t),
 * the constants pi and e, the usual one-argument functions, and counters written as $name.
 * Trigonometry works in radians.
 * Plain Java on purpose (no java.util.function) so it runs on API 21 without desugaring.
 */
public final class Formula {
    public static final class FormulaException extends Exception {
        public FormulaException(String message) {
            super(message);
        }
    }

    private interface Node {
        double eval(double[] vars);
    }

    private interface Fn {
        double apply(double v);
    }

    private static final String[] FUNCTION_NAMES = {
            "asin", "acos", "atan", "sinh", "cosh", "tanh", "sqrt", "ceil", "floor",
            "sin", "cos", "tan", "abs", "exp", "log", "ln"
    };
    private static final String[] CONSTANT_NAMES = {"pi", "e"};

    private static final int NUM = 0;
    private static final int NAME = 1;
    private static final int OP = 2;
    private static final int END = 3;
    private static final int VAR = 4;

    private static final class Token {
        final int type;
        final String text;
        final double number;
        final int pos;

        Token(int type, String text, double number, int pos) {
            this.type = type;
            this.text = text;
            this.number = number;
            this.pos = pos;
        }
    }

    private final Node root;

    private Formula(Node root) {
        this.root = root;
    }

    /** vars holds one value per name given to parse(), in the same order. */
    public double eval(double[] vars) {
        return root.eval(vars);
    }

    /** True for a name usable as $name: a letter or underscore, then letters, digits or underscores. */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty()) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
            if (!(letter || (i > 0 && isDigit(c)))) return false;
        }
        return true;
    }

    /** variables are the counter names that $name may refer to. */
    public static Formula parse(String text, String[] variables) throws FormulaException {
        String source = text == null ? "" : text.trim();
        source = stripPrefix(source);
        if (source.isEmpty()) throw new FormulaException("empty formula");
        return new Parser(tokenize(source), variables).parseAll();
    }

    /** Accepts "x = ..." and "y = ..." as well as a bare expression. */
    private static String stripPrefix(String s) {
        String compact = s.replace(" ", "").toLowerCase();
        if (compact.startsWith("y=") || compact.startsWith("x=")) return s.substring(s.indexOf('=') + 1).trim();
        return s;
    }

    // Tokenizer

    private static List<Token> tokenize(String s) throws FormulaException {
        List<Token> out = new ArrayList<Token>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (isDigit(c) || (c == '.' && i + 1 < s.length() && isDigit(s.charAt(i + 1)))) {
                int start = i;
                boolean dot = false;
                while (i < s.length() && (isDigit(s.charAt(i)) || (s.charAt(i) == '.' && !dot))) {
                    if (s.charAt(i) == '.') dot = true;
                    i++;
                }
                out.add(new Token(NUM, null, Double.parseDouble(s.substring(start, i)), start));
            } else if (c == '$') {
                int start = i++;
                int nameStart = i;
                while (i < s.length() && (isDigit(s.charAt(i)) || s.charAt(i) == '_' || (s.charAt(i) < 128 && Character.isLetter(s.charAt(i))))) i++;
                if (i == nameStart) throw new FormulaException("'$' needs a counter name");
                out.add(new Token(VAR, s.substring(nameStart, i), 0, start));
            } else if (Character.isLetter(c)) {
                int start = i;
                while (i < s.length() && Character.isLetter(s.charAt(i)) && s.charAt(i) != 'π') i++;
                if (i == start) i++; // a lone pi sign
                splitNames(s.substring(start, i).toLowerCase(), start, out);
            } else {
                out.add(new Token(OP, normalizeOp(c), 0, i));
                if (!isOp(normalizeOp(c))) throw new FormulaException("unexpected '" + c + "'");
                i++;
            }
        }
        out.add(new Token(END, null, 0, s.length()));
        return out;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static String normalizeOp(char c) {
        switch (c) {
            case '−':
                return "-";
            case '×':
            case '·':
                return "*";
            case '÷':
                return "/";
            default:
                return String.valueOf(c);
        }
    }

    private static boolean isOp(String op) {
        return "+-*/^()".contains(op);
    }

    /** "xsin" -> x, sin. Greedy longest match, so "exp" is a function and "ex" is e * x. */
    private static void splitNames(String run, int pos, List<Token> out) throws FormulaException {
        int i = 0;
        while (i < run.length()) {
            String match = null;
            if (run.charAt(i) == 'π') {
                match = "pi";
                i++;
            } else {
                for (String name : FUNCTION_NAMES) {
                    if (run.startsWith(name, i) && (match == null || name.length() > match.length())) match = name;
                }
                for (String name : CONSTANT_NAMES) {
                    if (run.startsWith(name, i) && (match == null || name.length() > match.length())) match = name;
                }
                if (match == null) throw new FormulaException("unknown name '" + run.substring(i) + "'");
                i += match.length();
            }
            out.add(new Token(NAME, match, 0, pos));
        }
    }

    // Parser

    private static final class Parser {
        private final List<Token> tokens;
        private final String[] variables;
        private int index;

        Parser(List<Token> tokens, String[] variables) {
            this.tokens = tokens;
            this.variables = variables == null ? new String[0] : variables;
        }

        Formula parseAll() throws FormulaException {
            Node node = parseExpr();
            Token t = peek();
            if (t.type != END) throw new FormulaException("unexpected '" + t.text + "'");
            return new Formula(node);
        }

        private Token peek() {
            return tokens.get(index);
        }

        private boolean peekOp(String op) {
            Token t = peek();
            return t.type == OP && t.text.equals(op);
        }

        private boolean startsPrimary() {
            Token t = peek();
            return t.type == NUM || t.type == NAME || t.type == VAR || (t.type == OP && t.text.equals("("));
        }

        private Node parseExpr() throws FormulaException {
            Node left = parseTerm();
            while (peekOp("+") || peekOp("-")) {
                final boolean plus = tokens.get(index++).text.equals("+");
                final Node a = left;
                final Node b = parseTerm();
                left = plus ? new Node() {
                    public double eval(double[] v) {
                        return a.eval(v) + b.eval(v);
                    }
                } : new Node() {
                    public double eval(double[] v) {
                        return a.eval(v) - b.eval(v);
                    }
                };
            }
            return left;
        }

        private Node parseTerm() throws FormulaException {
            Node left = parseUnary();
            while (true) {
                final Node a = left;
                if (peekOp("*") || peekOp("/")) {
                    final boolean times = tokens.get(index++).text.equals("*");
                    final Node b = parseUnary();
                    left = times ? new Node() {
                        public double eval(double[] v) {
                            return a.eval(v) * b.eval(v);
                        }
                    } : new Node() {
                        public double eval(double[] v) {
                            return a.eval(v) / b.eval(v);
                        }
                    };
                } else if (startsPrimary()) { // implicit multiplication: 2x, 3(x+1), (x+1)(x-1)
                    final Node b = parseUnary();
                    left = new Node() {
                        public double eval(double[] v) {
                            return a.eval(v) * b.eval(v);
                        }
                    };
                } else {
                    return left;
                }
            }
        }

        /** -x^2 is -(x^2), so the sign binds looser than the power. */
        private Node parseUnary() throws FormulaException {
            if (peekOp("-")) {
                index++;
                final Node inner = parseUnary();
                return new Node() {
                    public double eval(double[] v) {
                        return -inner.eval(v);
                    }
                };
            }
            if (peekOp("+")) {
                index++;
                return parseUnary();
            }
            return parsePower();
        }

        /** Right associative: 2^3^2 is 2^(3^2), and 2^-x works because the exponent is a unary. */
        private Node parsePower() throws FormulaException {
            final Node base = parsePrimary();
            if (peekOp("^")) {
                index++;
                final Node exp = parseUnary();
                return new Node() {
                    public double eval(double[] v) {
                        return Math.pow(base.eval(v), exp.eval(v));
                    }
                };
            }
            return base;
        }

        private Node parsePrimary() throws FormulaException {
            Token t = peek();
            if (t.type == NUM) {
                index++;
                final double value = t.number;
                return new Node() {
                    public double eval(double[] v) {
                        return value;
                    }
                };
            }
            if (t.type == OP && t.text.equals("(")) {
                index++;
                Node inner = parseExpr();
                expectClose();
                return inner;
            }
            if (t.type == VAR) {
                index++;
                return varNode(t.text);
            }
            if (t.type == NAME) {
                index++;
                return nameNode(t.text);
            }
            if (t.type == END) throw new FormulaException("unexpected end");
            throw new FormulaException("unexpected '" + t.text + "'");
        }

        private void expectClose() throws FormulaException {
            if (!peekOp(")")) throw new FormulaException("missing ')'");
            index++;
        }

        private Node varNode(String name) throws FormulaException {
            for (int i = 0; i < variables.length; i++) {
                if (variables[i].equals(name)) {
                    final int slot = i;
                    return new Node() {
                        public double eval(double[] v) {
                            return v[slot];
                        }
                    };
                }
            }
            throw new FormulaException("no counter named $" + name);
        }

        private Node nameNode(String name) throws FormulaException {
            if (name.equals("pi")) return constant(Math.PI);
            if (name.equals("e")) return constant(Math.E);
            final Fn fn = function(name);
            final Node arg;
            if (peekOp("(")) {
                index++;
                arg = parseExpr();
                expectClose();
            } else {
                if (!startsPrimary()) throw new FormulaException("'" + name + "' needs an argument");
                arg = parsePower(); // "sin x^2" is sin(x^2)
            }
            return new Node() {
                public double eval(double[] v) {
                    return fn.apply(arg.eval(v));
                }
            };
        }

        private static Node constant(final double value) {
            return new Node() {
                public double eval(double[] v) {
                    return value;
                }
            };
        }

        private static Fn function(String name) throws FormulaException {
            switch (name) {
                case "sin":
                    return v -> Math.sin(v);
                case "cos":
                    return v -> Math.cos(v);
                case "tan":
                    return v -> Math.tan(v);
                case "asin":
                    return v -> Math.asin(v);
                case "acos":
                    return v -> Math.acos(v);
                case "atan":
                    return v -> Math.atan(v);
                case "sinh":
                    return v -> Math.sinh(v);
                case "cosh":
                    return v -> Math.cosh(v);
                case "tanh":
                    return v -> Math.tanh(v);
                case "sqrt":
                    return v -> Math.sqrt(v);
                case "abs":
                    return v -> Math.abs(v);
                case "exp":
                    return v -> Math.exp(v);
                case "ln":
                    return v -> Math.log(v);
                case "log":
                    return v -> Math.log10(v);
                case "floor":
                    return v -> Math.floor(v);
                case "ceil":
                    return v -> Math.ceil(v);
                default:
                    throw new FormulaException("unknown function '" + name + "'");
            }
        }
    }
}
