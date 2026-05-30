package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

/**
 * JavaScript-safe captured block state including material, block data, and tile-entity state.
 * Useful for undo/restore operations that must preserve stairs, doors, chests, signs, etc.
 */
public final class VibeBlockSnapshot {
    private final BlockState state;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final BlockData blockData;

    VibeBlockSnapshot(BlockState state) {
        this.state = state;
        this.worldName = state.getWorld().getName();
        this.x = state.getX();
        this.y = state.getY();
        this.z = state.getZ();
        this.blockData = state.getBlockData().clone();
    }

    public String getType() {
        return state.getType().name();
    }

    public String getBlockData() {
        return blockData.getAsString();
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public VibeLocation getLocation() {
        return new VibeLocation(location());
    }

    public VibeWorld getWorld() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : VibeRuntimeWrappers.world(world);
    }

    /** Restore this snapshot at its original location without triggering physics updates. */
    public boolean restore() {
        Block block = originalBlock();
        if (block == null) return false;
        return restoreBlock(block);
    }

    /** Restore this snapshot at its original location and optionally apply physics. */
    public boolean restore(boolean applyPhysics) {
        Block block = originalBlock();
        if (block == null) return false;
        return restoreBlock(block, applyPhysics);
    }

    /** Restore this snapshot at another block's location without triggering physics updates. */
    public boolean restoreTo(VibeBlock block) {
        return restoreTo(block, false);
    }

    /** Restore this snapshot at another block's location and optionally apply physics. */
    public boolean restoreTo(VibeBlock block, boolean applyPhysics) {
        if (block == null) throw new IllegalArgumentException("Block cannot be null");
        return restoreBlock(block.unwrap(), applyPhysics);
    }

    private boolean restoreBlock(Block block) {
        return restoreBlock(block, false);
    }

    private boolean restoreBlock(Block block, boolean applyPhysics) {
        // Use the captured BlockState as the source of truth. BlockState#update(force, physics)
        // is the Bukkit-supported path for restoring tile entities such as chests/signs.
        BlockState placedState = sameLocation(block) ? state : state.copy(block.getLocation());
        placedState.setBlockData(blockData);
        boolean updated = placedState.update(true, applyPhysics);

        // Some BlockState implementations restore tile data but can leave block-data
        // properties normalized. Force the exact captured BlockData afterwards as well.
        // This is especially important for undo batches with stairs, doors, slabs, etc.
        block.setBlockData(blockData, applyPhysics);
        return updated;
    }

    private boolean sameLocation(Block block) {
        return block.getX() == x
                && block.getY() == y
                && block.getZ() == z
                && block.getWorld().getName().equals(worldName);
    }

    private Block originalBlock() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : world.getBlockAt(x, y, z);
    }

    private Location location() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) throw new IllegalStateException("World is not loaded: " + worldName);
        return new Location(world, x, y, z);
    }

    BlockState unwrap() {
        return state;
    }
}
