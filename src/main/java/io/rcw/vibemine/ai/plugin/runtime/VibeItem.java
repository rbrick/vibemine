package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * JavaScript-safe wrapper/binding for Vibe Item functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public final class VibeItem {
    private final ItemStack item;

    /**
     * JavaScript binding for {@code VibeItem}.
     */
    public VibeItem(ItemStack item) {
        this.item = item == null ? ItemStack.empty() : item.clone();
    }

    /**
     * JavaScript binding for {@code getType}.
     */
    public String getType() { return this.item.getType().name(); }
    /**
     * JavaScript binding for {@code setType}.
     */
    public void setType(String material) {
        Material matched = Material.matchMaterial(material);
        if (matched == null) throw new IllegalArgumentException("Unknown material: " + material);
        this.item.setType(matched);
    }

    /**
     * JavaScript binding for {@code getAmount}.
     */
    public int getAmount() { return this.item.getAmount(); }
    /**
     * JavaScript binding for {@code setAmount}.
     */
    public void setAmount(int amount) { this.item.setAmount(Math.max(0, Math.min(amount, this.item.getMaxStackSize()))); }
    /**
     * JavaScript binding for {@code getMaxStackSize}.
     */
    public int getMaxStackSize() { return this.item.getMaxStackSize(); }
    /**
     * JavaScript binding for {@code isEmpty}.
     */
    public boolean isEmpty() { return this.item.isEmpty() || this.item.getType().isAir(); }

    /**
     * JavaScript binding for {@code getName}.
     */
    public String getName() {
        ItemMeta meta = this.item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return null;
        return meta.displayName().toString();
    }

    /**
     * JavaScript binding for {@code setName}.
     */
    public void setName(String name) {
        ItemMeta meta = this.item.getItemMeta();
        if (meta == null) return;
        meta.displayName(VibeText.component(name));
        this.item.setItemMeta(meta);
    }

    /**
     * JavaScript binding for {@code clone}.
     */
    public VibeItem clone() { return new VibeItem(this.item); }
    /**
     * JavaScript binding for {@code unwrap}.
     */
    public ItemStack unwrap() { return this.item.clone(); }
}
