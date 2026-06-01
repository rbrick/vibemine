package io.rcw.vibemine.ai.plugin.runtime.dsl;

import java.util.Objects;

final class DslValues {
    private DslValues() {}
    static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof Boolean b) return b ? 1 : 0;
        if (value instanceof String s) return Double.parseDouble(s);
        if (value == null) return 0;
        throw new DslException("not a number: " + value);
    }
    static boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) return Boolean.parseBoolean(s) || (!s.isBlank() && !s.equals("0"));
        return value != null;
    }
    static boolean equals(Object a, Object b) {
        if (a instanceof Number || b instanceof Number) return Double.compare(number(a), number(b)) == 0;
        return Objects.equals(a, b);
    }
}
