package org.mydreamduels.dreamcoinshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Text on Paper: real Adventure components, built with MiniMessage. */
final class AdventureText implements Text {

    private final Dreamcoinshop plugin;
    private final Platform platform;
    private final Logger log;
    private final MiniMessage mm = MiniMessage.miniMessage();
    /** Tags older Paper doesn't know yet, so they're dropped instead of shown as text. */
    private final TagResolver compat;
    private YamlConfiguration messages;

    AdventureText(Dreamcoinshop plugin, Platform platform) {
        this.plugin = plugin;
        this.platform = platform;
        this.log = plugin.getLogger();
        TagResolver.Builder b = TagResolver.builder();
        if (!Capabilities.SHADOWS) b.resolver(TagResolver.resolver("shadow", (args, ctx) -> Tag.styling(s -> { })));
        compat = b.build();
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

    /** MiniMessage to a component. Never throws: broken text is shown without its tags. */
    Component parse(String mini, Ph ph) {
        try {
            return mm.deserialize(mini, TagResolver.resolver(compat, resolver(ph)));
        } catch (Exception ex) {
            log.warning("Could not read the text '" + mini + "': " + ex.getMessage());
            return Component.text(Text.plain(mini));
        }
    }

    /** Like {@link #parse} with extra component placeholders (e.g. a flag icon). */
    Component parse(String mini, Ph ph, Map<String, Component> components) {
        List<TagResolver> list = new ArrayList<>();
        list.add(compat);
        list.add(resolver(ph));
        components.forEach((k, v) -> list.add(Placeholder.component(k, v)));
        try {
            return mm.deserialize(mini, TagResolver.resolver(list));
        } catch (Exception ex) {
            log.warning("Could not read the text '" + mini + "': " + ex.getMessage());
            return Component.text(Text.plain(mini));
        }
    }

    private static TagResolver resolver(Ph ph) {
        if (ph == null) return TagResolver.empty();
        List<TagResolver> list = new ArrayList<>();
        ph.values.forEach((k, v) -> list.add(Placeholder.unparsed(k, v)));
        ph.snippets.forEach((k, v) -> list.add(Placeholder.parsed(k, v)));
        return TagResolver.resolver(list);
    }

    @Override
    public void sendMini(CommandSender to, String mini, Ph ph) {
        Component c = parse(mini, ph);
        if (to instanceof Player p) platform.forPlayer(p, () -> p.sendMessage(c));
        else to.sendMessage(c);
    }

    @Override
    public void sendLink(Player to, String mini, Ph ph, String url) {
        Component c = parse(mini, ph).clickEvent(ClickEvent.openUrl(url));
        platform.forPlayer(to, () -> to.sendMessage(c));
    }

    @Override
    public void itemText(ItemMeta meta, Player viewer, String nameMini, List<String> loreMinis, Ph ph) {
        if (nameMini != null) meta.displayName(noItalic(parse(nameMini, ph)));
        List<Component> lore = new ArrayList<>();
        if (loreMinis != null) {
            for (String block : loreMinis) {
                if (block == null) continue;
                for (String line : block.split("\n", -1)) lore.add(noItalic(parse(line, ph)));
            }
        }
        meta.lore(lore.isEmpty() ? null : lore);
    }

    @Override
    public Inventory inventory(InventoryHolder holder, int size, Player viewer, String titleMini, Ph ph) {
        return Bukkit.createInventory(holder, size, parse(titleMini, ph));
    }

    @Override
    public void setNames(Player player, String nameMini, boolean displayName, boolean tabList) {
        Component name = nameMini == null ? null : parse(nameMini, null);
        platform.forPlayer(player, () -> {
            if (!player.isOnline()) return;
            if (displayName) player.displayName(name);
            if (tabList) player.playerListName(name);
        });
    }

    /** Item names and lore are italic by default; ours shouldn't be. */
    static Component noItalic(Component c) {
        return Component.text().decoration(TextDecoration.ITALIC, false).append(c).build();
    }
}
