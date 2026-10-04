package org.mydreamduels.dreamcoinshop.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;

public class DreamcoinshopExpansion extends PlaceholderExpansion {
    private final Dreamcoinshop plugin;

    public DreamcoinshopExpansion(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    @NotNull
    public String getIdentifier() {
        return "dreamcoinshop";
    }

    @NotNull
    public String getAuthor() {
        return "Dreamcoinshop";
    }

    @NotNull
    public String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        switch (params.toLowerCase()) {
            case "coins":
                return String.valueOf((long) this.plugin.getCoinsManager().getBalance(player.getUniqueId()));
            case "coins_exact":
                return String.valueOf(this.plugin.getCoinsManager().getBalance(player.getUniqueId()));
            case "orbs":
                return String.valueOf((long) this.plugin.getOrbsManager().getBalance(player.getUniqueId()));
            case "orbs_exact":
                return String.valueOf(this.plugin.getOrbsManager().getBalance(player.getUniqueId()));
            case "gradient_name":
                // NEW: exposes the player's real username wrapped in their
                // purchased gradient (or just the plain real username if they
                // don't have one active) as a MiniMessage tag string, built
                // straight from player.getName() - never from displayName(),
                // so it can't pick up a /nick nickname or an ops-name-color
                // red tint from EssentialsX or anything else. Dreamtab's tab
                // list uses this instead of %player_name% to keep the
                // gradient visible there too.
                if (player instanceof Player online) {
                    return this.plugin.getGradientNameTag(online);
                }
                return player.getName();
            default:
                return null;
        }
    }
}
