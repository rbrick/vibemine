package io.rcw.vibemine.ai.tools.raytrace;

import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

@Named("raytrace")
public final class RayTraceTool implements Tool<Void, RayTraceTool.RayTraceResponse> {

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
    public Class<RayTraceResponse> outputClass() {
        return RayTraceResponse.class;
    }

    @Override
    public RayTraceResponse execute(Player player, Void unused) {
        var blocksResult = player.rayTraceBlocks(DISTANCE);
        var entitiesResult = player.rayTraceEntities((int) DISTANCE);

        if (blocksResult != null) {
            return RayTraceResponse.ofBlock(blocksResult.getHitBlock());
        }

        if (entitiesResult != null) {
            return  RayTraceResponse.ofEntity(entitiesResult.getHitEntity());
        }

        return null;
    }
}
