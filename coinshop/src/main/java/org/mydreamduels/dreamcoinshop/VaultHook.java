package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Pays into the server's own economy (EssentialsX, CMI ... through Vault) for
 * sellwand-settings.payout: VAULT. Vault is optional: used by name, so without it nothing breaks.
 */
final class VaultHook {

    private VaultHook() {
    }

    /** Deposits; false if Vault or an economy plugin isn't there. Main / global thread. */
    static boolean deposit(OfflinePlayer player, double amount) {
        try {
            Class<?> economy = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(economy);
            if (rsp == null) return false;
            Object eco = rsp.getProvider();
            Method m = economy.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            m.invoke(eco, player, amount);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    static boolean available() {
        try {
            Class<?> economy = Class.forName("net.milkbowl.vault.economy.Economy");
            return Bukkit.getServicesManager().getRegistration(economy) != null;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
