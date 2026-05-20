package io.rcw.vibemine.serialization;

import com.google.gson.*;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.lang.reflect.Type;

public final class BlockSerializer implements JsonSerializer<Block>, JsonDeserializer<Block> {
    @Override
    public Block deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var object = json.getAsJsonObject();
        var location = (Location) context.deserialize(object.get("location"), Location.class);
        return location.getBlock();
    }

    @Override
    public JsonElement serialize(Block src, Type typeOfSrc, JsonSerializationContext context) {
        var object = new JsonObject();

        object.addProperty("material", src.getType().toString());
        object.add("location", context.serialize(src.getLocation()));


        return object;

    }
}
