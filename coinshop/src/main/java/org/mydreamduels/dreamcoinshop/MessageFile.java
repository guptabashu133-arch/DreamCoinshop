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

    static YamlConfiguration load(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
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
