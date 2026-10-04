package org.mydreamduels.dreamcoinshop.economy;

import org.bukkit.plugin.java.JavaPlugin;

public class CoinsManager extends CurrencyManager {
    public CoinsManager(JavaPlugin plugin) {
        super(plugin, "coins.yml");
    }
}
