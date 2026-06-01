package io.rcw.vibemine.ai.plugin.runtime.dsl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DslContext {
    private final Map<String, Object> variables;
    private final Map<String, DslFunction> functions;

    public DslContext(Map<String, Object> variables, Map<String, DslFunction> functions) {
        this.variables = variables == null ? Map.of() : variables;
        this.functions = functions == null ? Map.of() : functions;
    }

    public Object resolve(String name) {
        if (name.equals("true")) return true;
        if (name.equals("false")) return false;
        if (name.equals("null")) return null;
        if (variables.containsKey(name)) return variables.get(name);
        throw new DslException("unknown variable: " + name);
    }

    public Object call(String name, List<Expr> args) {
        DslFunction fn = functions.get(name);
        if (fn == null) throw new DslException("unknown function: " + name);
        return fn.call(this, args);
    }

    public static DslContext withDefaults(Map<String, Object> variables) {
        return new DslContext(variables, defaultFunctions());
    }

    public static Map<String, DslFunction> defaultFunctions() {
        Map<String, DslFunction> f = new HashMap<>();
        f.put("abs", (c,a) -> Math.abs(DslValues.number(a.getFirst().eval(c))));
        f.put("floor", (c,a) -> Math.floor(DslValues.number(a.getFirst().eval(c))));
        f.put("ceil", (c,a) -> Math.ceil(DslValues.number(a.getFirst().eval(c))));
        f.put("sin", (c,a) -> Math.sin(DslValues.number(a.getFirst().eval(c))));
        f.put("cos", (c,a) -> Math.cos(DslValues.number(a.getFirst().eval(c))));
        f.put("sqrt", (c,a) -> Math.sqrt(DslValues.number(a.getFirst().eval(c))));
        f.put("min", (c,a) -> Math.min(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c))));
        f.put("max", (c,a) -> Math.max(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c))));
        f.put("mod", (c,a) -> { double x = DslValues.number(a.get(0).eval(c)); double y = DslValues.number(a.get(1).eval(c)); return DslMath.mod(x, y); });
        f.put("noise2", (c,a) -> DslMath.noise2(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c)), a.size() > 2 ? DslValues.number(a.get(2).eval(c)) : 0));
        f.put("perlin2", (c,a) -> DslMath.perlin2(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c)), a.size() > 2 ? DslValues.number(a.get(2).eval(c)) : 0));
        f.put("simplex2", (c,a) -> DslMath.simplex2(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c)), a.size() > 2 ? DslValues.number(a.get(2).eval(c)) : 0));
        f.put("fbm2", (c,a) -> DslMath.fbm2(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c)), DslValues.number(a.get(2).eval(c)), DslValues.number(a.get(3).eval(c)), DslValues.number(a.get(4).eval(c)), DslValues.number(a.get(5).eval(c))));
        f.put("ridged2", (c,a) -> DslMath.ridged2(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c)), DslValues.number(a.get(2).eval(c)), DslValues.number(a.get(3).eval(c)), DslValues.number(a.get(4).eval(c)), DslValues.number(a.get(5).eval(c))));
        f.put("billow2", (c,a) -> DslMath.billow2(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c)), DslValues.number(a.get(2).eval(c)), DslValues.number(a.get(3).eval(c)), DslValues.number(a.get(4).eval(c)), DslValues.number(a.get(5).eval(c))));
        f.put("warpX", (c,a) -> DslMath.warpX(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c)), DslValues.number(a.get(2).eval(c)), DslValues.number(a.get(3).eval(c)), DslValues.number(a.get(4).eval(c))));
        f.put("warpZ", (c,a) -> DslMath.warpZ(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c)), DslValues.number(a.get(2).eval(c)), DslValues.number(a.get(3).eval(c)), DslValues.number(a.get(4).eval(c))));
        f.put("terrace", (c,a) -> DslMath.terrace(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c))));
        f.put("smoothstep", (c,a) -> DslMath.smoothstep(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c)), DslValues.number(a.get(2).eval(c))));
        f.put("clamp", (c,a) -> DslMath.clamp(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c)), DslValues.number(a.get(2).eval(c))));
        f.put("lerp", (c,a) -> DslMath.lerp(DslValues.number(a.get(0).eval(c)), DslValues.number(a.get(1).eval(c)), DslValues.number(a.get(2).eval(c))));
        f.put("length", (c,a) -> { Object v = a.getFirst().eval(c); if (v instanceof String s) return (double) s.length(); if (v instanceof java.util.Collection<?> col) return (double) col.size(); return 0.0; });
        return Map.copyOf(f);
    }
}
