package io.rcw.vibemine.ai.plugin.runtime.dsl;

import java.util.ArrayList;
import java.util.List;

public final class Parser {
    private final List<Token> tokens;
    private int pos;

    public Parser(List<Token> tokens) { this.tokens = tokens; }

    public Expr parse() {
        Expr expr = expression(0);
        if (!current().type().equals(TokenType.EOF)) throw error("unexpected token: " + current().literal());
        return expr;
    }

    private Expr expression(int minBindingPower) {
        Token token = current();
        advance();
        Expr left = switch (token.type()) {
            case NUMBER -> new Expr.Literal(token.number());
            case STRING -> new Expr.Literal(token.literal());
            case IDENTIFIER -> identifierOrCall(token.literal());
            case OPERATOR -> switch (token.literal()) {
                case "-", "!" -> new Expr.Unary(token.literal(), expression(90));
                default -> throw error("unexpected prefix operator: " + token.literal());
            };
            case PUNCTUATION -> {
                if (!token.literal().equals("(")) throw error("unexpected punctuation: " + token.literal());
                Expr inner = expression(0);
                expect(")");
                yield inner;
            }
            default -> throw error("unexpected token: " + token.literal());
        };

        while (true) {
            Token op = current();
            if (op.type() == TokenType.PUNCTUATION && op.literal().equals("?")) {
                if (10 < minBindingPower) break;
                advance();
                Expr ifTrue = expression(0);
                expect(":");
                Expr ifFalse = expression(9);
                left = new Expr.Ternary(left, ifTrue, ifFalse);
                continue;
            }
            if (op.type() != TokenType.OPERATOR) break;
            int[] bp = bindingPower(op.literal());
            if (bp[0] < minBindingPower) break;
            advance();
            Expr right = expression(bp[1]);
            left = new Expr.Binary(left, op.literal(), right);
        }
        return left;
    }

    private Expr identifierOrCall(String name) {
        if (!current().literal().equals("(")) return new Expr.Variable(name);
        advance();
        List<Expr> args = new ArrayList<>();
        if (!current().literal().equals(")")) {
            do {
                args.add(expression(0));
                if (!current().literal().equals(",")) break;
                advance();
            } while (true);
        }
        expect(")");
        return new Expr.Call(name, args);
    }

    private int[] bindingPower(String op) {
        return switch (op) {
            case "||" -> new int[]{20, 21};
            case "&&" -> new int[]{30, 31};
            case "==", "!=" -> new int[]{40, 41};
            case "<", ">", "<=", ">=" -> new int[]{50, 51};
            case "+", "-" -> new int[]{60, 61};
            case "*", "/", "%" -> new int[]{70, 71};
            default -> throw error("unknown operator: " + op);
        };
    }

    private void expect(String literal) {
        if (!current().literal().equals(literal)) throw error("expected '" + literal + "' but got '" + current().literal() + "'");
        advance();
    }
    private Token current() { return pos >= tokens.size() ? Token.eof(pos) : tokens.get(pos); }
    private void advance() { if (pos < tokens.size()) pos++; }
    private DslException error(String message) { return new DslException(message + " at token " + current().position()); }
}
