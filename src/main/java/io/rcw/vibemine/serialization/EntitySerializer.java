package io.rcw.vibemine.serialization;

import com.google.gson.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Type;
import java.util.UUID;

public final class EntitySerializer implements JsonSerializer<Entity>, JsonDeserializer<Entity> {
    @Override
    public JsonElement serialize(Entity src, Type typeOfSrc, JsonSerializationContext context) {
        var object =  new JsonObject();

        object.addProperty("id", src.getEntityId());
        object.addProperty("type", src.getType().toString());
        object.addProperty("uniqueId", src.getUniqueId().toString());
        object.add("location", context.serialize(src.getLocation()));

        return object;
    }

    @Override
    public Entity deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var object =  json.getAsJsonObject();

        var location = (Location) context.deserialize(object.get("location"), Location.class);
        var uniqueId = (UUID) context.deserialize(object.get("uniqueId"), UUID.class);

        return location.getWorld().getEntity(uniqueId);
    }

}
