package io.rcw.vibemine.serialization;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Type;

public final class LocationSerializer implements JsonSerializer<Location>, JsonDeserializer<Location> {

    @Override
    public Location deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var object = json.getAsJsonObject();
        var x = object.get("x").getAsDouble();
        var y = object.get("y").getAsDouble();
        var z = object.get("z").getAsDouble();
        var world = Bukkit.getWorld(object.get("world").getAsString());

        return new Location(world, x, y, z);
    }

    @Override
    public JsonElement serialize(Location src, Type typeOfSrc, JsonSerializationContext context) {
        var object = new JsonObject();

        object.addProperty("world", src.getWorld().getName());
        object.addProperty("x", src.getX());
        object.addProperty("y", src.getY());
        object.addProperty("z", src.getZ());

        return object;
    }
}
