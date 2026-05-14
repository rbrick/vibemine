package io.rcw.vibemine;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public interface Items {
    public static final NamespacedKey VIBE_BOOK_KEY = new NamespacedKey("vibemine", "vibe_book");
    public static final NamespacedKey PROMPT_BOOK_KEY = new NamespacedKey("vibemine", "prompt_book");

    static void vibeBookData(PersistentDataContainer container) {
        container.set(VIBE_BOOK_KEY, PersistentDataType.BOOLEAN, true);
    }

    static ItemStack vibeBook() {
        var stack = ItemStack.of(Material.WRITABLE_BOOK, 1);
        var meta = stack.getItemMeta();

        // configure the display name
        meta.displayName(Component.text("The Vibe Book", Style.style(NamedTextColor.GREEN, TextDecoration.BOLD, TextDecoration.UNDERLINED)));
        stack.setItemMeta(meta);


        stack.editPersistentDataContainer(Items::vibeBookData);

        return stack;
    }

}
