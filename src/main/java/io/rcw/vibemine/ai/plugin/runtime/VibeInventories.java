package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;

/**
 * JavaScript-safe wrapper/binding for Vibe Inventories functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public final class VibeInventories {
    private VibeInventories() {}

    /**
     * JavaScript binding for {@code chest}.
     */
    public static VibeInventory chest(int rows, String title) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest rows must be between 1 and 6");
        return VibeRuntimeCache.inventory(Bukkit.createInventory(null, rows * 9, VibeText.component(title)));
    }

    /**
     * JavaScript binding for {@code typed}.
     */
    public static VibeInventory typed(String type, String title) {
        InventoryType inventoryType = InventoryType.valueOf(type.toUpperCase());
        return VibeRuntimeCache.inventory(Bukkit.createInventory(null, inventoryType, VibeText.component(title)));
    }
}
