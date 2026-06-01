package io.rcw.vibemine.ai.plugin.runtime.dsl;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class AsmExpressionCompiler implements Opcodes {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ConcurrentHashMap<CacheKey, CompiledExpression> CACHE = new ConcurrentHashMap<>();

    public CompiledExpression compile(String source, List<String> variables) {
        CacheKey key = new CacheKey(source, List.copyOf(variables));
        return CACHE.computeIfAbsent(key, ignored -> compileUncached(source, variables));
    }

    public CompiledExpression compileUncached(String source, List<String> variables) {
        try {
            return compileUncached(Expr.compile(source), variables);
        } catch (DslException exception) {
            throw new DslException("Could not compile DSL expression `" + source + "` with variables " + variables + ": " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new DslException("Could not compile DSL expression `" + source + "` with variables " + variables + ": " + exception.getClass().getSimpleName() + ": " + exception.getMessage(), exception);
        }
    }


    public CompiledExpression compile(Expr expr, List<String> variables) {
        return compileUncached(expr, variables);
    }

    private CompiledExpression compileUncached(Expr expr, List<String> variables) {
        try {
            String internalName = "io/rcw/vibemine/ai/plugin/runtime/dsl/CompiledDsl" + IDS.incrementAndGet();
            ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cw.visit(V17, ACC_PUBLIC | ACC_FINAL, internalName, null, "java/lang/Object", new String[] { internal(CompiledExpression.class) });

            MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            init.visitCode();
            init.visitVarInsn(ALOAD, 0);
            init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            init.visitInsn(RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();

            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "eval", "([D)D", null, null);
            mv.visitCode();
            emit(expr, mv, index(variables));
            mv.visitInsn(DRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            cw.visitEnd();

            Class<?> clazz = new BytecodeLoader(AsmExpressionCompiler.class.getClassLoader()).define(internalName.replace('/', '.'), cw.toByteArray());
            return (CompiledExpression) clazz.getConstructor().newInstance();
        } catch (Throwable throwable) {
            throw new DslException("Could not compile DSL bytecode for expression " + expr + " with variables " + variables + ": " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage(), throwable);
        }
    }

    private void emit(Expr expr, MethodVisitor mv, Map<String, Integer> vars) {
        if (expr instanceof Expr.Literal literal) {
            Object value = literal.value();
            if (!(value instanceof Number number)) throw new DslException("ASM compiler only supports numeric literals");
            mv.visitLdcInsn(number.doubleValue());
        } else if (expr instanceof Expr.Variable variable) {
            Integer index = vars.get(variable.name());
            if (index == null) throw new DslException("unknown compiled variable: " + variable.name());
            mv.visitVarInsn(ALOAD, 1);
            mv.visitLdcInsn(index);
            mv.visitInsn(DALOAD);
        } else if (expr instanceof Expr.Unary unary) {
            emit(unary.expr(), mv, vars);
            switch (unary.op()) {
                case "-" -> mv.visitInsn(DNEG);
                case "!" -> emitCompareZero(mv, IFEQ);
                default -> throw new DslException("unsupported unary op for ASM: " + unary.op());
            }
        } else if (expr instanceof Expr.Binary binary) {
            emitBinary(binary, mv, vars);
        } else if (expr instanceof Expr.Call call) {
            emitCall(call, mv, vars);
        } else if (expr instanceof Expr.Ternary ternary) {
            emit(ternary.condition(), mv, vars);
            Label falseLabel = new Label();
            Label end = new Label();
            mv.visitInsn(DCONST_0);
            mv.visitInsn(DCMPL);
            mv.visitJumpInsn(IFEQ, falseLabel);
            emit(ternary.ifTrue(), mv, vars);
            mv.visitJumpInsn(GOTO, end);
            mv.visitLabel(falseLabel);
            emit(ternary.ifFalse(), mv, vars);
            mv.visitLabel(end);
        } else {
            throw new DslException("unknown expression node: " + expr);
        }
    }

    private void emitBinary(Expr.Binary binary, MethodVisitor mv, Map<String, Integer> vars) {
        String op = binary.op();
        if (op.equals("&&") || op.equals("||")) {
            emitLogical(binary, mv, vars);
            return;
        }
        emit(binary.left(), mv, vars);
        emit(binary.right(), mv, vars);
        switch (op) {
            case "+" -> mv.visitInsn(DADD);
            case "-" -> mv.visitInsn(DSUB);
            case "*" -> mv.visitInsn(DMUL);
            case "/" -> mv.visitInsn(DDIV);
            case "%" -> mv.visitInsn(DREM);
            case "==" -> emitComparison(mv, IFEQ);
            case "!=" -> emitComparison(mv, IFNE);
            case "<" -> emitComparison(mv, IFLT);
            case ">" -> emitComparison(mv, IFGT);
            case "<=" -> emitComparison(mv, IFLE);
            case ">=" -> emitComparison(mv, IFGE);
            default -> throw new DslException("unsupported binary op for ASM: " + op);
        }
    }

    private void emitLogical(Expr.Binary binary, MethodVisitor mv, Map<String, Integer> vars) {
        Label yes = new Label();
        Label no = new Label();
        Label end = new Label();
        emit(binary.left(), mv, vars);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DCMPL);
        if (binary.op().equals("&&")) mv.visitJumpInsn(IFEQ, no);
        else mv.visitJumpInsn(IFNE, yes);
        emit(binary.right(), mv, vars);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DCMPL);
        mv.visitJumpInsn(IFNE, yes);
        mv.visitLabel(no);
        mv.visitInsn(DCONST_0);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(yes);
        mv.visitInsn(DCONST_1);
        mv.visitLabel(end);
    }

    private void emitCall(Expr.Call call, MethodVisitor mv, Map<String, Integer> vars) {
        validateArity(call);
        call.args().forEach(arg -> emit(arg, mv, vars));
        switch (call.name()) {
            case "abs" -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "abs", "(D)D", false);
            case "floor" -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false);
            case "ceil" -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false);
            case "sin" -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "sin", "(D)D", false);
            case "cos" -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "cos", "(D)D", false);
            case "sqrt" -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false);
            case "min" -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(DD)D", false);
            case "max" -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "max", "(DD)D", false);
            case "mod" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "mod", "(DD)D", false);
            case "noise2" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "noise2", "(DDD)D", false);
            case "perlin2" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "perlin2", "(DDD)D", false);
            case "simplex2" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "simplex2", "(DDD)D", false);
            case "fbm2" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "fbm2", "(DDDDDD)D", false);
            case "ridged2" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "ridged2", "(DDDDDD)D", false);
            case "billow2" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "billow2", "(DDDDDD)D", false);
            case "warpX" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "warpX", "(DDDDD)D", false);
            case "warpZ" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "warpZ", "(DDDDD)D", false);
            case "terrace" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "terrace", "(DD)D", false);
            case "smoothstep" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "smoothstep", "(DDD)D", false);
            case "clamp" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "clamp", "(DDD)D", false);
            case "lerp" -> mv.visitMethodInsn(INVOKESTATIC, internal(DslMath.class), "lerp", "(DDD)D", false);
            default -> throw new DslException("unsupported ASM function: " + call.name());
        }
    }

    private void validateArity(Expr.Call call) {
        int expected = switch (call.name()) {
            case "abs", "floor", "ceil", "sin", "cos", "sqrt" -> 1;
            case "min", "max", "mod", "terrace" -> 2;
            case "noise2", "perlin2", "simplex2", "smoothstep", "clamp", "lerp" -> 3;
            case "warpX", "warpZ" -> 5;
            case "fbm2", "ridged2", "billow2" -> 6;
            default -> throw new DslException("unsupported ASM function: " + call.name());
        };
        if (call.args().size() != expected) {
            throw new DslException("function " + call.name() + " expects " + expected + " args but got " + call.args().size());
        }
    }

    private void emitCompareZero(MethodVisitor mv, int jump) {
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DCMPL);
        Label yes = new Label(), end = new Label();
        mv.visitJumpInsn(jump, yes);
        mv.visitInsn(DCONST_0);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(yes);
        mv.visitInsn(DCONST_1);
        mv.visitLabel(end);
    }

    private void emitComparison(MethodVisitor mv, int jump) {
        mv.visitInsn(DCMPG);
        Label yes = new Label(), end = new Label();
        mv.visitJumpInsn(jump, yes);
        mv.visitInsn(DCONST_0);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(yes);
        mv.visitInsn(DCONST_1);
        mv.visitLabel(end);
    }

    private Map<String, Integer> index(List<String> variables) {
        java.util.LinkedHashMap<String, Integer> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < variables.size(); i++) map.put(variables.get(i), i);
        return map;
    }

    private static String internal(Class<?> type) { return type.getName().replace('.', '/'); }

    private static final class BytecodeLoader extends ClassLoader {
        private BytecodeLoader(ClassLoader parent) { super(parent); }
        private Class<?> define(String name, byte[] bytecode) { return defineClass(name, bytecode, 0, bytecode.length); }
    }

    private static final class SafeClassWriter extends ClassWriter {
        private SafeClassWriter(int flags) { super(flags); }
        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                ClassLoader loader = AsmExpressionCompiler.class.getClassLoader();
                Class<?> class1 = Class.forName(type1.replace('/', '.'), false, loader);
                Class<?> class2 = Class.forName(type2.replace('/', '.'), false, loader);
                if (class1.isAssignableFrom(class2)) return type1;
                if (class2.isAssignableFrom(class1)) return type2;
                if (class1.isInterface() || class2.isInterface()) return "java/lang/Object";
                do class1 = class1.getSuperclass(); while (class1 != null && !class1.isAssignableFrom(class2));
                return class1 == null ? "java/lang/Object" : class1.getName().replace('.', '/');
            } catch (Throwable ignored) {
                return "java/lang/Object";
            }
        }
    }

    private record CacheKey(String source, List<String> variables) {}
}
