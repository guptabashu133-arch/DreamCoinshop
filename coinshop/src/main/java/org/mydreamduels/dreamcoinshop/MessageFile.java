package org.mydreamduels.dreamcoinshop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Loads a yml file from the data folder (writing the bundled one if it's missing) and adds any
 * keys a newer version brought, without touching what the owner already changed.
 */
final class MessageFile {

    private MessageFile() {
    }

    static final int MESSAGES_VERSION = 2;

    /**
     * messages.yml version 1 had "coins" / "orbs" written out; version 2 uses the currency names
     * from config.yml (&lt;coins_lower&gt; ...), so renaming Orbs to Shards changes every text.
     */
    private static void upgradeMessages(JavaPlugin plugin, File file, YamlConfiguration yaml) {
        try {
            java.nio.file.Files.copy(file.toPath(), new File(plugin.getDataFolder(), "messages-v1-backup.yml").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            for (String key : yaml.getKeys(true)) {
                if (yaml.isString(key)) yaml.set(key, currencyWords(yaml.getString(key)));
            }
            yaml.set("messages-version", MESSAGES_VERSION);
            yaml.save(file);
            plugin.getLogger().info("messages.yml now uses the currency names from config.yml (backup: messages-v1-backup.yml).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not update messages.yml", e);
        }
    }

    /** "500 coins" -> "500 &lt;coins_lower&gt;", "Orbs Top" -> "&lt;orbs_name&gt; Top", "/orb give" -> "/&lt;cmd&gt; give". */
    static String currencyWords(String s) {
        String out = s.replaceAll("/(coin|orb)\\b(?!shop)", "/<cmd>")
                .replaceAll("\\bOrbshop\\b", "<orbshop_name>")
                .replaceAll("\\bOrbs\\b(?!\\.yml)", "<orbs_name>")
                .replaceAll("\\borbs\\b(?!\\.yml)", "<orbs_lower>")
                .replaceAll("\\bOrb\\b", "<orb_name>")
                .replaceAll("\\bCoins\\b(?!\\.yml)", "<coins_name>")
                .replaceAll("\\bcoins\\b(?!\\.yml)", "<coins_lower>")
                .replaceAll("\\bCoin\\b", "<coin_name>");
        // the amount placeholders <coins> / <orbs> themselves stay
        return out.replace("<<coins_lower>>", "<coins>").replace("<<orbs_lower>>", "<orbs>");
    }

    static YamlConfiguration load(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
        YamlConfiguration yaml;
        try {
            yaml = ShopConfig.parse(file);
        } catch (ShopConfig.BrokenConfig e) {
            // Never "repair" a file with a typo by saving defaults over it: use the defaults in memory only
            plugin.getLogger().severe(e.getMessage() + " - using the built-in texts until it's fixed (your file was not changed).");
            yaml = new YamlConfiguration();
            try (InputStream in = plugin.getResource(name)) {
                if (in != null) yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
            return yaml;
        }
        if (name.equals("messages.yml") && yaml.getInt("messages-version", 1) < MESSAGES_VERSION) upgradeMessages(plugin, file, yaml);
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                boolean missing = false;
                for (String key : defaults.getKeys(true)) {
                    if (!defaults.isConfigurationSection(key) && !yaml.contains(key, true)) {
                        yaml.set(key, defaults.get(key));
                        missing = true;
                    }
                }
                if (missing) yaml.save(file);
                yaml.setDefaults(defaults);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Could not add the new default keys to " + name, ex);
        }
        return yaml;
    }
}
