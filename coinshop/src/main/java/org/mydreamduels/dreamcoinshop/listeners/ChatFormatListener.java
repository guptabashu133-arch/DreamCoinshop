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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;
import org.mydreamduels.dreamcoinshop.data.PlayerProfile;
import org.mydreamduels.dreamcoinshop.shopdata.ShopOptions;

public class ChatFormatListener implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final Dreamcoinshop plugin;

    public ChatFormatListener(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = this.plugin.getPlayerDataManager().get(player.getUniqueId());
        Component styledMessage = event.message();
        if (profile.getActiveChatColor() != null) {
            ShopOptions.ChatColorOption opt = this.plugin.getShopConfig().getChatColors().get(profile.getActiveChatColor());
            if (opt != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
                if (opt.isGradient()) {
                    String tag = "<gradient:" + opt.gradient().get(0) + ":" + opt.gradient().get(1) + ">" + this.escape(plain) + "</gradient>";
                    styledMessage = MM.deserialize(tag);
                } else {
                    NamedTextColor color = opt.colorHex() != null ? (NamedTextColor) TextColor.fromHexString(opt.colorHex()) : NamedTextColor.WHITE;
                    styledMessage = Component.text(plain, (TextColor) color);
                }
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
        // FIXED: was event.getPlayer().displayName() - that field is exactly
        // what EssentialsX (or any /nick plugin) overwrites with the player's
        // nickname, and colours red by default for ops (ops-name-color).
        // getGradientNameComponent() rebuilds the name straight from
        // player.getName() (the real Mojang username, which nothing else can
        // silently repaint) plus this player's own purchased gradient, so
        // chat always shows the real name with the correct gradient
        // regardless of what any other plugin does to displayName().
        Component nameLine = MM.deserialize(format, new TagResolver[]{Placeholder.component("name", (ComponentLike) this.plugin.getGradientNameComponent(player)), Placeholder.component("tag", (ComponentLike) tag)});
        Component finalMessage = styledMessage;
        event.renderer((source, sourceDisplayName, message, viewer) -> ((TextComponent.Builder) ((TextComponent.Builder) ((TextComponent.Builder) Component.text().append(nameLine)).append(Component.text(": ", (TextColor) NamedTextColor.GRAY))).append(finalMessage)).build());
    }

    private String escape(String plain) {
        return MM.escapeTags(plain);
    }
}
