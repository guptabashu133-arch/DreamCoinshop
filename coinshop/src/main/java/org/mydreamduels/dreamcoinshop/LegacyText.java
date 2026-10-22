package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Text on plain Spigot: the same MiniMessage, rendered as legacy colour codes. */
final class LegacyText implements Text {

    private final Dreamcoinshop plugin;
    private final Platform platform;
    private YamlConfiguration messages;

    LegacyText(Dreamcoinshop plugin, Platform platform) {
        this.plugin = plugin;
        this.platform = platform;
        reload();
    }

    @Override
    public void reload() {
        messages = MessageFile.load(plugin, "messages.yml");
    }

    @Override
    public String raw(String path) {
        String value = messages.getString(path);
        return value != null ? value : "<red>Missing message: " + path;
    }

    @Override
    public boolean has(String path) {
        return messages.getString(path) != null;
    }

    @Override
    public void sendMini(CommandSender to, String mini, Ph ph) {
        String text = legacy(mini, ph);
        if (to instanceof Player p) platform.forPlayer(p, () -> p.sendMessage(text));
        else to.sendMessage(text);
    }

    @Override
    public void sendLink(Player to, String mini, Ph ph, String url) {
        String text = legacy(mini, ph);
        platform.forPlayer(to, () -> {
            if (!sendClickable(to, text, url)) to.sendMessage(text + " " + url);
        });
    }

    @Override
    public void itemText(ItemMeta meta, Player viewer, String nameMini, List<String> loreMinis, Ph ph) {
        if (nameMini != null) meta.setDisplayName(legacy(nameMini, ph));
        List<String> lore = new ArrayList<>();
        if (loreMinis != null) {
            for (String block : loreMinis) {
                if (block == null) continue;
                for (String line : block.split("\n", -1)) lore.add(legacy(line, ph));
            }
        }
        meta.setLore(lore.isEmpty() ? null : lore);
    }

    @Override
    public Inventory inventory(InventoryHolder holder, int size, Player viewer, String titleMini, Ph ph) {
        return Bukkit.createInventory(holder, size, legacy(titleMini, ph));
    }

    @Override
    public void setNames(Player player, String nameMini, boolean displayName, boolean tabList) {
        String name = nameMini == null ? null : legacy(nameMini, null);
        platform.forPlayer(player, () -> {
            if (!player.isOnline()) return;
            if (displayName) player.setDisplayName(name);
            if (tabList) player.setPlayerListName(name);
        });
    }

    // ------------------------------------------------------------------ clickable link on Spigot

    private static Method spigotMethod;
    private static Method sendMethod;
    private static Method fromLegacy;
    private static Method setClick;
    private static java.lang.reflect.Constructor<?> clickConstructor;
    private static Object openUrl;
    private static boolean hooked;

    /** player.spigot().sendMessage(TextComponent.fromLegacyText(text)) with an open-url click, via reflection. */
    private static synchronized boolean sendClickable(Player player, String text, String url) {
        if (!hooked) {
            hooked = true;
            try {
                Class<?> base = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
                Class<?> textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent");
                Class<?> click = Class.forName("net.md_5.bungee.api.chat.ClickEvent");
                Class<?> action = Class.forName("net.md_5.bungee.api.chat.ClickEvent$Action");
                openUrl = action.getField("OPEN_URL").get(null);
                clickConstructor = click.getConstructor(action, String.class);
                setClick = base.getMethod("setClickEvent", click);
                fromLegacy = textComponent.getMethod("fromLegacyText", String.class);
                spigotMethod = Player.class.getMethod("spigot");
                Class<?> playerSpigot = Class.forName("org.bukkit.entity.Player$Spigot");
                sendMethod = playerSpigot.getMethod("sendMessage", Array.newInstance(base, 0).getClass());
            } catch (Throwable t) {
                sendMethod = null;
            }
        }
        if (sendMethod == null) return false;
        try {
            Object components = fromLegacy.invoke(null, text);
            Object event = clickConstructor.newInstance(openUrl, url);
            for (int i = 0; i < Array.getLength(components); i++) setClick.invoke(Array.get(components, i), event);
            sendMethod.invoke(spigotMethod.invoke(player), components);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
