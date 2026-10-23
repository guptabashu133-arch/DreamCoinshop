package org.mydreamduels.dreamcoinshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * "Type the new value in chat": the chest menus have no text boxes, so the item editor asks in
 * chat. The next message (within a minute) is the answer and is not shown to anyone; "cancel"
 * stops. The answer is handled on the player's thread.
 */
@SuppressWarnings("deprecation") // AsyncPlayerChatEvent: still fired by Paper, and the only one on Spigot
final class ChatPrompts implements Listener {

    private record Prompt(Consumer<String> answer, Runnable cancelled, long until) {
    }

    private final Dreamcoinshop plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    ChatPrompts(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    /** Asks; the menu is closed so the player can type. cancelled runs on "cancel" (not on timeout). */
    void ask(Player player, String questionMini, Consumer<String> answer, Runnable cancelled) {
        prompts.put(player.getUniqueId(), new Prompt(answer, cancelled, System.currentTimeMillis() + 60_000));
        plugin.menus().close(player);
        plugin.platform().forPlayer(player, player::closeInventory);
        plugin.text().sendMini(player, questionMini, null);
        plugin.text().send(player, "editor.prompt-hint", null);
    }

    boolean waiting(Player player) {
        Prompt p = prompts.get(player.getUniqueId());
        return p != null && p.until() > System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Prompt p = prompts.remove(player.getUniqueId());
        if (p == null || p.until() < System.currentTimeMillis()) return;
        event.setCancelled(true);
        String message = event.getMessage().trim();
        plugin.platform().forPlayer(player, () -> {
            if (!player.isOnline()) return;
            if (message.equalsIgnoreCase("cancel")) {
                plugin.text().send(player, "editor.cancelled", null);
                if (p.cancelled() != null) p.cancelled().run();
            } else {
                p.answer().accept(message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }
}
