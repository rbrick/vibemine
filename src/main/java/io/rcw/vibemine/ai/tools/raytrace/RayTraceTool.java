package io.rcw.vibemine.ai.tools.raytrace;

import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Named("raytrace")
public final class RayTraceTool implements Tool<Void, List<RayTraceTool.RayTraceResponse>> {

    public record RayTraceResponse(@Nullable Location location, @Nullable  Block block, @Nullable Entity entity) {
        public static RayTraceResponse ofBlock(@Nullable Block block) {
            return new RayTraceResponse(block.getLocation(), block, null);
        }

        public static RayTraceResponse ofEntity(@Nullable Entity entity) {
            return new RayTraceResponse(entity.getLocation(), null, entity);
        }
    }

    public static final double DISTANCE = 100.0;


    @Override
    public Class<Void> inputClass() {
        return Void.class;
    }

    @Override
    public Class<List<RayTraceResponse>> outputClass() {
        return (Class<List<RayTraceResponse>>) (Class<?>) List.class;
    }

    @Override
    public List<RayTraceResponse> execute(Player player, Void unused) {
        var blocksResult = player.rayTraceBlocks(DISTANCE);
        var entitiesResult = player.rayTraceEntities((int) DISTANCE);
        var result = new ArrayList<RayTraceResponse>();

        if (entitiesResult != null) {
          result.add(RayTraceResponse.ofEntity(entitiesResult.getHitEntity()));
        }

        if (blocksResult != null) {
            result.add(RayTraceResponse.ofBlock(blocksResult.getHitBlock()));
        }

        return result;
    }
}
