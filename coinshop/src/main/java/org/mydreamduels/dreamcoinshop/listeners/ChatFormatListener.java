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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;
import org.mydreamduels.dreamcoinshop.data.PlayerProfile;
import org.mydreamduels.dreamcoinshop.shopdata.ShopOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        ShopOptions.ChatColorOption colorOption = null;
        if (profile.getActiveChatColor() != null) {
            colorOption = this.plugin.getShopConfig().getChatColors().get(profile.getActiveChatColor());
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
        final ShopOptions.ChatColorOption finalColor = colorOption;
        // FIXED: the chat colour used to be baked into a message captured
        // HERE, and the renderer ignored its "message" argument. Dreamtab's
        // chat emotes (:in: flag, :ender_pearl: icon, ...) run AFTER this at
        // MONITOR and hand the icon-filled message into this renderer - which
        // then threw it away and printed the original ":in:" text. So the
        // colour is now applied to whatever message the renderer is given.
        // At most two variants per message (Java / Bedrock emote forms), so
        // cache them instead of re-running MiniMessage once per viewer.
        Map<Component, Component> styled = new ConcurrentHashMap<>(2);
        event.renderer((source, sourceDisplayName, message, viewer) -> Component.text()
                .append(nameLine)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(finalColor == null ? message : styled.computeIfAbsent(message, m -> this.applyChatColor(m, finalColor)))
                .build());
    }

    /**
     * Paints the player's purchased chat colour / gradient over the text of
     * {@code message} while leaving inline icons (flag heads, item sprites,
     * Bedrock flag glyphs) exactly as another plugin inserted them -
     * flattening those to plain text is what turned an emote back into
     * nothing. An icon is any non-text component, or text carrying its own
     * font or hover (the Bedrock glyph / text fallbacks). A message with no
     * icons comes out the same as before: one plain-text run, coloured.
     */
    private Component applyChatColor(Component message, ShopOptions.ChatColorOption opt) {
        List<Component> pieces = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        this.collect(message, run, pieces, opt);
        this.flush(run, pieces, opt);
        return pieces.size() == 1 ? pieces.get(0) : Component.empty().children(pieces);
    }

    private void collect(Component component, StringBuilder run, List<Component> pieces, ShopOptions.ChatColorOption opt) {
        if (!(component instanceof TextComponent) || component.font() != null || component.hoverEvent() != null) {
            this.flush(run, pieces, opt);
            pieces.add(component);
            return;
        }
        run.append(((TextComponent) component).content());
        for (Component child : component.children()) {
            this.collect(child, run, pieces, opt);
        }
    }

    private void flush(StringBuilder run, List<Component> pieces, ShopOptions.ChatColorOption opt) {
        if (run.isEmpty()) {
            return;
        }
        String plain = run.toString();
        run.setLength(0);
        if (opt.isGradient()) {
            pieces.add(MM.deserialize("<gradient:" + opt.gradient().get(0) + ":" + opt.gradient().get(1) + ">" + this.escape(plain) + "</gradient>"));
            return;
        }
        // FIXED: was cast to NamedTextColor, which throws ClassCastException
        // for any hex that is not one of the 16 named colours (e.g. #FF5733).
        TextColor color = opt.colorHex() != null ? TextColor.fromHexString(opt.colorHex()) : null;
        pieces.add(Component.text(plain, color != null ? color : NamedTextColor.WHITE));
    }

    private String escape(String plain) {
        return MM.escapeTags(plain);
    }
}
