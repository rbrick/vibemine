package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * JavaScript-safe wrapper/binding for Vibe Inventories functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public final class VibeInventories {
    public VibeInventories() {}

    /**
     * JavaScript binding for {@code chest}.
     */
    public VibeInventory chest(int rows, String title) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest rows must be between 1 and 6");
        return VibeRuntimeCache.inventory(Bukkit.createInventory(null, rows * 9, VibeText.component(title)));
    }

    /**
     * JavaScript binding for {@code typed}.
     */
    public VibeInventory typed(String type, String title) {
        InventoryType inventoryType = InventoryType.valueOf(type.toUpperCase());
        return VibeRuntimeCache.inventory(Bukkit.createInventory(null, inventoryType, VibeText.component(title)));
    }

    /**
     * JavaScript binding for {@code item}.
     */
    public VibeItem item(String material, int amount) {
        Material matched = Material.matchMaterial(material);
        if (matched == null) throw new IllegalArgumentException("Unknown material: " + material);
        return new VibeItem(new ItemStack(matched, Math.max(1, amount)));
    }

    /**
     * JavaScript binding for {@code namedItem}.
     */
    public VibeItem namedItem(String material, int amount, String name) {
        VibeItem item = item(material, amount);
        item.setName(name);
        return item;
    }

    /**
     * JavaScript binding for {@code taggedItem}.
     */
    public VibeItem taggedItem(String material, int amount, String name, String key, String value) {
        VibeItem item = namedItem(material, amount, name);
        item.setData(key, value);
        return item;
    }
}
