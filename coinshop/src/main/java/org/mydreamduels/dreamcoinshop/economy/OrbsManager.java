package org.mydreamduels.dreamcoinshop.economy;

import org.bukkit.plugin.java.JavaPlugin;

public class OrbsManager extends CurrencyManager {
    public OrbsManager(JavaPlugin plugin) {
        super(plugin, "orbs.yml");
    }
}
