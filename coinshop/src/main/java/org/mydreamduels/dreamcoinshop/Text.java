package org.mydreamduels.dreamcoinshop;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Everything the plugin shows as text, written once as MiniMessage. Paper renders it with
 * Adventure (hex colours, gradients, shadows); plain Spigot gets the same text as legacy colour
 * codes. Messages always go out on the receiving player's own thread (Folia).
 */
interface Text {

    void reload();

    /** The MiniMessage text of a messages.yml entry. */
    String raw(String path);

    boolean has(String path);

    /** A messages.yml entry. Entries left empty ("") are not sent at all. */
    default void send(CommandSender to, String path, Ph ph) {
        String raw = raw(path);
        if (!raw.isEmpty()) sendMini(to, raw, ph);
    }

    void sendMini(CommandSender to, String mini, Ph ph);

    /** A chat line that opens a link when clicked. */
    void sendLink(Player to, String mini, Ph ph, String url);

    /** Name and lore of a menu item. Lore lines may contain "\n" for more lines. */
    void itemText(ItemMeta meta, Player viewer, String nameMini, List<String> loreMinis, Ph ph);

    Inventory inventory(InventoryHolder holder, int size, Player viewer, String titleMini, Ph ph);

    /** Name in chat (display name) and/or the tab list. Null resets it to the plain name. */
    void setNames(Player player, String nameMini, boolean displayName, boolean tabList);

    /** MiniMessage as legacy "§" text, for Spigot chat and PlaceholderAPI. */
    default String legacy(String mini, Ph ph) {
        return MiniLegacy.INSTANCE.render(mini, ph);
    }

    /** MiniMessage with every tag removed. */
    static String plain(String mini) {
        return MiniLegacy.INSTANCE.render(mini, null).replaceAll("§[0-9a-fk-orx]", "");
    }

    /** Puts text (a player name, a chat message) into MiniMessage without its tags being parsed. */
    static String escape(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }
}
