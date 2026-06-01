package io.rcw.vibemine.ai.plugin.runtime.dsl;

import java.util.List;
import java.util.Map;

public sealed interface Expr permits Expr.Literal, Expr.Variable, Expr.Unary, Expr.Binary, Expr.Call, Expr.Ternary {
    Object eval(DslContext ctx);

    record Literal(Object value) implements Expr { public Object eval(DslContext ctx) { return value; } }
    record Variable(String name) implements Expr { public Object eval(DslContext ctx) { return ctx.resolve(name); } }
    record Unary(String op, Expr expr) implements Expr {
        public Object eval(DslContext ctx) {
            Object v = expr.eval(ctx);
            return switch (op) { case "-" -> -DslValues.number(v); case "!" -> !DslValues.bool(v); default -> throw new DslException("bad unary op " + op); };
        }
    }
    record Binary(Expr left, String op, Expr right) implements Expr {
        public Object eval(DslContext ctx) {
            if (op.equals("&&")) return DslValues.bool(left.eval(ctx)) && DslValues.bool(right.eval(ctx));
            if (op.equals("||")) return DslValues.bool(left.eval(ctx)) || DslValues.bool(right.eval(ctx));
            Object l = left.eval(ctx), r = right.eval(ctx);
            return switch (op) {
                case "+" -> (l instanceof String || r instanceof String) ? String.valueOf(l) + r : DslValues.number(l) + DslValues.number(r);
                case "-" -> DslValues.number(l) - DslValues.number(r);
                case "*" -> DslValues.number(l) * DslValues.number(r);
                case "/" -> DslValues.number(l) / DslValues.number(r);
                case "%" -> DslValues.number(l) % DslValues.number(r);
                case "==" -> DslValues.equals(l, r);
                case "!=" -> !DslValues.equals(l, r);
                case "<" -> DslValues.number(l) < DslValues.number(r);
                case ">" -> DslValues.number(l) > DslValues.number(r);
                case "<=" -> DslValues.number(l) <= DslValues.number(r);
                case ">=" -> DslValues.number(l) >= DslValues.number(r);
                default -> throw new DslException("bad binary op " + op);
            };
        }
    }
    record Call(String name, List<Expr> args) implements Expr {
        public Object eval(DslContext ctx) { return ctx.call(name, args); }
    }
    record Ternary(Expr condition, Expr ifTrue, Expr ifFalse) implements Expr {
        public Object eval(DslContext ctx) { return DslValues.bool(condition.eval(ctx)) ? ifTrue.eval(ctx) : ifFalse.eval(ctx); }
    }

    static Expr compile(String source) { return new Parser(new Tokenizer(source).tokenize()).parse(); }
    static Object eval(String source, Map<String, Object> vars) { return compile(source).eval(DslContext.withDefaults(vars)); }
}
