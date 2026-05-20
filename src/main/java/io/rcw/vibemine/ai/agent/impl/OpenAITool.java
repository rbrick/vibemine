package io.rcw.vibemine.ai.agent.impl;

import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionTool;
import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenAITool {
    private final String name;
    private final Tool<?, ?> tool;
    private final ChatCompletionTool openAITool;

    public OpenAITool(Tool<?, ?> tool) {
        this.tool = tool;
        this.name = getName(tool);
        this.openAITool = ChatCompletionTool.ofFunction(
                ChatCompletionFunctionTool.builder()
                        .function(FunctionDefinition.builder()
                                .name(this.name)
                                .description("""
                                        Minecraft server tool: %s
                                        %s
                                        """.formatted(this.name, (
                                                !tool.usage().isBlank() ? tool.usage() : ""
                                        )))
                                .parameters(parametersFor(tool.inputClass()))
                                .build())
                        .build());
    }

    public String name() {
        return name;
    }

    public Tool<?, ?> tool() {
        return tool;
    }

    public ChatCompletionTool openAITool() {
        return openAITool;
    }

    private static String getName(Tool<?, ?> tool) {
        var named = tool.getClass().getAnnotation(Named.class);
        return named == null ? tool.getClass().getSimpleName() : named.value();
    }

    private static FunctionParameters parametersFor(Class<?> inputClass) {
        var properties = new LinkedHashMap<String, Object>();
        var required = new java.util.ArrayList<String>();

        if (inputClass != Void.class && inputClass.isRecord()) {
            for (RecordComponent component : inputClass.getRecordComponents()) {
                properties.put(component.getName(), Map.of("type", jsonType(component.getType())));
                required.add(component.getName());
            }
        }

        return FunctionParameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(properties))
                .putAdditionalProperty("required", JsonValue.from(required))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();
    }

    private static String jsonType(Class<?> type) {
        if (type == String.class || type.isEnum()) return "string";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type == int.class || type == long.class || type == Integer.class || type == Long.class) return "integer";
        if (Number.class.isAssignableFrom(type) || type == float.class || type == double.class) return "number";
        return "object";
    }
}
