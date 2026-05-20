package io.rcw.vibemine.ai.tools.spawn;

import io.rcw.vibemine.Vibemine;
import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

@Named("spawn")
public final class SpawnTool implements Tool<SpawnTool.SpawnInput, SpawnTool.SpawnOutput> {
    public record SpawnInput(Location spawnLocation, EntityType entityType) {}
    public record SpawnOutput(boolean succcess) {}


    @Override
    public Class<SpawnInput> inputClass() {
        return SpawnInput.class;
    }

    @Override
    public Class<SpawnOutput> outputClass() {
        return SpawnOutput.class;
    }


    @Override
    public SpawnOutput execute(Player player, SpawnInput spawnInput) {
        Bukkit.getScheduler().runTask(Vibemine.getInstance(), () -> {
            player.getWorld().spawnEntity(spawnInput.spawnLocation, EntityType.valueOf(spawnInput.entityType().name()));
        });
        return new SpawnOutput(true);
    }
}
