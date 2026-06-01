package io.rcw.vibemine.ai.plugin.runtime.dsl;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Global entry point for compiling/evaluating safe Vibe DSL expressions. */
public final class VibeDsl {
    private static final ConcurrentHashMap<NumericKey, CompiledNumericExpression> NUMERIC_CACHE = new ConcurrentHashMap<>();

    public DslExpression compile(String source) { return new DslExpression(Expr.compile(source)); }
    public CompiledNumericExpression compileNumeric(String source, java.util.List<String> variables) {
        NumericKey key = new NumericKey(source, java.util.List.copyOf(variables));
        return NUMERIC_CACHE.computeIfAbsent(key, ignored -> new CompiledNumericExpression(new AsmExpressionCompiler().compile(source, variables), variables));
    }
    public Object eval(String source, Map<String, Object> variables) { return compile(source).eval(variables); }

    public static final class DslExpression {
        private final Expr expr;
        private DslExpression(Expr expr) { this.expr = expr; }
        public Object eval(Map<String, Object> variables) { return expr.eval(DslContext.withDefaults(variables)); }
        public double evalNumber(Map<String, Object> variables) { return DslValues.number(eval(variables)); }
        public boolean evalBoolean(Map<String, Object> variables) { return DslValues.bool(eval(variables)); }
        public String evalString(Map<String, Object> variables) { Object result = eval(variables); return result == null ? null : String.valueOf(result); }
    }

    public static final class CompiledNumericExpression {
        private static final int MAX_VALUE_CACHE_ENTRIES = 100_000;
        private final CompiledExpression expression;
        private final java.util.List<String> variables;
        private final ConcurrentHashMap<ValueKey, Double> valueCache = new ConcurrentHashMap<>();
        private CompiledNumericExpression(CompiledExpression expression, java.util.List<String> variables) {
            this.expression = expression;
            this.variables = java.util.List.copyOf(variables);
        }
        public double eval(Map<String, Object> values) {
            double[] args = new double[variables.size()];
            for (int i = 0; i < variables.size(); i++) args[i] = DslValues.number(values.get(variables.get(i)));
            return evalArray(args);
        }
        public double evalArray(double[] values) {
            if (valueCache.size() >= MAX_VALUE_CACHE_ENTRIES) return expression.eval(values);
            ValueKey key = new ValueKey(values);
            return valueCache.computeIfAbsent(key, ignored -> expression.eval(values));
        }
        public double evalArrayUncached(double[] values) { return expression.eval(values); }
    }

    private record NumericKey(String source, java.util.List<String> variables) {}

    private record ValueKey(double[] values) {
        private ValueKey(double[] values) { this.values = Arrays.copyOf(values, values.length); }
        @Override public boolean equals(Object object) { return object instanceof ValueKey other && Arrays.equals(values, other.values); }
        @Override public int hashCode() { return Arrays.hashCode(values); }
    }
}
