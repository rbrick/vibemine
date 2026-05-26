package io.rcw.vibemine.ai.plugin.runtime;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * JavaScript-safe wrapper/binding for Vibe Inventory functionality.
 * <p>Methods on this type are intended to be exposed to GraalJS scripts instead
 * of exposing raw Bukkit/Paper objects directly.</p>
 */
public final class VibeInventory {
    private final Inventory inventory;

    /**
     * JavaScript binding for {@code VibeInventory}.
     */
    public VibeInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * JavaScript binding for {@code getSize}.
     */
    public int getSize() { return this.inventory.getSize(); }
    /**
     * JavaScript binding for {@code getType}.
     */
    public String getType() { return this.inventory.getType().name(); }
    /**
     * JavaScript binding for {@code getTitle}.
     */
    public String getTitle() { return this.inventory.getViewers().isEmpty() ? null : this.inventory.getViewers().getFirst().getOpenInventory().getTitle(); }
    /**
     * JavaScript binding for {@code isEmpty}.
     */
    public boolean isEmpty() { return this.inventory.isEmpty(); }

    /**
     * JavaScript binding for {@code getItem}.
     */
    public VibeItem getItem(int slot) {
        checkSlot(slot);
        return new VibeItem(this.inventory.getItem(slot));
    }

    /**
     * JavaScript binding for {@code setItem}.
     */
    public void setItem(int slot, VibeItem item) {
        checkSlot(slot);
        this.inventory.setItem(slot, item == null ? null : item.unwrap());
    }

    /**
     * JavaScript binding for {@code clear}.
     */
    public void clear() { this.inventory.clear(); }

    /**
     * JavaScript binding for {@code clear}.
     */
    public void clear(int slot) {
        checkSlot(slot);
        this.inventory.clear(slot);
    }

    /**
     * JavaScript binding for {@code firstEmpty}.
     */
    public int firstEmpty() { return this.inventory.firstEmpty(); }

    /**
     * JavaScript binding for {@code contains}.
     */
    public boolean contains(String material) {
        Material matched = matchMaterial(material);
        return this.inventory.contains(matched);
    }

    /**
     * JavaScript binding for {@code containsAtLeast}.
     */
    public boolean containsAtLeast(String material, int amount) {
        Material matched = matchMaterial(material);
        return this.inventory.containsAtLeast(new ItemStack(matched), amount);
    }

    /**
     * JavaScript binding for {@code count}.
     */
    public int count(String material) {
        Material matched = matchMaterial(material);
        int count = 0;
        for (ItemStack item : this.inventory.getContents()) {
            if (item != null && item.getType() == matched) count += item.getAmount();
        }
        return count;
    }

    /**
     * JavaScript binding for {@code addItem}.
     */
    public void addItem(VibeItem item) {
        this.inventory.addItem(item.unwrap());
    }

    /**
     * JavaScript binding for {@code addItem}.
     */
    public void addItem(String material, int amount) {
        this.inventory.addItem(new ItemStack(matchMaterial(material), amount));
    }

    /**
     * JavaScript binding for {@code removeItem}.
     */
    public void removeItem(VibeItem item) {
        this.inventory.removeItem(item.unwrap());
    }

    /**
     * JavaScript binding for {@code remove}.
     */
    public void remove(String material) {
        this.inventory.remove(matchMaterial(material));
    }

    /**
     * JavaScript binding for {@code remove}.
     */
    public void remove(String material, int amount) {
        Material matched = matchMaterial(material);
        int remaining = amount;
        for (int slot = 0; slot < this.inventory.getSize() && remaining > 0; slot++) {
            ItemStack item = this.inventory.getItem(slot);
            if (item == null || item.getType() != matched) continue;

            int taken = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - taken);
            remaining -= taken;

            if (item.getAmount() <= 0) this.inventory.clear(slot);
            else this.inventory.setItem(slot, item);
        }
    }

    /**
     * JavaScript binding for {@code getContents}.
     */
    public List<VibeItem> getContents() {
        return java.util.Arrays.stream(this.inventory.getContents()).map(VibeItem::new).toList();
    }

    /**
     * JavaScript binding for {@code setContents}.
     */
    public void setContents(List<VibeItem> items) {
        ItemStack[] contents = new ItemStack[this.inventory.getSize()];
        for (int i = 0; i < contents.length && i < items.size(); i++) {
            VibeItem item = items.get(i);
            contents[i] = item == null ? null : item.unwrap();
        }
        this.inventory.setContents(contents);
    }

    /**
     * JavaScript binding for {@code unwrap}.
     */
    Inventory unwrap() { return this.inventory; }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= this.inventory.getSize()) {
            throw new IndexOutOfBoundsException("Inventory slot out of range: " + slot);
        }
    }

    private static Material matchMaterial(String material) {
        Material matched = Material.matchMaterial(material);
        if (matched == null) throw new IllegalArgumentException("Unknown material: " + material);
        return matched;
    }
}
