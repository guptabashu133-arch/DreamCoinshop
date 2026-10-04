package org.mydreamduels.dreamcoinshop.listeners;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;
import org.mydreamduels.dreamcoinshop.config.MessagesConfig;
import org.mydreamduels.dreamcoinshop.orbzone.OrbZoneManager;

/** Orb Wand clicks (left = pos1, right = pos2) and orb-zone cleanup on quit. */
public class OrbWandListener implements Listener {
    private final Dreamcoinshop plugin;

    public OrbWandListener(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        OrbZoneManager zones = this.plugin.getOrbZoneManager();
        if (!zones.isWand(event.getItem())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Never break / strip / use the block with the wand.
        event.setCancelled(true);
        if (!event.getPlayer().hasPermission("dreamcoinshop.admin")) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        int index = action == Action.LEFT_CLICK_BLOCK ? 0 : 1;
        Location loc = block.getLocation();
        zones.setPosition(event.getPlayer(), index, loc);
        event.getPlayer().sendMessage(this.plugin.getMessages().get(index == 0 ? "orbzone.pos1" : "orbzone.pos2",
                MessagesConfig.ph("pos", loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ())));
    }

    /** Creative-mode left click breaks instantly without a cancellable interact in some cases. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (this.plugin.getOrbZoneManager().isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.getOrbZoneManager().forget(event.getPlayer());
    }
}
