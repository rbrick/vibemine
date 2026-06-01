package io.rcw.vibemine.ai.plugin.runtime.dsl;

import java.util.List;

@FunctionalInterface
public interface DslFunction {
    Object call(DslContext ctx, List<Expr> args);
}
