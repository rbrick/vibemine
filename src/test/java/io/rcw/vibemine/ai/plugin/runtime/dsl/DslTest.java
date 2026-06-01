package io.rcw.vibemine.ai.plugin.runtime.dsl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class DslTest {
    @Test
    void tokenizerKeepsGoStyleStringsAndNumericSeparators() {
        var tokens = new Tokenizer("name == \"snow\\nworld\" && amount >= 1_024.5").tokenize();

        assertEquals(TokenType.IDENTIFIER, tokens.get(0).type());
        assertEquals("name", tokens.get(0).literal());
        assertEquals("==", tokens.get(1).literal());
        assertEquals(TokenType.STRING, tokens.get(2).type());
        assertEquals("snow\nworld", tokens.get(2).literal());
        assertEquals("&&", tokens.get(3).literal());
        assertEquals("amount", tokens.get(4).literal());
        assertEquals(">=", tokens.get(5).literal());
        assertEquals(1024.5, tokens.get(6).number(), 0.000001);
    }

    @Test
    void commaAfterNumberIsFunctionArgumentSeparatorNotNumericSeparator() {
        var tokens = new Tokenizer("noise2(x/80,z/80,seed)").tokenize();

        assertEquals("80", tokens.get(4).literal());
        assertEquals(",", tokens.get(5).literal());
        assertDoesNotThrow(() -> Expr.compile("noise2(x/80,z/80,seed)"));
    }

    @Test
    void interpretedDslHandlesPrecedenceTernaryAndFunctions() {
        var expr = Expr.compile("x + y * 3 > 10 ? floor((sin(x) + cos(y) + 2) * 10) : 7");
        Object result = expr.eval(DslContext.withDefaults(Map.of("x", 3, "y", 4)));

        assertInstanceOf(Double.class, result);
        assertTrue((Double) result >= 0);
        assertTrue((Double) result <= 40);
        assertEquals(7.0, Expr.eval("x + y * 3 > 10 ? 1 : 7", Map.of("x", 1, "y", 2)));
    }

    @Test
    void compiledNumericMatchesInterpreter() {
        String source = "64 + floor((sin(x/80) + cos(z/80) + mod(seed,10)) * 3) + (x > z ? 2 : -2)";
        var variables = List.of("x", "z", "seed");
        var compiled = new AsmExpressionCompiler().compile(source, variables);

        double[] args = {100, 200, 12345};
        double actual = compiled.eval(args);
        double expected = DslValues.number(Expr.eval(source, Map.of("x", 100.0, "z", 200.0, "seed", 12345.0)));

        assertEquals(expected, actual, 0.000001);
    }

    @Test
    void compileNumericReusesSameWrapperForSameSourceAndVariables() {
        VibeDsl dsl = new VibeDsl();
        var first = dsl.compileNumeric("x*y", List.of("x", "y"));
        var second = dsl.compileNumeric("x*y", List.of("x", "y"));
        var differentOrder = dsl.compileNumeric("x*y", List.of("y", "x"));

        assertSame(first, second);
        assertNotSame(first, differentOrder);
        assertEquals(6.0, first.eval(Map.of("x", 3, "y", 2)), 0.000001);
        assertEquals(6.0, first.evalArray(new double[]{3, 2}), 0.000001);
    }

    @Test
    @SuppressWarnings("unchecked")
    void compiledExpressionCachesEvaluationResults() throws Exception {
        VibeDsl dsl = new VibeDsl();
        var expr = dsl.compileNumeric("x*y", List.of("x", "y"));

        assertEquals(6.0, expr.evalArray(new double[]{3, 2}), 0.000001);
        assertEquals(6.0, expr.evalArray(new double[]{3, 2}), 0.000001);

        Field cacheField = expr.getClass().getDeclaredField("valueCache");
        cacheField.setAccessible(true);
        var cache = (ConcurrentHashMap<?, ?>) cacheField.get(expr);

        assertEquals(1, cache.size());
        assertTrue(cache.containsValue(6.0));
    }

    @Test
    void unknownVariableFailsClearly() {
        DslException exception = assertThrows(DslException.class, () -> Expr.eval("x + missing", Map.of("x", 1)));
        assertTrue(exception.getMessage().contains("unknown variable"));
    }
}
