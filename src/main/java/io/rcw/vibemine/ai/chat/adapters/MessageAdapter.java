package io.rcw.vibemine.ai.chat.adapters;

import com.google.gson.*;
import io.rcw.vibemine.ai.chat.Conversation;
import io.rcw.vibemine.ai.chat.Sender;

import java.lang.reflect.Type;


public final class MessageAdapter implements JsonSerializer<Conversation.Message>, JsonDeserializer<Conversation.Message> {

    @Override
    public JsonElement serialize(Conversation.Message src, Type typeOfSrc, JsonSerializationContext context) {
        var object = new JsonObject();

        object.addProperty("sender", src.sender().name());
        object.addProperty("message", src.message());
        object.addProperty("timestamp", src.timestamp());

        return object;
    }

    @Override
    public Conversation.Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var object = json.getAsJsonObject();

        var sender =  object.get("sender").getAsString();
        var message = object.get("message").getAsString();
        var timestamp = object.get("timestamp").getAsLong();

        return new Conversation.Message(Sender.valueOf(sender), message, timestamp);
    }
}
