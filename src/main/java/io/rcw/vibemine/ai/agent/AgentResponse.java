package io.rcw.vibemine.ai.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.rcw.vibemine.Vibemine;

public record AgentResponse(ResponseType kind, String rawMessage) {
    public static AgentResponse parse(String rawMessage) {
        try {
            JsonObject object = Vibemine.GSON.fromJson(rawMessage, JsonObject.class);
            if (object == null || !object.has("type")) {
                return new AgentResponse(ResponseType.CHAT, rawMessage);
            }

            ResponseType type = ResponseType.valueOf(object.get("type").getAsString());
            return new AgentResponse(type, rawMessage);
        } catch (Exception ignored) {
            return new AgentResponse(ResponseType.CHAT, rawMessage);
        }
    }

    public JsonElement responseJson() {
        JsonObject object = Vibemine.GSON.fromJson(rawMessage, JsonObject.class);
        if (object == null || !object.has("response")) {
            return null;
        }
        return object.get("response");
    }

    public String responseText() {
        JsonElement response = responseJson();
        if (response == null) return rawMessage;
        return response.isJsonPrimitive() ? response.getAsString() : Vibemine.GSON.toJson(response);
    }
}
