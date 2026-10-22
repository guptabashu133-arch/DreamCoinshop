package org.mydreamduels.dreamcoinshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.EventExecutor;

/**
 * Chat on plain Spigot: the same format as legacy colour codes. The flag is shown as text
 * ("[IN]"): legacy chat can't carry pictures.
 */
final class LegacyChat implements Listener {

    private final Dreamcoinshop plugin;

    private LegacyChat(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    static LegacyChat register(Dreamcoinshop plugin, EventPriority priority) {
        LegacyChat chat = new LegacyChat(plugin);
        EventExecutor executor = (listener, event) -> {
            if (event instanceof AsyncPlayerChatEvent e) chat.onChat(e);
        };
        plugin.getServer().getPluginManager().registerEvent(AsyncPlayerChatEvent.class, chat, priority, executor, plugin, true);
        return chat;
    }

    void unregister() {
        HandlerList.unregisterAll(this);
    }

    @SuppressWarnings("deprecation")
    private void onChat(AsyncPlayerChatEvent event) {
        ShopConfig cfg = plugin.config();
        if (!cfg.chatEnabled) return;
        Player player = event.getPlayer();
        Profile profile = plugin.data().online(player.getUniqueId());
        if (profile == null) return;
        Cosmetics cos = plugin.cosmetics();
        Text text = plugin.text();

        String tagMini = cfg.chatTags ? cos.tagMini(profile) : null;
        Flags.Country flag = cfg.chatFlags ? cos.flag(profile) : null;
        String flagMini = flag == null ? "" : " " + cos.flagTextMini(flag);
        String escaped = Text.escape(event.getMessage());
        String messageMini = cfg.chatColors ? cos.colorMessage(profile, escaped) : escaped;

        // Everything before <message> becomes the format; the message itself stays %2$s
        String format = cfg.chatFormat;
        int split = format.indexOf("<message>");
        String head = format.substring(0, split);
        String tail = format.substring(split + "<message>".length());
        Ph ph = new Ph().snippet("name", cos.nameMini(player, profile))
                .snippet("tag", tagMini == null ? "" : " " + tagMini)
                .snippet("flag", flagMini);
        String before = text.legacy(head, ph).replace("%", "%%");
        String after = text.legacy(tail, ph).replace("%", "%%");
        event.setMessage(text.legacy(messageMini, null));
        event.setFormat(before + "%2$s" + after);
    }
}
