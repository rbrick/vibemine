package io.rcw.vibemine.ai.plugin.runtime.dsl;

@FunctionalInterface
public interface CompiledExpression {
    double eval(double[] variables);
}
