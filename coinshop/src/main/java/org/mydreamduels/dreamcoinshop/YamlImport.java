package org.mydreamduels.dreamcoinshop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Version 1 kept balances in coins.yml / orbs.yml and unlocks in playerdata/&lt;uuid&gt;.yml. On the
 * first start with a database, all of it is copied in once, and the files are renamed to
 * *.imported (kept as a backup, never read again). A shared database only imports once: a second
 * server that still has old files is told so and leaves them alone.
 */
final class YamlImport {

    static final String META_KEY = "yaml-import";

    private YamlImport() {
    }

    /** Database thread. */
    static void run(JavaPlugin plugin, Storage storage) throws Exception {
        File dir = plugin.getDataFolder();
        File coins = new File(dir, "coins.yml");
        File orbs = new File(dir, "orbs.yml");
        File players = new File(dir, "playerdata");
        boolean anything = coins.isFile() || orbs.isFile() || players.isDirectory();
        Logger log = plugin.getLogger();
        String done = storage.getMeta(META_KEY);
        if (done != null) {
            if (anything) {
                log.warning("Found old coins.yml / orbs.yml / playerdata, but this database already has imported data ("
                        + done + "). They were NOT imported again; delete or rename them to stop this message.");
            }
            return;
        }
        if (!anything) {
            storage.setMeta(META_KEY, "nothing to import");
            return;
        }

        Map<UUID, Storage.Data> all = new HashMap<>();
        if (coins.isFile()) readBalances(coins, all, Currency.COINS);
        if (orbs.isFile()) readBalances(orbs, all, Currency.ORBS);
        File[] files = players.isDirectory() ? players.listFiles((d, n) -> n.endsWith(".yml")) : null;
        if (files != null) {
            for (File f : files) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                Storage.Data d = all.computeIfAbsent(uuid, u -> Storage.Data.empty(u, null));
                add(d, Category.CHATCOLOR, y.getStringList("unlocked.chatcolors"), y.getString("active.chatcolor"));
                add(d, Category.GLOW, y.getStringList("unlocked.glows"), y.getString("active.glow"));
                add(d, Category.PERK, y.getStringList("unlocked.perks"), null);
                add(d, Category.TAG, y.getStringList("unlocked.tags"), y.getString("active.tag"));
                add(d, Category.GRADIENT, y.getStringList("unlocked.gradients"), y.getString("active.gradient"));
                add(d, Category.FLAG, y.getStringList("unlocked.flags"), y.getString("active.flag"));
            }
        }

        for (Storage.Data d : all.values()) storage.importData(d);
        storage.setMeta(META_KEY, all.size() + " players, " + new java.util.Date());

        rename(coins);
        rename(orbs);
        rename(players);
        log.info("Imported " + all.size() + " players from the old yml files into the " + storage.describe()
                + " database. The old files were renamed to *.imported (a backup, not used any more).");
    }

    private static void readBalances(File file, Map<UUID, Storage.Data> all, Currency currency) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            double value = Math.max(0, y.getDouble(key));
            Storage.Data d = all.getOrDefault(uuid, Storage.Data.empty(uuid, null));
            d = currency == Currency.COINS
                    ? new Storage.Data(uuid, d.name(), value, d.orbs(), d.unlocked(), d.active())
                    : new Storage.Data(uuid, d.name(), d.coins(), value, d.unlocked(), d.active());
            all.put(uuid, d);
        }
    }

    private static void add(Storage.Data d, Category c, List<String> owned, String active) {
        d.unlocked().get(c).addAll(owned);
        if (active != null && !active.isBlank()) d.active().put(c, active);
    }

    private static void rename(File f) {
        if (!f.exists()) return;
        File target = new File(f.getParentFile(), f.getName() + ".imported");
        for (int i = 2; target.exists(); i++) target = new File(f.getParentFile(), f.getName() + ".imported" + i);
        if (!f.renameTo(target)) {
            throw new IllegalStateException("Imported, but could not rename " + f.getName() + " to " + target.getName());
        }
    }
}
