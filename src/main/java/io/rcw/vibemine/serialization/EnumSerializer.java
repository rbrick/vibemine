package io.rcw.vibemine.serialization;

import com.google.gson.*;

import java.lang.reflect.Type;

public final class EnumSerializer<T extends Enum<T>> implements JsonSerializer<T>, JsonDeserializer<T> {
    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return Enum.valueOf((Class<T>) typeOfT, json.getAsString());
    }

    @Override
    public JsonElement serialize(T src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.name());
    }
}
