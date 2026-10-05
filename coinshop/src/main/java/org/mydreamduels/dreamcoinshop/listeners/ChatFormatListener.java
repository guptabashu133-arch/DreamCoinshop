package org.mydreamduels.dreamcoinshop.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;
import org.mydreamduels.dreamcoinshop.data.PlayerProfile;
import org.mydreamduels.dreamcoinshop.shopdata.ShopOptions;

public class ChatFormatListener implements Listener {
    private static final Pattern EMOTE = Pattern.compile(":[A-Za-z0-9_+\\-]{1,32}:");
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final Dreamcoinshop plugin;

    public ChatFormatListener(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    /**
     * HIGH, not MONITOR: this sets the base chat line, and Dreamtab (rank prefix + :emote: icons)
     * and Dreamcore wrap it afterwards. The renderer below uses the message it is HANDED, so
     * their replacements survive; it used to rebuild the line from its own copy of the message,
     * which silently threw away every chat icon and Dreamtab's prefix.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = this.plugin.getPlayerDataManager().get(player.getUniqueId());
        if (profile.getActiveChatColor() != null) {
            ShopOptions.ChatColorOption opt = this.plugin.getShopConfig().getChatColors().get(profile.getActiveChatColor());
            if (opt != null) {
                event.message(this.colour(PlainTextComponentSerializer.plainText().serialize(event.message()), opt));
            }
        }
        Component tag = Component.empty();
        if (profile.getActiveTag() != null) {
            ShopOptions.TagOption tagOpt = this.plugin.getShopConfig().getTags().get(profile.getActiveTag());
            if (tagOpt != null) {
                tag = MM.deserialize(tagOpt.miniMessage());
            }
        }
        String format = this.plugin.getShopConfig().getChatNameFormat();
        // getGradientNameComponent() rebuilds the name from player.getName() (the real username,
        // which /nick plugins can't repaint) plus this player's own purchased gradient.
        Component nameLine = MM.deserialize(format, new TagResolver[]{Placeholder.component("name", (ComponentLike) this.plugin.getGradientNameComponent(player)), Placeholder.component("tag", (ComponentLike) tag)});
        event.renderer((source, sourceDisplayName, message, viewer) -> Component.text()
                .append(nameLine)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(message)
                .build());
    }

    /**
     * Colours the message, but keeps every ":token:" as its own plain piece. A gradient splits
     * text into one component per letter, and Dreamtab's :emote: replacement can't match across
     * pieces - so without this, ":ender_pearl:" never became an icon for gradient chat colours.
     */
    private Component colour(String plain, ShopOptions.ChatColorOption opt) {
        TextComponent.Builder out = Component.text();
        Matcher m = EMOTE.matcher(plain);
        int last = 0;
        while (m.find()) {
            out.append(this.colourPart(plain.substring(last, m.start()), opt));
            out.append(Component.text(m.group(), NamedTextColor.WHITE));
            last = m.end();
        }
        out.append(this.colourPart(plain.substring(last), opt));
        return out.build();
    }

    private Component colourPart(String text, ShopOptions.ChatColorOption opt) {
        if (text.isEmpty()) {
            return Component.empty();
        }
        if (opt.isGradient()) {
            return MM.deserialize("<gradient:" + opt.gradient().get(0) + ":" + opt.gradient().get(1) + ">" + this.escape(text) + "</gradient>");
        }
        TextColor color = opt.colorHex() != null ? TextColor.fromHexString(opt.colorHex()) : null;
        return Component.text(text, color != null ? color : NamedTextColor.WHITE);
    }

    private String escape(String plain) {
        return MM.escapeTags(plain);
    }
}
