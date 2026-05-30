package io.rcw.vibemine.ai.chat.adapters;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.Conversation.Message;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ConversationAdapter implements JsonSerializer<Conversation>, JsonDeserializer<Conversation> {

    @Override
    public Conversation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var object = json.getAsJsonObject();

        var playerId = object.get("playerId").getAsString();
        var sessionId = object.getAsJsonPrimitive("session").getAsString();
        //noinspection unchecked
        var messages = (List<Message>) context.deserialize(object.get("messages"), new TypeToken<ArrayList<Message>>() {
        }.getType());

        int estimatedOutputTokens = object.has("estimatedOutputTokens") ? object.get("estimatedOutputTokens").getAsInt() : 0;

        return new Conversation(UUID.fromString(playerId), UUID.fromString(sessionId), messages, estimatedOutputTokens);
    }

    @Override
    public JsonElement serialize(Conversation src, Type typeOfSrc, JsonSerializationContext context) {
        var object = new JsonObject();

        object.addProperty("session", src.getSessionId().toString());
        object.addProperty("playerId", src.getPlayerId().toString());
        object.addProperty("estimatedOutputTokens", src.getEstimatedOutputTokens());
        object.add("messages", context.serialize(src.getMessages()));

        return object;
    }
}
