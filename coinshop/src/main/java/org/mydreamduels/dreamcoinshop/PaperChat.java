package org.mydreamduels.dreamcoinshop;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.util.HashMap;
import java.util.Map;

/**
 * Chat on Paper: name gradient, tag, flag and chat colour, built per viewer. Java 1.21.9+
 * viewers see the flag as a picture; older clients, Bedrock and the console see "[IN]".
 * Registered at chat.priority. Only loaded on Paper.
 */
final class PaperChat implements Listener {

    private final Dreamcoinshop plugin;

    private PaperChat(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    static PaperChat register(Dreamcoinshop plugin, EventPriority priority) {
        PaperChat chat = new PaperChat(plugin);
        EventExecutor executor = (listener, event) -> {
            if (event instanceof AsyncChatEvent e) chat.onChat(e);
        };
        plugin.getServer().getPluginManager().registerEvent(AsyncChatEvent.class, chat, priority, executor, plugin, true);
        return chat;
    }

    void unregister() {
        HandlerList.unregisterAll(this);
    }

    private void onChat(AsyncChatEvent event) {
        ShopConfig cfg = plugin.config();
        if (!cfg.chatEnabled) return;
        Player player = event.getPlayer();
        Profile profile = plugin.data().online(player.getUniqueId());
        if (profile == null) return;
        Cosmetics cos = plugin.cosmetics();
        AdventureText text = (AdventureText) plugin.text();

        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        String messageMini = cfg.chatColors ? cos.colorMessage(profile, Text.escape(plain)) : null;
        Component message = messageMini == null || messageMini.equals(Text.escape(plain)) ? event.message() : text.parse(messageMini, null);
        Component name = text.parse(cos.nameMini(player, profile), null);
        String tagMini = cfg.chatTags ? cos.tagMini(profile) : null;
        Component tag = tagMini == null ? Component.empty() : Component.space().append(text.parse(tagMini, null));
        Flags.Country flag = cfg.chatFlags ? cos.flag(profile) : null;

        // Built once per kind of viewer, not once per player
        Map<Integer, Component> lines = new HashMap<>();
        event.renderer((source, sourceName, msg, viewer) -> lines.computeIfAbsent(kind(viewer), k -> {
            Component flagPart = flag == null ? Component.empty() : flagFor(k, flag, text, cos);
            Map<String, Component> parts = Map.of("name", name, "tag", tag, "flag", flagPart, "message", message);
            return text.parse(cfg.chatFormat, null, parts);
        }));
    }

    /** 0 = sees flag pictures, 1 = text, 2 = Bedrock. */
    private int kind(Audience viewer) {
        if (viewer instanceof Player p) {
            if (plugin.bedrock().isBedrock(p)) return 2;
            return Capabilities.OBJECTS && ClientInfo.supportsSprites(p) ? 0 : 1;
        }
        return 1;
    }

    private Component flagFor(int kind, Flags.Country flag, AdventureText text, Cosmetics cos) {
        ShopConfig cfg = plugin.config();
        Component inner;
        if (kind == 0) {
            inner = ModernText.head("flag_" + flag.code(), flag.texture());
        } else if (kind == 2 && cfg.flagsBedrock == ShopConfig.BedrockFlags.HIDE) {
            return Component.empty();
        } else if (kind == 2 && cfg.flagsBedrock == ShopConfig.BedrockFlags.GLYPH && flag.bedrockGlyph() > 0) {
            inner = Component.text(new String(Character.toChars(flag.bedrockGlyph())), NamedTextColor.WHITE);
        } else {
            inner = text.parse(cos.flagTextMini(flag), null);
        }
        return Component.space().append(inner);
    }
}
