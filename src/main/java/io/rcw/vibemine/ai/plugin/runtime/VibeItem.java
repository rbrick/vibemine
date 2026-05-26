package io.rcw.vibemine.ai.plugin.runtime;

import io.rcw.vibemine.Vibemine;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
        return getLegacyName();
    }

    /**
     * JavaScript binding for {@code getLegacyName}.
     */
    public String getLegacyName() {
        ItemMeta meta = this.item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || meta.displayName() == null) return null;
        return LegacyComponentSerializer.legacySection().serialize(meta.displayName());
    }

    /**
     * JavaScript binding for {@code getPlainName}.
     */
    public String getPlainName() {
        ItemMeta meta = this.item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || meta.displayName() == null) return null;
        return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
    }

    /**
     * JavaScript binding for {@code hasName}.
     */
    public boolean hasName(String name) {
        String legacyName = getLegacyName();
        String plainName = getPlainName();
        return name != null && (name.equals(legacyName) || name.equals(plainName));
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
     * JavaScript binding for {@code setData}.
     */
    public void setData(String key, String value) {
        ItemMeta meta = this.item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(dataKey(key), PersistentDataType.STRING, value);
        this.item.setItemMeta(meta);
    }

    /**
     * JavaScript binding for {@code getData}.
     */
    public String getData(String key) {
        ItemMeta meta = this.item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(dataKey(key), PersistentDataType.STRING);
    }

    /**
     * JavaScript binding for {@code hasData}.
     */
    public boolean hasData(String key) {
        ItemMeta meta = this.item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(dataKey(key), PersistentDataType.STRING);
    }

    /**
     * JavaScript binding for {@code dataEquals}.
     */
    public boolean dataEquals(String key, String value) {
        String existing = getData(key);
        return existing != null && existing.equals(value);
    }

    /**
     * JavaScript binding for {@code removeData}.
     */
    public void removeData(String key) {
        ItemMeta meta = this.item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(dataKey(key));
        this.item.setItemMeta(meta);
    }

    /**
     * JavaScript binding for {@code clone}.
     */
    public VibeItem clone() { return new VibeItem(this.item); }

    private NamespacedKey dataKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Missing item data key");
        String normalized = key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._/-]+", "_");
        return new NamespacedKey(Vibemine.getInstance(), normalized);
    }
    /**
     * JavaScript binding for {@code unwrap}.
     */
    ItemStack unwrap() { return this.item.clone(); }
}
