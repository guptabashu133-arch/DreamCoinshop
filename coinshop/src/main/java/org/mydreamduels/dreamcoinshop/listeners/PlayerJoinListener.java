package org.mydreamduels.dreamcoinshop.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;

public class PlayerJoinListener implements Listener {
    private final Dreamcoinshop plugin;

    public PlayerJoinListener(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        this.plugin.applyPerkPermissions(player);
        this.plugin.refreshCosmetics(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.clearPerkAttachment(event.getPlayer());
    }
}
