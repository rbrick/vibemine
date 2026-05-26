package io.rcw.vibemine.ai.tools.api;

import io.rcw.vibemine.ai.plugin.context.CommandExecutionContext;
import io.rcw.vibemine.ai.plugin.context.EventExecutionContext;
import io.rcw.vibemine.ai.plugin.runtime.*;
import io.rcw.vibemine.ai.plugin.runtime.database.VibeDatabase;
import io.rcw.vibemine.ai.plugin.runtime.permissions.VibePermissions;
import io.rcw.vibemine.ai.plugin.runtime.raytrace.VibeRayTraceResult;
import io.rcw.vibemine.ai.plugin.runtime.scheduler.VibeScheduler;
import io.rcw.vibemine.ai.plugin.runtime.scheduler.VibeTask;
import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.entity.Player;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

@Named("api_reference")
public final class ApiReferenceTool implements Tool<ApiReferenceTool.ApiReferenceInput, ApiReferenceTool.ApiReferenceOutput> {
    private static final Map<String, Class<?>> TYPES = buildTypes();

    public record ApiReferenceInput(String type) { }
    public record MethodReference(String signature, String returns) { }
    public record ApiReferenceOutput(String type, List<String> availableTypes, List<MethodReference> methods, String notes, String error) {
        public static ApiReferenceOutput error(String message) {
            return new ApiReferenceOutput(null, List.copyOf(TYPES.keySet()), List.of(), null, message);
        }
    }

    @Override
    public Class<ApiReferenceInput> inputClass() {
        return ApiReferenceInput.class;
    }

    @Override
    public Class<ApiReferenceOutput> outputClass() {
        return ApiReferenceOutput.class;
    }

    @Override
    public ApiReferenceOutput execute(Player player, ApiReferenceInput input) {
        if (input == null || input.type() == null || input.type().isBlank()) {
            return new ApiReferenceOutput(null, List.copyOf(TYPES.keySet()), List.of(), notes(), null);
        }

        String key = normalize(input.type());
        Class<?> type = TYPES.get(key);
        if (type == null) return ApiReferenceOutput.error("Unknown API type: " + input.type());

        List<MethodReference> methods = Arrays.stream(type.getMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getDeclaringClass() != Object.class)
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getParameterCount() == 0 || Arrays.stream(method.getParameterTypes()).noneMatch(ApiReferenceTool::isHiddenType))
                .sorted(Comparator.comparing(Method::getName).thenComparing(Method::getParameterCount))
                .map(this::reference)
                .toList();

        return new ApiReferenceOutput(type.getSimpleName(), List.copyOf(TYPES.keySet()), methods, notesFor(type), null);
    }

    private MethodReference reference(Method method) {
        String params = Arrays.stream(method.getParameters())
                .map(parameter -> simpleType(parameter.getType()) + " " + parameter.getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return new MethodReference(method.getName() + "(" + params + ")", simpleType(method.getReturnType()));
    }

    private static boolean isHiddenType(Class<?> type) {
        return type == Value.class || type.getName().startsWith("org.bukkit") || type.getName().startsWith("org.graalvm");
    }

    private static String simpleType(Class<?> type) {
        if (type == void.class || type == Void.class) return "void";
        if (type == String.class) return "string";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type == int.class || type == Integer.class) return "int";
        if (type == long.class || type == Long.class) return "long";
        if (type == float.class || type == Float.class) return "float";
        if (type == double.class || type == Double.class) return "double";
        if (List.class.isAssignableFrom(type)) return "List";
        return type.getSimpleName();
    }

    private static Map<String, Class<?>> buildTypes() {
        Map<String, Class<?>> types = new TreeMap<>();
        register(types, CommandExecutionContext.class, "command_ctx", "command", "ctx_command");
        register(types, EventExecutionContext.class, "event_ctx", "event", "ctx_event");
        register(types, VibeSender.class, "sender");
        register(types, VibePlayer.class, "player");
        register(types, VibeWorld.class, "world");
        register(types, VibeLocation.class, "location");
        register(types, VibeBlock.class, "block");
        register(types, VibeEntity.class, "entity");
        register(types, VibeItem.class, "item");
        register(types, VibeInventory.class, "inventory");
        register(types, VibeInventories.class, "inventories");
        register(types, VibeServer.class, "server");
        register(types, VibeDatabase.class, "database");
        register(types, VibePermissions.class, "permissions");
        register(types, VibeRayTraceResult.class, "raytrace", "ray_trace_result");
        register(types, VibeScheduler.class, "scheduler");
        register(types, VibeTask.class, "task");
        return Collections.unmodifiableMap(types);
    }

    private static void register(Map<String, Class<?>> types, Class<?> type, String... aliases) {
        types.put(normalize(type.getSimpleName()), type);
        for (String alias : aliases) types.put(normalize(alias), type);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static String notes() {
        return "Ask for a specific type, e.g. {\"type\":\"VibePlayer\"}, {\"type\":\"CommandExecutionContext\"}, or {\"type\":\"database\"}. Global bindings include server, inventories, permissions, scheduler, and database.";
    }

    private static String notesFor(Class<?> type) {
        if (type == CommandExecutionContext.class) return "Command handlers receive this as ctx. Use ctx.getSender().isPlayer() before ctx.getSender().asPlayer().";
        if (type == EventExecutionContext.class) return "Event handlers receive this as ctx. Some getters return null depending on event type.";
        if (type == VibePlayer.class) return "VibePlayer extends VibeSender, so sender methods like sendMessage and hasPermission are also available.";
        if (type == VibeDatabase.class) return "Plugin-scoped persistent key/value storage. setJson/getJson can store structured JSON-like values.";
        return notes();
    }

    @Override
    public String usage() {
        return """
                Reveal the safe VibePlugin JavaScript API exposed to generated plugins.
                Call this before using unfamiliar ctx, player, world, server, database, scheduler, inventory, item, entity, or block APIs.
                Input: {"type":"VibePlayer"} or {"type":"player"}. Omit type to list available API types.
                """;
    }
}
